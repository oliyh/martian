(ns martian.core
  (:require [tripod.path :as t]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]))

(defn- sanitise [x]
  (if (string? x)
    (string/replace-first x "/" "")
    ;; consistent across clj and cljs
    (-> (str x)
        (string/replace-first ":" "")
        (string/replace-first "/" ""))))

(defn- ->tripod-route [url-pattern swagger-definition]
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
     ;; todo path constraints - required?
     ;; :path-constraints {:id "(\\d+)"},
     ;; {:in "path", :name "id", :description "", :required true, :type "string", :format "uuid"
     :route-name (keyword (some swagger-definition [:operationId "operationId"]))}))

(defn- swagger->tripod [swagger-json]
  (reduce-kv
   (fn [tripod-routes url-pattern swagger-definition]
     (into tripod-routes (map (partial ->tripod-route url-pattern) (vals swagger-definition))))
   []
   (some swagger-json [:paths "paths"])))

(defn bootstrap
  "Creates a routing function which should be supplied with an api-root and a swagger spec

   (let [url-for (bootstrap \"https://api.org\" swagger-spec)]
     (url-for :load-pet {:id 123}))

   ;; => https://api.org/pets/123"
  [api-root swagger-json]
  (let [path-for (t/path-for-routes (swagger->tripod swagger-json))]
    (fn [route-name & [params]]
      (str api-root (apply path-for (keyword route-name) [(keywordize-keys params)])))))
