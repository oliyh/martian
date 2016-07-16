(ns martian.core
  (:require [tripod.context :as tc]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [martian.interceptors :as interceptors]
            [martian.schema :as schema]
            [schema.core :as s]))

(def default-interceptors
  [interceptors/set-method
   interceptors/set-url
   interceptors/set-query-params
   interceptors/set-body-params
   interceptors/set-form-params
   interceptors/set-header-params])

(defn- body-schema [definitions swagger-params]
  (when-let [body-param (first (not-empty (filter #(= "body" (:in %)) swagger-params)))]
    (schema/make-schema definitions body-param)))

(defn- form-schema [definitions swagger-params]
  (when-let [form-params (not-empty (filter #(= "formData" (:in %)) swagger-params))]
    (schema/schemas-for-parameters definitions form-params)))

(defn- path-schema [definitions swagger-params]
  (when-let [path-params (not-empty (filter #(= "path" (:in %)) swagger-params))]
    (schema/schemas-for-parameters definitions path-params)))

(defn- query-schema [definitions swagger-params]
  (when-let [query-params (not-empty (filter #(= "query" (:in %)) swagger-params))]
    (schema/schemas-for-parameters definitions query-params)))

(defn- headers-schema [definitions swagger-params]
  (when-let [query-params (not-empty (filter #(= "header" (:in %)) swagger-params))]
    (schema/schemas-for-parameters definitions query-params)))

(defn- response-schemas [definitions swagger-responses]
  (for [[status response] swagger-responses]
    {:status (s/eq status)
     :body (schema/make-schema definitions response)}))

(defn- sanitise [x]
  (if (string? x)
    x
    ;; consistent across clj and cljs
    (-> (str x)
        (string/replace-first ":" ""))))

(defn- tokenise-path [url-pattern]
  (let [url-pattern (sanitise url-pattern)
        parts (map first (re-seq #"([^{}]+|\{.+?\})" url-pattern))]
    (map #(if-let [param-name (second (re-matches #"^\{(.*)\}" %))]
            (keyword param-name)
            %) parts)))

(defn- ->handler [{:keys [definitions] :as swagger-map} url-pattern [method swagger-definition]]
  (let [path-parts (tokenise-path url-pattern)
        uri (string/join (map str path-parts))
        parameters (:parameters swagger-definition)]
    {:path uri
     :path-parts path-parts
     :method method
     :path-schema (path-schema definitions parameters)
     :query-schema (query-schema definitions parameters)
     :body-schema (body-schema definitions parameters)
     :form-schema (form-schema definitions parameters)
     :headers-schema (headers-schema definitions parameters)
     :response-schemas (response-schemas definitions (:responses swagger-definition))
     :swagger-definition (merge (select-keys swagger-map [:produces :consumes])
                                swagger-definition)
     ;; todo path constraints - required?
     ;; :path-constraints {:id "(\\d+)"},
     ;; {:in "path", :name "id", :description "", :required true, :type "string", :format "uuid"
     :route-name (->kebab-case-keyword (:operationId swagger-definition))}))

(defn- swagger->handlers [swagger-json]
  (reduce-kv
   (fn [handlers url-pattern swagger-handlers]
     (into handlers (map (partial ->handler
                                  swagger-json
                                  url-pattern)
                         swagger-handlers)))
   []
   (:paths swagger-json)))

(defn- find-handler [handlers route-name]
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

(defn- build-instance [api-root swagger-json {:keys [interceptors]}]
  (->Martian api-root (swagger->handlers swagger-json) (or interceptors default-interceptors)))

(defn bootstrap-swagger
  "Creates a routing function which should be supplied with an api-root and a swagger spec

   (let [url-for (bootstrap \"https://api.org\" swagger-spec)]
     (url-for :load-pet {:id 123}))

   ;; => https://api.org/pets/123"
  [api-root swagger-json & [opts]]
  (build-instance api-root (keywordize-keys swagger-json) (keywordize-keys opts)))
