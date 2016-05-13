(ns martian.core
  (:require [tripod.path :as tp]
            [tripod.context :as tc]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [martian.protocols :refer [Martian url-for request-for]]))

(defn- make-interceptors [uri method swagger-definition]
  [{:name ::method
    :leave (fn [{:keys [response] :as ctx}]
             (update ctx :response assoc :method method))}

   {:name ::uri
    :leave (fn [{:keys [request response path-for handler] :as ctx}]
             (let [path-params (:path-parts handler)]
               (update ctx :response
                       assoc :uri (path-for (:route-name handler)
                                            (select-keys (:params request) path-params)))))}

   {:name ::body-params
    :leave (fn [{:keys [request response handler] :as ctx}]
             (if-let [body-param (:body-param handler)]
               (update ctx :response
                       assoc :body (get (:params request) body-param))
               ctx))}])

(defn- sanitise [x]
  (if (string? x)
    (string/replace-first x "/" "")
    ;; consistent across clj and cljs
    (-> (str x)
        (string/replace-first ":" "")
        (string/replace-first "/" ""))))

(defn- body-params [swagger-params]
  (if-let [body-param (first (filter #(= "body" (:in %)) swagger-params))]
    (keyword (string/lower-case (:name body-param)))))

(defn- ->tripod-route [url-pattern [method swagger-definition]]
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
     :body-param (body-params (:parameters swagger-definition))
     ;; todo path constraints - required?
     ;; :path-constraints {:id "(\\d+)"},
     ;; {:in "path", :name "id", :description "", :required true, :type "string", :format "uuid"
     :route-name (keyword (some swagger-definition [:operationId "operationId"]))}))

(defn- swagger->tripod [swagger-json]
  (reduce-kv
   (fn [tripod-routes url-pattern swagger-handlers]
     (into tripod-routes (map (partial ->tripod-route url-pattern) swagger-handlers)))
   []
   (some swagger-json [:paths "paths"])))

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
