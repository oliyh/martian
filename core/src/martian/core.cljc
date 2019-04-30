(ns martian.core
  (:require [tripod.context :as tc]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.string :as string]
            [clojure.walk :refer [postwalk postwalk-replace]]
            [martian.interceptors :as interceptors]
            [martian.schema :as schema]
            [martian.swagger :as swagger]
            [schema.core :as s]))

(def default-interceptors
  [interceptors/set-method
   interceptors/set-url
   interceptors/set-query-params
   interceptors/set-body-params
   interceptors/set-form-params
   interceptors/set-header-params
   interceptors/enqueue-route-specific-interceptors])

(def ^:private parameter-schemas [:path-schema :query-schema :body-schema :form-schema :headers-schema])

(defn keywordize-keys
  "Recursively transforms all map keys from strings to keywords."
  [m]
  (let [f (fn [[k v]] (if (string? k) [(keyword k) v] [k v]))]
    (postwalk (fn [x]
                (cond (record? x)
                      (let [ks (filter string? (keys x))]
                        (into (apply dissoc x ks) (map f x)))
                      (map? x) (into {} (map f x))
                      :else x))
              m)))

(defn- enrich-handler [handler]
  (-> handler
      (assoc :parameter-aliases (let [ks (schema/parameter-keys (map handler parameter-schemas))]
                                  (zipmap (map ->kebab-case-keyword ks) ks)))))

(defn- concise->handlers [concise-handlers global-produces global-consumes]
  (map (comp
        enrich-handler
        (fn [handler]
          (-> handler
              (update :produces #(or % global-produces))
              (update :consumes #(or % global-consumes)))))
       concise-handlers))

(defn find-handler [handlers route-name]
  (first (filter #(= (keyword route-name) (:route-name %)) handlers)))

(defn handler-for [m route-name]
  (find-handler (:handlers m) route-name))

(defn update-handler
  "Update a handler in the martian record with the provided route-name
   e.g. add route-specific interceptors:
   (update-handler m :load-pet assoc :interceptors [an-interceptor])"
  [{:keys [handlers] :as m} route-name update-fn & update-args]
  (update m :handlers #(mapv (fn [handler]
                               (if (= (keyword route-name) (:route-name handler))
                                 (apply update-fn handler update-args)
                                 handler))
                             %)))

(defrecord Martian [api-root handlers interceptors])

(defn url-for
  ([martian route-name] (url-for martian route-name {}))
  ([{:keys [api-root handlers]} route-name params]
   (when-let [handler (find-handler handlers route-name)]
     (let [params (->> params keywordize-keys (interceptors/coerce-data handler :path-schema))]
       (str api-root (string/join (map #(get params % %) (:path-parts handler))))))))

(defn request-for
  ([martian route-name] (request-for martian route-name {}))
  ([{:keys [handlers interceptors] :as martian} route-name params]
   (when-let [handler (find-handler handlers route-name)]
     (let [params (keywordize-keys params)
           ctx (tc/enqueue* {} (-> (or interceptors default-interceptors) vec (conj interceptors/request-only-handler)))]
       (:request (tc/execute
                  (assoc ctx
                         :url-for (partial url-for martian)
                         :request (or (::request params) {})
                         :handler handler
                         :params params)))))))

(defn response-for
  ([martian route-name] (response-for martian route-name {}))
  ([{:keys [handlers interceptors] :as martian} route-name params]
   (when-let [handler (find-handler handlers route-name)]
     (let [params (keywordize-keys params)
           ctx (tc/enqueue* {} (or interceptors default-interceptors))]
       (:response (tc/execute
                   (assoc ctx
                          :url-for (partial url-for martian)
                          :request (or (::request params) {})
                          :handler handler
                          :params params)))))))

(defn explore
  ([{:keys [handlers]}] (mapv (juxt :route-name :summary) handlers))
  ([{:keys [handlers]} route-name]
   (when-let [handler (find-handler handlers route-name)]
     {:summary (:summary handler)
      :parameters (let [parameter-aliases (:parameter-aliases handler)]
                    (->> (map handler parameter-schemas)
                         (apply merge)
                         (postwalk-replace (zipmap (vals parameter-aliases) (keys parameter-aliases)))))
      :returns (->> (:response-schemas handler)
                    (map (juxt (comp :v :status) :body))
                    (into {}))})))

(defn- build-instance [api-root handlers {:keys [interceptors]}]
  (->Martian api-root handlers (or interceptors default-interceptors)))

(defn bootstrap-swagger
  "Creates a martian instance from a swagger spec

   (let [m (bootstrap-swagger \"https://api.org\" swagger-spec)]
     (url-for m :load-pet {:id 123}))

   ;; => https://api.org/pets/123"
  [api-root swagger-json & [opts]]
  (build-instance api-root (map enrich-handler (swagger/swagger->handlers swagger-json)) (keywordize-keys opts)))

(defn bootstrap
  "Creates a martian instance from a martian description"
  [api-root concise-handlers & [{:keys [produces consumes] :as opts}]]
  (build-instance api-root (concise->handlers concise-handlers produces consumes) opts))
