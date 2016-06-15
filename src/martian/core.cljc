(ns martian.core
  (:require [tripod.path :as tp]
            [tripod.context :as tc]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [martian.schema :as schema]
            [martian.protocols :refer [Martian url-for request-for]]))

(def default-interceptors
  [{:name ::method
    :leave (fn [{:keys [response handler] :as ctx}]
             (update ctx :response assoc :method (:method handler)))}

   {:name ::uri
    :leave (fn [{:keys [request response path-for handler] :as ctx}]
             (let [path-schema (:path-schema handler)]
               (update ctx :response
                       assoc :uri (path-for (:path-parts handler)
                                            (schema/coerce-data path-schema (:params request))))))}

   {:name ::query-params
    :leave (fn [{:keys [request response handler] :as ctx}]
             (let [query-schema (:query-schema handler)
                   coerced-params (schema/coerce-data query-schema (:params request))]
               (if (not-empty coerced-params)
                 (update ctx :response assoc :query-params coerced-params)
                 ctx)))}

   {:name ::body-params
    :leave (fn [{:keys [request response handler] :as ctx}]
             (let [body-schema (:body-schema handler)
                   coerced-params (schema/coerce-data body-schema (:params request))]
               (if (not-empty coerced-params)
                 (update ctx :response assoc :body coerced-params)
                 ctx)))}])

(defn- body-schema [definitions swagger-params]
  (when-let [body-param (first (not-empty (filter #(= "body" (:in %)) swagger-params)))]
    (schema/make-schema definitions body-param)))

(defn- path-schema [definitions swagger-params]
  (when-let [path-params (not-empty (filter #(= "path" (:in %)) swagger-params))]
    (schema/schemas-for-parameters definitions path-params)))

(defn- query-schema [definitions swagger-params]
  (when-let [query-params (not-empty (filter #(= "query" (:in %)) swagger-params))]
    (schema/schemas-for-parameters definitions query-params)))

(defn- sanitise [x]
  (if (string? x)
    (string/replace-first x "/" "")
    ;; consistent across clj and cljs
    (-> (str x)
        (string/replace-first ":" "")
        (string/replace-first "/" ""))))

(defn- tokenise-path [url-pattern]
  (let [parts (map first (re-seq #"([^{}]+|\{.+?\})" url-pattern))]
    (println parts)
    (map #(if-let [param-name (second (re-matches #"^\{(.*)\}" %))]
            (keyword param-name)
            %) parts)))

(defn- ->tripod-route [definitions url-pattern [method swagger-definition]]
  (let [path-parts (tokenise-path url-pattern)
        uri (string/join "/" (map str path-parts))
        parameters (:parameters swagger-definition)]
    {:path uri
     :path-parts path-parts
     :method method
     :path-schema (path-schema definitions parameters)
     :query-schema (query-schema definitions parameters)
     :body-schema (body-schema definitions parameters)
     ;; todo path constraints - required?
     ;; :path-constraints {:id "(\\d+)"},
     ;; {:in "path", :name "id", :description "", :required true, :type "string", :format "uuid"
     :route-name (->kebab-case-keyword (:operationId swagger-definition))}))

(defn- swagger->tripod [swagger-json]
  (let [swagger-json (keywordize-keys swagger-json)]
    (reduce-kv
     (fn [tripod-routes url-pattern swagger-handlers]
       (into tripod-routes (map (partial ->tripod-route
                                         (:definitions swagger-json)
                                         url-pattern)
                                swagger-handlers)))
     []
     (:paths swagger-json))))

(defn- path-for [path-parts params]
  (println path-parts)
  (println params)
  (let [path-params (filter keyword? path-parts)]
    (string/join (map #(get params % %) path-parts))))

(defn- build-instance [api-root swagger-json]
  (let [tripod (swagger->tripod swagger-json)]
    (reify Martian
      (url-for [this route-name] (url-for this route-name {}))
      (url-for [this route-name params]
        (when-let [handler (first (filter #(= (keyword route-name) (:route-name %)) tripod))]
          (str api-root (path-for (:path-parts handler) (keywordize-keys params)))))

      (request-for [this route-name] (request-for this route-name {}))
      (request-for [this route-name params]
        (when-let [handler (first (filter #(= (keyword route-name) (:route-name %)) tripod))]
          (let [ctx (tc/enqueue* {} default-interceptors)]
            (:response (tc/execute
                        (assoc ctx
                               :path-for (comp (partial str api-root) path-for)
                               :request {:params params}
                               :handler handler)))))))))

(defn bootstrap-swagger
  "Creates a routing function which should be supplied with an api-root and a swagger spec

   (let [url-for (bootstrap \"https://api.org\" swagger-spec)]
     (url-for :load-pet {:id 123}))

   ;; => https://api.org/pets/123"
  [api-root swagger-json]
  (build-instance api-root swagger-json))
