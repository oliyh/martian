(ns martian.schema-tools
  (:require [flatland.ordered.set :refer [ordered-set]]
            [schema.core :as s #?@(:cljs [:refer [MapEntry EqSchema]])]
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
  "Returns a collection of paths which would address all possible entries (using `get-in`) in data described by the schema"
  [schema]
  (when (map? schema)
    (loop [paths (ordered-set [])
           paths-and-schemas (with-paths [] schema)]
      (if-let [{:keys [path schema]} (first paths-and-schemas)]
        (recur (conj paths path) (concat (rest paths-and-schemas)
                                         (with-paths path schema)))
        (vec paths)))))

;;

;; TODO: Cover with more tests and lean on the `schema-tools.walk` if possible.

(defn walk-with-path
  "Identical to `clojure.walk/walk` except keeps track of the path through the data structure (as per `get-in`)
   as it goes, calling `inner` and `outer` with two args: the path and form"
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
