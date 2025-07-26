(ns martian.core
  (:require [clojure.core.protocols]
            [clojure.spec.alpha :as spec]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [lambdaisland.uri :refer [map->query-string]]
            [martian.interceptors :as interceptors]
            [martian.openapi :refer [openapi->handlers openapi-schema?]]
            [martian.parameter-aliases :refer [parameter-aliases alias-schema]]
            [martian.schema :as schema]
            [martian.spec :as mspec]
            [martian.swagger :refer [swagger->handlers]]
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

(def default-interceptors
  [interceptors/keywordize-params
   interceptors/set-method
   interceptors/set-url
   interceptors/set-query-params
   interceptors/set-body-params
   interceptors/set-form-params
   interceptors/set-header-params
   interceptors/enqueue-route-specific-interceptors])

(def default-coercion-matcher schema/default-coercion-matcher)

(def ^:private parameter-schemas [:path-schema :query-schema :body-schema :form-schema :headers-schema])

(defn- resolve-instance [m]
  (cond
    (map? m) m
    (var? m) (deref m)
    (fn? m) (m)
    :else m))

(defn- validate-handler! [{:keys [exception route-name] :as handler}]
  (when (some? exception)
    (throw (ex-info (str "Handler " route-name " exception")
                    {:handler handler}
                    exception)))
  handler)

(defn- matching-handler? [route-name handler]
  (= (keyword route-name) (:route-name handler)))

(defn find-handler [handlers route-name]
  (or (some-> (some #(when (matching-handler? route-name %) %) handlers)
              (validate-handler!))
      (throw (ex-info (str "Could not find route " route-name)
                      {:route-name route-name
                       :handlers handlers}))))

(defn handler-for [m route-name]
  (find-handler (:handlers (resolve-instance m)) route-name))

(defn update-handler
  "Update a handler in the martian record with the provided route-name
   e.g. add route-specific interceptors:
   (update-handler m :load-pet assoc :interceptors [an-interceptor])"
  [m route-name update-fn & update-args]
  (update (resolve-instance m)
          :handlers #(mapv (fn [handler]
                             (if (matching-handler? route-name handler)
                               (apply update-fn handler update-args)
                               handler))
                           %)))

(defrecord Martian [api-root handlers interceptors opts])

(defn url-for
  ([martian route-name] (url-for martian route-name {}))
  ([martian route-name params] (url-for martian route-name params {}))
  ([martian route-name params opts]
   (let [{:keys [api-root handlers]} (resolve-instance martian)]
     (when-let [handler (find-handler handlers route-name)]
       (let [path-params (interceptors/coerce-data handler :path-schema (keywordize-keys params) (:opts martian))
             query-params (when (:include-query? opts)
                            (interceptors/coerce-data handler :query-schema (keywordize-keys params) (:opts martian)))]
         (str api-root (string/join (map #(get path-params % %) (:path-parts handler)))
              (if query-params
                (str "?" (map->query-string query-params))
                "")))))))

(defn request-for
  ([martian route-name] (request-for martian route-name {}))
  ([martian route-name params]
   (let [{:keys [handlers interceptors] :as martian} (resolve-instance martian)]
     (when-let [handler (find-handler handlers route-name)]
       (let [ctx (tc/enqueue* {} (-> (or interceptors default-interceptors) vec (conj interceptors/request-only-handler)))]
         (:request (tc/execute
                    (assoc ctx
                           :url-for (partial url-for martian)
                           :request (or (::request params) {})
                           :handler handler
                           :params params
                           :opts (:opts martian)))))))))

(defn response-for
  ([martian route-name] (response-for martian route-name {}))
  ([martian route-name params]
   (let [{:keys [handlers interceptors] :as martian} (resolve-instance martian)]
     (when-let [handler (find-handler handlers route-name)]
       (let [ctx (tc/enqueue* {} (or interceptors default-interceptors))]
         (:response (tc/execute
                     (assoc ctx
                            :url-for (partial url-for martian)
                            :request (or (::request params) {})
                            :handler handler
                            :params params
                            :opts (:opts martian)))))))))

(declare explore)
(defn- navize-routes
  [martian routes]
  (with-meta
    routes
    {`clojure.core.protocols/nav
     (fn [_coll _k [route-name _route-description]]
       (explore martian route-name))}))

(defn explore
  ([martian] (navize-routes martian (mapv (juxt :route-name :summary) (:handlers (resolve-instance martian)))))
  ([martian route-name]
   (when-let [{:keys [parameter-aliases summary deprecated?] :as handler} (find-handler (:handlers (resolve-instance martian)) route-name)]
     (-> {:summary summary
          :parameters (reduce (fn [params parameter-key]
                                (merge params (alias-schema (get parameter-aliases parameter-key) (get handler parameter-key))))
                              {}
                              parameter-schemas)
          :returns (->> (:response-schemas handler)
                        (map (juxt (comp :v :status) :body))
                        (into {}))}
         (cond-> deprecated? (assoc :deprecated? true))))))

(defn- enrich-handlers [handlers]
  (mapv (fn [handler]
          (try
            (assoc handler
              :parameter-aliases
              (reduce (fn [aliases parameter-key]
                        (assoc aliases parameter-key (parameter-aliases (get handler parameter-key))))
                      {}
                      parameter-schemas))
            (catch #?(:clj Exception :cljs js/Error) ex
              (assoc handler :exception ex))))
        handlers))

(defn- build-instance [api-root handlers {:keys [interceptors validate-handlers?] :as opts}]
  (let [enriched-handlers (enrich-handlers handlers)]
    (when validate-handlers?
      (doseq [handler enrich-handlers]
        (validate-handler! handler)))
    (->Martian api-root
               enriched-handlers
               (or interceptors default-interceptors)
               (dissoc opts :interceptors))))

(spec/fdef build-instance
  :args (spec/cat :api-root ::mspec/api-root
                  :handlers (spec/coll-of ::mspec/handler)
                  :opts ::mspec/opts))

(defn bootstrap-openapi
  "Creates a martian instance from an OpenAPI/Swagger spec based on the schema provided"
  [api-root json & [opts]]
  (let [{:keys [interceptors] :or {interceptors default-interceptors} :as opts} (keywordize-keys opts)
        handlers (if (openapi-schema? json)
                   (openapi->handlers json (interceptors/supported-content-types interceptors))
                   (swagger->handlers json))]
    (build-instance api-root handlers opts)))

(def bootstrap-swagger bootstrap-openapi)

(defn bootstrap
  "Creates a martian instance from a martian description"
  [api-root concise-handlers & [{:keys [produces consumes] :as opts}]]
  (let [handlers (map (fn [handler]
                        (-> handler
                            (update :produces #(or % produces))
                            (update :consumes #(or % consumes))))
                      concise-handlers)]
    (build-instance api-root handlers opts)))
