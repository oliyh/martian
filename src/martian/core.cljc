(ns martian.core
  (:require [tripod.path :as t]
            [clojure.string :as string]))

(defn- ->tripod-route [url-pattern swagger-definition]
  (let [path-parts (->> (string/split (name url-pattern) #"/")
                        (mapv (fn [part]
                               (if-let [[_ token] (re-matches #"\{(.*)\}" part)]
                                 (keyword token)
                                 part)))
                        (into [""]))]
    {:path (string/join "/" (map str path-parts))
     :path-parts path-parts
     ;; todo path constraints
     ;; :path-constraints {:id "(\\d+)"},
     ;; {:in "path", :name "id", :description "", :required true, :type "string", :format "uuid"
     :route-name (keyword (:operationId swagger-definition))}))

(defn- swagger->tripod [swagger-json]
  (reduce-kv
   (fn [tripod-routes url-pattern swagger-definition]
     (into tripod-routes (map (partial ->tripod-route url-pattern) (vals swagger-definition))))
   []
   (:paths swagger-json)))

(defn bootstrap [swagger-json]
  (t/path-for-routes (swagger->tripod swagger-json)))
