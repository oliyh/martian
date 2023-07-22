(ns martian.core
  (:require [tripod.context :as tc]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [martian.interceptors :as interceptors]
            [martian.parameter-aliases :as parameter-aliases :refer [parameter-aliases alias-schema]]
            [martian.swagger :refer [swagger->handlers]]
            [martian.openapi :refer [openapi->handlers openapi-schema?]]
            [clojure.spec.alpha :as spec]
            [martian.spec :as mspec]
            [lambdaisland.uri :refer [map->query-string]]))

#?(:bb
   ;; reflection issue in babasha -- TODO, submit patch upstream?
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

(def ^:private parameter-schemas [:path-schema :query-schema :body-schema :form-schema :headers-schema])

(defn- enrich-handler [handler]
  (-> handler
      (assoc :parameter-aliases
             (reduce (fn [aliases parameter-key]
                       (assoc aliases parameter-key (parameter-aliases (get handler parameter-key))))
                     {}
                     parameter-schemas))))

(defn- concise->handlers [concise-handlers global-produces global-consumes]
  (map (comp
        enrich-handler
        (fn [handler]
          (-> handler
              (update :produces #(or % global-produces))
              (update :consumes #(or % global-consumes)))))
       concise-handlers))

(defn- resolve-instance [m]
  (cond
    (map? m) m
    (var? m) (deref m)
    (fn? m) (m)
    :else m))

(defn find-handler [handlers route-name]
  (or (first (filter #(= (keyword route-name) (:route-name %)) handlers))
      (throw (ex-info (str "Could not find route " route-name)
                      {:handlers   handlers
                       :route-name route-name}))))

(defn handler-for [m route-name]
  (find-handler (:handlers (resolve-instance m)) route-name))

(defn update-handler
  "Update a handler in the martian record with the provided route-name
   e.g. add route-specific interceptors:
   (update-handler m :load-pet assoc :interceptors [an-interceptor])"
  [m route-name update-fn & update-args]
  (update (resolve-instance m)
          :handlers #(mapv (fn [handler]
                             (if (= (keyword route-name) (:route-name handler))
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

(defn explore
  ([martian] (mapv (juxt :route-name :summary) (:handlers (resolve-instance martian))))
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

(defn- build-instance [api-root handlers {:keys [interceptors] :as opts}]
  (->Martian api-root handlers (or interceptors default-interceptors) (dissoc opts :interceptors)))

(spec/fdef build-instance
  :args (spec/cat :api-root ::mspec/api-root
                  :handlers (spec/coll-of ::mspec/handler)
                  :opts ::mspec/opts))

(defn bootstrap-openapi
  "Creates a martian instance from an openapi/swagger spec based on the schema provided"
  [api-root json & [opts]]
  (let [{:keys [interceptors] :or {interceptors default-interceptors} :as opts} (keywordize-keys opts)]
    (build-instance api-root
                    (map enrich-handler (if (openapi-schema? json)
                                          (openapi->handlers json (interceptors/supported-content-types interceptors))
                                          (swagger->handlers json)))
                    opts)))

(def bootstrap-swagger bootstrap-openapi)

(defn bootstrap
  "Creates a martian instance from a martian description"
  [api-root concise-handlers & [{:keys [produces consumes] :as opts}]]
  (build-instance api-root (concise->handlers concise-handlers produces consumes) opts))
