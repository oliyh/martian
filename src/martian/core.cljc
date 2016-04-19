(ns martian.core
  (:require [tripod.path :as t]
            [clojure.string :as string]))

(defn- ->tripod-route [url-pattern swagger-definition]
  (let [trailing-slash? (re-find #"/$" (name url-pattern))
        path-parts (as->
                       (string/split (name url-pattern) #"/") pp
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
     :route-name (keyword (:operationId swagger-definition))}))

(defn- swagger->tripod [swagger-json]
  (reduce-kv
   (fn [tripod-routes url-pattern swagger-definition]
     (into tripod-routes (map (partial ->tripod-route url-pattern) (vals swagger-definition))))
   []
   (:paths swagger-json)))

(defn bootstrap
  "Creates a routing function which should be supplied with an api-root and a swagger spec

   (let [url-for (bootstrap \"https://api.org\" swagger-spec)]
     (url-for :load-pet {:id 123}))

   ;; => https://api.org/pets/123"
  [api-root swagger-json]
  (comp (partial str api-root)
        (t/path-for-routes (swagger->tripod swagger-json))))
