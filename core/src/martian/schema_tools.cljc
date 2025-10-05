(ns martian.schema-tools
  (:require [schema.core :as s #?@(:cljs [:refer [MapEntry EqSchema]])]
            [schema-tools.impl :as sti]
            [schema.spec.core :as spec])
  #?(:clj (:import [schema.core MapEntry EqSchema])))

(defn unspecify-key [k]
  (if (s/specific-key? k)
    (s/explicit-schema-key k)
    k))

(def default-schema? #'sti/default?)

(defn with-paths [path schema]
  (when (satisfies? schema.core/Schema schema)
    (->> (spec/subschemas (s/spec schema))
         (mapcat (fn [schema]
                   (cond (and (instance? MapEntry schema)
                              (instance? EqSchema (:key-schema schema)))
                         (let [key-schema-v (:v (:key-schema schema))
                               val-schema (:val-schema schema)]
                           (if (default-schema? val-schema)
                             [{:path (conj path key-schema-v)
                               :schema val-schema}
                              {:path (conj path key-schema-v :schema)
                               :schema (:schema val-schema)}
                              {:path (conj path key-schema-v :value)
                               :schema (:value val-schema)}]
                             [{:path (conj path key-schema-v)
                               :schema val-schema}]))
                         (map? schema)
                         [{:path path
                           :schema schema}]
                         (vector? schema)
                         [{:path (conj path ::idx) ; must be qualified!
                           :schema (first schema)}])))
         (remove nil?))))

(defn key-seqs
  "Returns a coll of paths (key seqs) which would address all possible entries
   in a data described by the given `schema` as well as the `schema` itself."
  [schema]
  (when (map? schema)
    (loop [paths [[]]
           paths-and-schemas (with-paths [] schema)]
      (if-let [{:keys [path schema]} (first paths-and-schemas)]
        (recur (conj paths path) (concat (rest paths-and-schemas)
                                         (with-paths path schema)))
        (distinct paths)))))

;;

(defn walk-with-path
  "Similar to the `schema-tools.walk/walk` except it keeps track of the `path`
   through the data structure as it goes, calling `inner` and `outer` with two
   args: the `path` and the `form`. It also does not preserve any metadata."
  ([inner outer form] (walk-with-path inner outer [] form))
  ([inner outer path form]
   (cond
     (map-entry? form)
     (outer path [(inner path (key form))
                  (inner (conj path (key form)) (val form))])
     (record? form)
     (outer path (reduce (fn [r x] (conj r (inner path x))) form form))
     (list? form)
     (outer path (apply list (map #(inner path %) form)))
     (seq? form)
     (outer path (doall (map #(inner path %) form)))
     (coll? form)
     (outer path (into (empty form) (map #(inner path %) form)))
     :else (outer path form))))

(defn postwalk-with-path [f path form]
  (walk-with-path (fn [path form] (postwalk-with-path f path form))
                  f
                  path
                  form))

(defn prewalk-with-path [f path form]
  (walk-with-path (fn [path form] (prewalk-with-path f path form))
                  (fn [_path form] form)
                  path
                  (f path form)))
