(ns martian.defaults
  (:require [schema.core :as s #?@(:cljs [:refer [MapEntry EqSchema]])]
            [schema.spec.core :as spec]
            [martian.schema :refer [#?(:cljs SchemaWithMeta)]])
  #?(:clj (:import [schema.core MapEntry EqSchema]
                   [martian.schema SchemaWithMeta])))

(defn get-default [schema]
  (when (instance? SchemaWithMeta schema) (get-in schema [:meta :default])))

(defn- with-paths [path schema]
  (map (fn [schema]
         {:path (if (and (instance? MapEntry schema)
                         (instance? EqSchema (:key-schema schema)))
                  (conj path (:v (:key-schema schema)))
                  path)
          :schema schema})
       (spec/subschemas (s/spec schema))))

(defn defaults [schema]
  (when schema
    (loop [defaults {}
           paths-and-schemas (with-paths [] schema)]
      (if-let [{:keys [path schema]} (first paths-and-schemas)]
        (if-let [default (get-default schema)]
          (recur (assoc-in defaults path default) (concat (rest paths-and-schemas)
                                                          (with-paths path schema)))
          (recur defaults (concat (rest paths-and-schemas)
                                  (with-paths path schema))))
        defaults))))
