(ns martian.interceptors
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys stringify-keys]]
            [martian.encoders :as encoders]
            [martian.encoding :as encoding]
            [martian.schema :as schema]
            [martian.schema-tools :as schema-tools]
            [martian.utils :as utils]
            #?(:clj  [schema.core :as s]
               :cljs [schema.core :as s :refer [EqSchema]])
            [tripod.context :as tc])
  #?(:clj (:import [schema.core EqSchema])))

#?(:bb
   ;; reflection issue in babashka -- TODO, submit patch upstream?
   (do (defn- exception->ex-info [^Throwable exception execution-id interceptor stage]
         (ex-info (str "Interceptor Exception: " #?(:clj  (.getMessage exception)
                                                    :cljs (.-message exception)))
                  (merge {:execution-id execution-id
                          :stage        stage
                          :interceptor  (:name interceptor)
                          :type         (type exception)
                          :exception    exception}
                         (ex-data exception))
                  exception))
       (alter-var-root #'tc/exception->ex-info (constantly exception->ex-info))))

(defn remove-stack [ctx]
  (-> ctx tc/terminate (dissoc ::tc/stack)))

(def request-only-handler
  {:name ::request-only-handler
   :leave remove-stack})

(defn- create-only [m k v]
  (if (get m k)
    m
    (assoc m k v)))

(defn- insert-or-merge [m k v]
  (cond
    (get m k) (update m k #(merge v %))
    (not-empty v) (assoc m k v)
    :else m))

(def set-method
  {:name ::method
   :enter (fn [{:keys [handler] :as ctx}]
            (update ctx :request create-only :method (:method handler)))})

(def set-url
  {:name ::url
   :enter (fn [{:keys [params url-for handler] :as ctx}]
            (update ctx :request create-only :url (url-for (:route-name handler) params)))})

(defn coerce-data [{:keys [parameter-aliases] :as handler} schema-key params opts]
  (let [coerce-opts (-> opts
                        (select-keys [:coercion-matcher :use-defaults?])
                        (assoc :parameter-aliases (get parameter-aliases schema-key)))]
    (schema/coerce-data (get handler schema-key) params coerce-opts)))

(def keywordize-params
  {:name ::keywordize-params
   :enter (fn [ctx] (update ctx :params keywordize-keys))})

(defn- set-params [ctx req-key coerced-data]
  (update ctx :request insert-or-merge req-key coerced-data))

(def set-query-params
  {:name ::query-params
   :enter (fn [{:keys [params handler opts] :as ctx}]
            (set-params ctx :query-params (coerce-data handler :query-schema params opts)))})

(defn- map-schema
  "Returns the map schema that `schema` is, or that it wraps, e.g. in
   `s/maybe`, `st/default`, `s/named` or `s/constrained`, if any."
  [schema]
  (cond
    (and (map? schema) (not (record? schema))) schema
    (and (record? schema) (contains? schema :schema)) (recur (:schema schema))))

(defn- declared-params
  "Returns the keys that map `schema` declares, in both forms a param can be
   given in: its own key and its idiomatic alias. Returns nil when Martian
   can't tell them, as `schema` isn't a map or has only generic keys, such as
   `s/Any`."
  [schema]
  (when-some [schema (map-schema schema)]
    (let [ks (filter #(or (keyword? %) (string? %) (s/specific-key? %))
                     (keys schema))]
      (when (or (seq ks) (empty? schema))
        (into #{}
              (comp (mapcat (fn [k]
                              [(schema-tools/explicit-key k)
                               (schema-tools/->idiomatic k)]))
                    (remove nil?))
              ks)))))

(defn- martian-params
  "Returns Martian's own params, such as `:martian.core/request`."
  [params]
  (filter #(and (keyword? %) (= "martian.core" (namespace %))) (keys params)))

(defn- flat-body-params
  "Returns the `params` given flat for the body: all of them but Martian's own
   and, when Martian can tell which keys the body declares, the ones that only
   other destinations declare."
  [handler params body-schema]
  (let [own (declared-params body-schema)
        others (when own
                 (into #{}
                       (mapcat #(declared-params (get handler %)))
                       [:path-schema :query-schema :form-schema :headers-schema]))]
    (apply dissoc params (concat (remove own others) (martian-params params)))))

(def set-body-params
  {:name ::body-params
   :enter (fn [{:keys [params handler opts] :as ctx}]
            (if-let [[body-key body-schema] (first (:body-schema handler))]
              (let [body-key (s/explicit-schema-key body-key)
                    body-params (or (:martian.core/body params)
                                    (get params body-key)
                                    (get params (->kebab-case-keyword body-key))
                                    (flat-body-params handler params body-schema))]
                (set-params ctx :body (get (coerce-data handler :body-schema {body-key body-params} opts) body-key)))
              ctx))})

(def set-form-params
  {:name ::form-params
   :enter (fn [{:keys [params handler opts] :as ctx}]
            (set-params ctx :form-params (coerce-data handler :form-schema params opts)))})

(def set-header-params
  {:name ::header-params
   :enter (fn [{:keys [params handler opts] :as ctx}]
            (let [default-headers (some-> ctx :request :headers keywordize-keys)
                  coerced-headers (coerce-data handler :headers-schema (merge default-headers params) opts)]
              (-> ctx
                  (set-params :headers coerced-headers)
                  (utils/update-in* [:request :headers] stringify-keys))))})

(def enqueue-route-specific-interceptors
  {:name ::enqueue-route-specific-interceptors
   :enter (fn [{:keys [handler] :as ctx}]
            (if-let [i (:interceptors handler)]
              (update ctx ::tc/queue #(into (into tc/queue i) %))
              ctx))})

(defn- content-type? [header-key]
  (= "content-type" (str/lower-case (name header-key))))

(def memo:content-type? (memoize content-type?))

(defn get-content-type [headers]
  (some #(when (memo:content-type? %) (headers %)) (keys headers)))

(defn drop-content-type [headers]
  (if-let [header-key (some #(when (memo:content-type? %) %) (keys headers))]
    (dissoc headers header-key)
    headers))

(defn encode-request [encoders]
  {:name ::encode-request
   :encodes (keys encoders)
   :enter (fn [{:keys [request handler] :as ctx}]
            (let [has-body? (:body request)
                  content-type (when (and has-body?
                                          (not (get-in request [:headers "Content-Type"])))
                                 (encoding/choose-media-type encoders (:consumes handler)))
                  ;; NB: There are many possible subtypes of multipart requests.
                  multipart? (when content-type (str/starts-with? content-type "multipart/"))
                  {:keys [encode]} (encoding/find-encoder encoders content-type)
                  encoded-request (cond-> request

                                    has-body?
                                    (update :body encode)

                                    multipart?
                                    ;; NB: Luckily, all target JVM/BB HTTP clients that support multipart requests,
                                    ;;     i.e. `hato`, `clj-http`, `http-kit`, `bb/http-client`, all use the same
                                    ;;     syntax — the `:multipart` key mapped to a value that's a vector of maps.
                                    ;; TODO: Add multipart support for JS HTTP clients that use `:multipart-params`.
                                    (-> (set/rename-keys {:body :multipart})
                                        (update :headers drop-content-type))

                                    (and content-type (not multipart?))
                                    (assoc-in [:headers "Content-Type"] content-type))]
              (assoc ctx :request encoded-request)))})

(def default-encode-request (encode-request (encoders/default-encoders)))

(defn coerce-response
  ([encoders]
   (coerce-response encoders nil))
  ([encoders coerce-opts]
   (let [{:keys [request-key] :as coerce-opts} (encoding/set-default-coerce-opts coerce-opts)]
     {:name ::coerce-response
      :decodes (keys encoders)
      :enter (fn [{:keys [request handler] :as ctx}]
               (let [response-media-type (when (not (get-in request [:headers "Accept"]))
                                           (encoding/choose-media-type encoders (:produces handler)))
                     {response-as :value
                      :as coerce-as} (encoding/get-coerce-as encoders response-media-type coerce-opts)]
                 (cond-> (assoc ctx :coerce-as coerce-as)
                   response-as (update :request assoc request-key response-as)
                   response-media-type (assoc-in [:request :headers "Accept"] response-media-type))))
      :leave (fn [{:keys [response coerce-as] :as ctx}]
               ;; TODO: In some cases (`http-kit`) it may be necessary to decode an `:error :body`.
               (let [content-type (when (:body response)
                                    (get-content-type (:headers response)))]
                 (if-not (or (encoding/skip-decoding? coerce-as content-type coerce-opts)
                             (encoding/auto-coercion-by-client? coerce-as coerce-opts))
                   (let [{:keys [decode]} (encoding/find-encoder encoders content-type)
                         decoded-response (update response :body decode)]
                     (assoc ctx :response decoded-response))
                   ctx)))})))

(def default-coerce-response (coerce-response (encoders/default-encoders)))

(defn- empty-body? [body]
  (or (nil? body) (and (string? body) (str/blank? body))))

(defn- default-status?
  "Whether the `status` schema is that of a `default` response: any value for
   OpenAPI, or the symbol `default` for Swagger."
  [status]
  (or (= s/Any status)
      (and (instance? EqSchema status) (= 'default (:v status)))))

(defn- response-schema-for
  "Returns the one of `response-schemas` that `status` belongs to: the one with
   that exact code, else one whose range covers it, else `default`, as OpenAPI
   ranks them."
  [response-schemas status]
  (->> response-schemas
       (filter #(or (default-status? (:status %))
                    (nil? (s/check (:status %) status))))
       (sort-by #(cond
                   (default-status? (:status %))    2
                   (instance? EqSchema (:status %)) 0
                   :else                            1))
       (first)))

(defn validate-response-body
  "Validate responses against the appropriate response schema, the one of
   the response's status, which expects no body if it describes none.

   Optional 'strict mode' throws an error if it is invalid: when no response
   schema matches the status, or when the response has a body but the schema
   of its status describes none.

   A response without a status isn't validated, as it's no HTTP response, e.g.
   the `{:error ...}` that an HTTP client gives for a failed connection."
  ([] (validate-response-body {:strict? false}))
  ([{:keys [strict?]}]
   {:name ::validate-response
    :leave (fn [{:keys [handler response] :as ctx}]
             (when (int? (:status response))
               (let [response-schema (response-schema-for (:response-schemas handler)
                                                          (:status response))]
                 (if-let [body-schema (:body response-schema)]
                   (s/validate body-schema (:body response))
                   (when (and strict? (or (nil? response-schema)
                                          (not (empty-body? (:body response)))))
                     (throw (ex-info (str "No response body schema found for status "
                                          (:status response))
                                     {:response response
                                      :response-schemas (:response-schemas handler)}))))))
             ctx)}))

(def prepare-content-types
  (comp vec distinct #(into % ["text/plain" "application/octet-stream"])))

(defn supported-content-types
  "Returns the full set of supported content-types used for parsing OpenAPI spec
   as declared by any encoding/decoding interceptors, preserving their original
   declaration order and appending the default ones."
  [interceptors]
  (-> (reduce (fn [acc interceptor]
                (merge-with into acc (select-keys interceptor [:encodes :decodes])))
              {:encodes []
               :decodes []}
              interceptors)
      (update :encodes prepare-content-types)
      (update :decodes prepare-content-types)))

;; borrowed from https://github.com/walmartlabs/lacinia-pedestal/blob/master/src/com/walmartlabs/lacinia/pedestal.clj#L40
(defn inject
  "Locates the named interceptor in the list of interceptors and adds (or replaces)
  the new interceptor to the list.
  relative-position may be :before, :after, or :replace.
  For :replace, the new interceptor may be nil, in which case the interceptor is removed.
  The named interceptor must exist, or an exception is thrown."
  [interceptors new-interceptor relative-position interceptor-name]
  (let [*found? (volatile! false)
        final-result (reduce (fn [result interceptor]
                               ;; An interceptor can also be a bare handler function, which is 'nameless'
                               (if-not (= interceptor-name (when (map? interceptor)
                                                             (:name interceptor)))
                                 (conj result interceptor)
                                 (do
                                   (vreset! *found? true)
                                   (case relative-position
                                     :before
                                     (conj result new-interceptor interceptor)

                                     :after
                                     (conj result interceptor new-interceptor)

                                     :replace
                                     (if new-interceptor
                                       (conj result new-interceptor)
                                       result)))))
                             []
                             interceptors)]
    (when-not @*found?
      (throw (ex-info "Could not find existing interceptor."
                      {:interceptors interceptors
                       :new-interceptor new-interceptor
                       :relative-position relative-position
                       :interceptor-name interceptor-name})))
    final-result))
