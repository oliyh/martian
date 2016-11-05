(ns martian.core
  (:require [tripod.context :as tc]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
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
   interceptors/set-header-params])

(defn- tokenise-path [url-pattern]
  (let [parts (map first (re-seq #"([^:]+|:[^/]+)" url-pattern))]
    (map #(if-let [param-name (second (re-matches #"^:(.+)/?" %))]
            (keyword param-name)
            %)
         parts)))

(defn- concise->handlers [concise-handlers global-produces global-consumes]
  (map (fn [{:keys [path] :as handler}]
         (-> handler
             (assoc :path-parts (tokenise-path path))
             (update :produces #(or % global-produces))
             (update :consumes #(or % global-consumes))))
       concise-handlers))

(defn find-handler [handlers route-name]
  (first (filter #(= (keyword route-name) (:route-name %)) handlers)))

(defrecord Martian [api-root handlers interceptors])

(defn url-for
  ([martian route-name] (url-for martian route-name {}))
  ([{:keys [api-root handlers]} route-name params]
   (when-let [handler (find-handler handlers route-name)]
     (let [params (->> params keywordize-keys (schema/coerce-data (:path-schema handler)))]
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
                         :request {}
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
                          :request {}
                          :handler handler
                          :params params)))))))

(defn explore
  ([{:keys [handlers]}] (mapv (juxt :route-name (comp :summary :swagger-definition)) handlers))
  ([{:keys [handlers]} route-name]
   (when-let [handler (find-handler handlers route-name)]
     {:summary (get-in handler [:swagger-definition :summary])
      :parameters (apply merge (map handler [:path-schema :query-schema :body-schema :form-schema :headers-schema]))})))

(defn- build-instance [api-root handlers {:keys [interceptors]}]
  (->Martian api-root handlers (or interceptors default-interceptors)))

(defn bootstrap-swagger
  "Creates a martian instance from a swagger spec

   (let [m (bootstrap-swagger \"https://api.org\" swagger-spec)]
     (url-for m :load-pet {:id 123}))

   ;; => https://api.org/pets/123"
  [api-root swagger-json & [opts]]
  (build-instance api-root (swagger/swagger->handlers swagger-json) (keywordize-keys opts)))

(defn bootstrap
  "Creates a martian instance from a martian description"
  [api-root concise-handlers & [{:keys [produces consumes] :as opts}]]
  (build-instance api-root (concise->handlers concise-handlers produces consumes) opts))
