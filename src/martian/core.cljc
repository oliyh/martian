(ns martian.core
  (:require [tripod.path :as tp]
            [tripod.context :as tc]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [martian.protocols :refer [Martian url-for request-for]]))

(defn- make-interceptors [method swagger-definition]
  [{:name ::method
    :leave (fn [{:keys [response] :as ctx}]
             (assoc response :method method))}])

(defn- sanitise [x]
  (if (string? x)
    (string/replace-first x "/" "")
    ;; consistent across clj and cljs
    (-> (str x)
        (string/replace-first ":" "")
        (string/replace-first "/" ""))))

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
                     (concat pp (when trailing-slash? [""])))]
    {:path (string/join "/" (map str path-parts))
     :path-parts path-parts
     :interceptors (make-interceptors method swagger-definition)
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
          (tc/execute (tc/enqueue* {:request {:params params}} (:interceptors handler))))))))

(defn bootstrap
  "Creates a routing function which should be supplied with an api-root and a swagger spec

   (let [url-for (bootstrap \"https://api.org\" swagger-spec)]
     (url-for :load-pet {:id 123}))

   ;; => https://api.org/pets/123"
  [api-root swagger-json]
  (build-instance api-root swagger-json))
