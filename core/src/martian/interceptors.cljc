(ns martian.interceptors
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys stringify-keys]]
            [martian.encoders :as encoders]
            [martian.encoding :as encoding]
            [martian.schema :as schema]
            [martian.utils :as utils]
            [schema.core :as s]
            [tripod.context :as tc]))

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

(def set-body-params
  {:name ::body-params
   :enter (fn [{:keys [params handler opts] :as ctx}]
            (if-let [[body-key] (first (:body-schema handler))]
              (let [body-key (s/explicit-schema-key body-key)
                    body-params (or (:martian.core/body params)
                                    (get params body-key)
                                    (get params (->kebab-case-keyword body-key))
                                    params)]
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

(defn validate-response-body
  "Validate responses against the appropriate response schema.
   Optional strict mode throws an error if it is invalid"
  ([] (validate-response-body {:strict? false}))
  ([{:keys [strict?]}]
   {:name ::validate-response
    :leave (fn [{:keys [handler response] :as ctx}]
             (if-let [body-schema (some (fn [schema]
                                          (when-not (s/check (:status schema) (:status response))
                                            (:body schema)))
                                        (:response-schemas handler))]
               (s/validate body-schema (:body response))
               (when strict?
                 (throw (ex-info (str "No response body schema found for status " (:status response))
                                 {:response response
                                  :response-schemas (:response-schemas handler)}))))
             ctx)}))

(defn supported-content-types
  "Return the full set of supported content-types as declared by any encoding/decoding interceptors,
   preserving their original declaration order."
  [interceptors]
  (-> (reduce (fn [acc interceptor]
                (merge-with into acc (select-keys interceptor [:encodes :decodes])))
              {:encodes []
               :decodes []}
              interceptors)
      (update :encodes distinct)
      (update :decodes distinct)))

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
