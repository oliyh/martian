(ns martian.schema-tools
  (:require [schema.core :as s]
            [schema-tools.impl]))

(defn explicit-key [k]
  (if (s/specific-key? k) (s/explicit-schema-key k) k))

(defn concrete-key? [k]
  (or (keyword? k)
      (s/specific-key? k)
      (string? k)))

(defn- concat* [& xs]
  (apply concat (remove nil? xs)))

(defprotocol KeyPaths
  (-paths [schema path include-self?]
    "Returns a sequence of path vectors found within the given prefix `path`.
     If `include-self?` is true, includes `path` itself as the first element."))

(extend-protocol KeyPaths
  #?(:clj  clojure.lang.APersistentMap
     :cljs cljs.core.PersistentArrayMap)
  (-paths [schema path include-self?]
    (concat*
      (when include-self? (list path))
      (mapcat (fn [[k v]]
                (when (concrete-key? k)
                  (let [k' (explicit-key k)
                        path' (conj path k')]
                    (cons path' (-paths v path' false)))))
              schema)))

  ;; NB: Vector schemas are transparent (indices are ignored).
  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (-paths [schema path include-self?]
    (concat*
      (when include-self? (list path))
      (mapcat #(-paths % path false) schema)))

  schema.core.NamedSchema
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema path false))))

  schema.core.Maybe
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema path false))))

  schema.core.Constrained
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema path false))))

  schema.core.One
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema path false))))

  schema.core.Record
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema path false))))

  schema.core.Both
  (-paths [schema path include-self?]
    (let [inner-schemas (:schemas schema)]
      (concat*
        (when include-self? (list path))
        (mapcat #(-paths % (conj path :schemas) false) inner-schemas)
        (mapcat #(-paths % path false) inner-schemas))))

  schema.core.Either
  (-paths [schema path include-self?]
    (let [inner-schemas (:schemas schema)]
      (concat*
        (when include-self? (list path))
        (mapcat #(-paths % (conj path :schemas) false) inner-schemas)
        (mapcat #(-paths % path false) inner-schemas))))

  schema.core.CondPre
  (-paths [schema path include-self?]
    (let [inner-schemas (:schemas schema)]
      (concat*
        (when include-self? (list path))
        (mapcat #(-paths % (conj path :schemas) false) inner-schemas)
        (mapcat #(-paths % path false) inner-schemas))))

  schema.core.ConditionalSchema
  (-paths [schema path include-self?]
    (let [inner-schemas (map second (:preds-and-schemas schema))]
      (concat*
        (when include-self? (list path))
        (mapcat #(-paths % (conj path :preds-and-schemas) false) inner-schemas)
        (mapcat #(-paths % path false) inner-schemas))))

  schema_tools.impl.Default
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema (conj path :value) true)
        (-paths inner-schema path false))))

  #?(:clj Object :cljs default)
  (-paths [_ path include-self?]
    (when include-self? (list path)))

  nil
  (-paths [_ _ _] nil))

(defn key-seqs
  "Returns a vec of unique key paths (key seqs) for `schema` and all subschemas
   that will cover all possible entries in a data described by `schema` as well
   as the `schema` itself."
  [schema]
  (->> (-paths schema [] true)
       (distinct)
       (vec)))

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
