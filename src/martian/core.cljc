(ns martian.core
  (:require [tripod.path :as t]
            [clojure.string :as string]))

(defn- ->tripod-route [url-pattern swagger-definition]
  {:path (string/replace (name url-pattern) #"\{(.*)\}" (fn [[_ path-part]] (str ":" path-part)))
   :path-parts (->> (:parameters swagger-definition)
                    (filter #(= "path" (:in %)))
                    (map :name)
                    (into [""]))
   ;; :path-constraints {:id "(\\d+)"},
   :route-name (:operationId swagger-definition)})

(defn- swagger->tripod [swagger-json]
  (reduce-kv
   (fn [tripod-routes url-pattern swagger-definition]
     (into tripod-routes (map (partial ->tripod-route url-pattern) (vals swagger-definition))))
   []
   (:paths swagger-json)))

(defn bootstrap [swagger-json]
  (t/path-for-routes (swagger->tripod swagger-json)))
