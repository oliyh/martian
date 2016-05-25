(ns martian.core
  (:require [tripod.path :as tp]
            [tripod.context :as tc]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [schema.core :as s]
            [schema.coerce :as sc]
            [martian.protocols :refer [Martian url-for request-for]]))

(defn- coerce-data [schema data]
  (some->> (keys schema)
           (map s/explicit-schema-key)
           (select-keys data)
           ((sc/coercer! schema sc/string-coercion-matcher))))

(defn- make-interceptors [uri method swagger-definition]
  [{:name ::method
    :leave (fn [{:keys [response] :as ctx}]
             (update ctx :response assoc :method method))}

   {:name ::uri
    :leave (fn [{:keys [request response path-for handler] :as ctx}]
             (let [path-params (:path-params handler)]
               (update ctx :response
                       assoc :uri (path-for (:route-name handler)
                                            (coerce-data path-params (:params request))))))}

   {:name ::query-params
    :leave (fn [{:keys [request response handler] :as ctx}]
             (let [query-params (:query-params handler)
                   coerced-params (coerce-data query-params (:params request))]
               (if (not-empty coerced-params)
                 (update ctx :response assoc :query-params coerced-params)
                 ctx)))}

   {:name ::body-params
    :leave (fn [{:keys [request response handler] :as ctx}]
             (let [{:keys [parameter-name schema]} (:body-param handler)
                   coerced-params (coerce-data schema (get-in request [:params parameter-name]))]
               (if (not-empty coerced-params)
                 (update ctx :response assoc :body coerced-params)
                 ctx)))}])

(defn- sanitise [x]
  (if (string? x)
    (string/replace-first x "/" "")
    ;; consistent across clj and cljs
    (-> (str x)
        (string/replace-first ":" "")
        (string/replace-first "/" ""))))

(declare make-schema)

(defn schemas-for-parameters [definitions parameters]
  (->> (for [{:keys [name required] :as parameter} parameters
             :let [name (->kebab-case-keyword name)
                   schema (make-schema definitions parameter)]]
         [(if required name (s/optional-key name))
          (if required schema (s/maybe schema))])
       (into {})))

(defn make-schema [definitions {:keys [name required type enum schema properties]}]
  (cond
    enum (apply s/enum enum)
    (= "string" type) s/Str
    (= "integer" type) s/Int

    (:$ref schema)
    (make-schema definitions
                 (some->> (:$ref schema)
                          (re-find #"#/definitions/(.*)")
                          second
                          keyword
                          definitions))

    (= "object" type)
    (schemas-for-parameters definitions (map (fn [[name p]]
                                               (assoc p :name name)) properties))

    :default s/Any))

(defn- body-param [definitions swagger-params]
  (when-let [body-param (first (filter #(= "body" (:in %)) swagger-params))]
    {:parameter-name (->kebab-case-keyword (:name body-param))
     :schema (make-schema definitions body-param)}))

(defn- path-params [definitions swagger-params]
  (when-let [path-params (not-empty (filter #(= "path" (:in %)) swagger-params))]
    (schemas-for-parameters definitions path-params)))

(defn- query-params [definitions swagger-params]
  (when-let [query-params (not-empty (filter #(= "query" (:in %)) swagger-params))]
    (schemas-for-parameters definitions query-params)))

(defn- ->tripod-route [definitions url-pattern [method swagger-definition]]
  (let [url-pattern (sanitise url-pattern)
        trailing-slash? (re-find #"/$" url-pattern)
        path-parts (as->
                       (string/split url-pattern #"/") pp
                     (mapv (fn [part]
                             (if-let [[_ token] (re-matches #"\{(.*)\}" part)]
                               (keyword token)
                               part)) pp)
                     (into [""] pp)
                     (concat pp (when trailing-slash? [""])))
        uri (string/join "/" (map str path-parts))]
    {:path uri
     :path-parts path-parts
     :interceptors (make-interceptors uri method swagger-definition)
     :path-params (path-params definitions (:parameters swagger-definition))
     :query-params (query-params definitions (:parameters swagger-definition))
     :body-param (body-param definitions (:parameters swagger-definition))
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

(defn- build-instance [api-root swagger-json]
  (let [tripod (swagger->tripod swagger-json)
        path-for (tp/path-for-routes tripod)]
    (reify Martian
      (url-for [this route-name] (url-for this route-name {}))
      (url-for [this route-name params]
        (str api-root (apply path-for (keyword route-name) [(keywordize-keys params)])))

      (request-for [this route-name] (request-for this route-name {}))
      (request-for [this route-name params]
        (when-let [handler (first (filter #(= route-name (:route-name %)) tripod))]
          (let [ctx (tc/enqueue* {} (:interceptors handler))]
            (:response (tc/execute
                        (assoc ctx
                               :path-for (comp (partial str api-root) path-for)
                               :request {:params params}
                               :handler handler)))))))))

(defn bootstrap
  "Creates a routing function which should be supplied with an api-root and a swagger spec

   (let [url-for (bootstrap \"https://api.org\" swagger-spec)]
     (url-for :load-pet {:id 123}))

   ;; => https://api.org/pets/123"
  [api-root swagger-json]
  (build-instance api-root swagger-json))
