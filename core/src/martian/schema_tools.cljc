(ns martian.schema-tools
  (:require [flatland.ordered.set :refer [ordered-set]]
            [schema.core :as s #?@(:cljs [:refer [MapEntry EqSchema]])]
            [schema.spec.core :as spec])
  #?(:clj (:import [schema.core MapEntry EqSchema])))

(defn unspecify-key [k]
  (if (s/specific-key? k)
    (s/explicit-schema-key k)
    k))

(defn with-paths [path schema]
  (keep (fn [schema]
          (cond (and (instance? MapEntry schema)
                     (instance? EqSchema (:key-schema schema)))
                {:path (conj path (:v (:key-schema schema)))
                 :schema (:val-schema schema)}
                (map? schema)
                {:path path
                 :schema schema}
                (vector? schema)
                {:path (conj path :martian/idx)
                 :schema (first schema)}))
        (spec/subschemas (s/spec schema))))

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
     (list? form) (outer path (apply list (map (partial inner path) form)))
     (map-entry? form)
     (outer path #?(:clj (clojure.lang.MapEntry. (inner path (key form)) (inner (conj path (key form)) (val form)))
                    :cljs (cljs.core/MapEntry. (inner path (key form)) (inner (conj path (key form)) (val form)) nil)))
     (seq? form) (outer path (doall (map (partial inner path) form)))
     (record? form) (outer path (reduce (fn [r x] (conj r (inner path x))) form form))
     (coll? form) (outer path (into (empty form) (map (partial inner path) form)))
     :else (outer path form))))

(defn postwalk-with-path [f path form]
  (walk-with-path (partial postwalk-with-path f) f path form))

(defn prewalk-with-path [f path form]
  (walk-with-path (partial prewalk-with-path f) (fn [_path form] form) path (f path form)))
