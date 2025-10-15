(ns martian.schema-tools
  (:require [camel-snake-kebab.core :refer [->kebab-case]]
            [schema.core :as s]
            [schema-tools.impl]))

(defn explicit-key [k]
  (if (s/specific-key? k) (s/explicit-schema-key k) k))

(defn concrete-key?
  "Checks if the schema key `k` is not generic (`s/Any`, `s/Keyword`, etc.)."
  [k]
  (or (keyword? k) (s/specific-key? k) (string? k)))

(defn- can-be-renamed? [k]
  ;; NB: See `camel-snake-kebab.internals.alter-name` ns.
  (or (and (keyword? k) (not (namespace k))) (string? k)))

(defn ->idiomatic [k]
  (when-some [k' (explicit-key k)]
    (when (can-be-renamed? k')
      (->kebab-case k'))))

(defn map-entry-aliases
  "Returns a map of idiomatic keys to original explicit keys for the immediate
   entries of the given map schema `ms`.

   - Considers only keys that can be renamed: unqualified keywords or strings;
   - Uses the `explicit-key` helper function to unwrap required/optional keys;
   - Includes an entry only when the idiomatic form differs from the original;
   - Returns `nil` when there are no aliasable entries at this level."
  [ms]
  (not-empty
    (reduce-kv
      (fn [acc k _]
        (let [ek (explicit-key k)
              ik (->idiomatic ek)]
          (if (and ik (not= ik ek))
            (assoc acc ik ek)
            acc)))
      {}
      ms)))

(defn child-by-idiomatic
  "Finds the child schema addressed by the next path segment `seg` in the map
   schema `ms`.

   The `seg` is one idiomatic path segment (a kebab-case, unqualified keyword
   or string). The function scans entries of `ms` and returns the value whose
   key, after being idiomatized, equals `seg`."
  [ms seg]
  (some (fn [[k v]] (when (= seg (->idiomatic k)) v)) ms))

(defn- concat* [& xs]
  (apply concat (remove nil? xs)))

(def ^:dynamic *seen-recursion*
  "Cycle/budget guard for `s/recursive` schema targets. Bound in `key-seqs` fn."
  nil)

(def ^:dynamic *max-recursions-per-target*
  "Maximum number of times the same recursive schema target may be expanded
   along a single path."
  3)

(defmacro with-recursion-guard [rec-target form]
  `(when ~rec-target
     (let [n# (get @*seen-recursion* ~rec-target 0)]
       (when (< n# *max-recursions-per-target*)
         (vswap! *seen-recursion* update ~rec-target (fnil inc 0))
         (let [res# ~form]
           (vswap! *seen-recursion* update ~rec-target #(max 0 (dec %)))
           res#)))))

(defprotocol PathAliases
  "Internal traversal API used to locate alias maps inside Prismatic schemas."
  (-paths [schema path include-self?]
    "Returns a sequence of path vectors found within the given prefix `path`.
     If `include-self?` is true, includes `path` itself as the first element.")
  (-aliases-at [schema idiomatic-path]
    "Returns the alias map available at `idiomatic-path` inside the `schema`,
     or `nil` if that location is not a map level or has no aliasable keys.

     Implementations should walk `schema` along an `idiomatic-path` (a vector of
     kebab-case, unqualified segments) and, when the path lands on a map schema,
     return an alias map for that level; otherwise return `nil`. Do not traverse
     past the target level or build whole-tree results."))

(defn combine-aliases-at
  "Collects and merges alias maps from multiple `inner-schemas` at the single
   idiomatic `path`.

   Calls `(-aliases-at s path)` for each inner schema, discards `nil` results,
   and merges the remaining maps left-to-right with `merge` semantics. Returns
   `nil` when nothing contributes, i.e. the merged result would be empty."
  [path inner-schemas]
  (not-empty (apply merge (keep #(-aliases-at % path) inner-schemas))))

(extend-protocol PathAliases

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
  (-aliases-at [schema path]
    (if (empty? path)
      (map-entry-aliases schema)
      (let [seg (first path)]
        (when (or (keyword? seg) (string? seg))
          (when-some [child (child-by-idiomatic schema seg)]
            (-aliases-at child (rest path)))))))

  ;; Vector schemas are transparent
  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (-paths [schema path include-self?]
    (concat*
      (when include-self? (list path))
      (mapcat #(-paths % path false) schema)))
  (-aliases-at [schema path]
    (combine-aliases-at path schema))

  ;; Single-child wrappers

  schema.core.NamedSchema
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema path false))))
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  schema.core.Maybe
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema path false))))
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  schema.core.Constrained
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema path false))))
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  schema.core.One
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema path false))))
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  schema.core.Record
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema path false))))
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  ;; Recursive schemas
  schema.core.Recursive
  (-paths [schema path include-self?]
    (let [target (:derefable schema)]
      (concat*
        (when include-self? (list path))
        (with-recursion-guard
          target
          (let [inner-schema @target]
            (concat
              (-paths inner-schema (conj path :derefable) true)
              (-paths inner-schema path false)))))))
  (-aliases-at [schema path]
    (let [inner-schema @(:derefable schema)]
      (cond
        (empty? path)               (-aliases-at inner-schema [])
        (= :derefable (first path)) (-aliases-at inner-schema (rest path))
        :else                       (-aliases-at inner-schema path))))

  ;; Multi-variant unions

  schema.core.Both
  (-paths [schema path include-self?]
    (let [inner-schemas (:schemas schema)]
      (concat*
        (when include-self? (list path))
        (mapcat #(-paths % (conj path :schemas) false) inner-schemas)
        (mapcat #(-paths % path false) inner-schemas))))
  (-aliases-at [schema path]
    (let [inner-schemas (:schemas schema)]
      (cond
        (empty? path)             (combine-aliases-at [] inner-schemas)
        (= :schemas (first path)) (combine-aliases-at (rest path) inner-schemas)
        :else                     (combine-aliases-at path inner-schemas))))

  schema.core.Either
  (-paths [schema path include-self?]
    (let [inner-schemas (:schemas schema)]
      (concat*
        (when include-self? (list path))
        (mapcat #(-paths % (conj path :schemas) false) inner-schemas)
        (mapcat #(-paths % path false) inner-schemas))))
  (-aliases-at [schema path]
    (let [inner-schemas (:schemas schema)]
      (cond
        (empty? path)             (combine-aliases-at [] inner-schemas)
        (= :schemas (first path)) (combine-aliases-at (rest path) inner-schemas)
        :else                     (combine-aliases-at path inner-schemas))))

  schema.core.CondPre
  (-paths [schema path include-self?]
    (let [inner-schemas (:schemas schema)]
      (concat*
        (when include-self? (list path))
        (mapcat #(-paths % (conj path :schemas) false) inner-schemas)
        (mapcat #(-paths % path false) inner-schemas))))
  (-aliases-at [schema path]
    (let [inner-schemas (:schemas schema)]
      (cond
        (empty? path)             (combine-aliases-at [] inner-schemas)
        (= :schemas (first path)) (combine-aliases-at (rest path) inner-schemas)
        :else                     (combine-aliases-at path inner-schemas))))

  schema.core.ConditionalSchema
  (-paths [schema path include-self?]
    (let [inner-schemas (map second (:preds-and-schemas schema))]
      (concat*
        (when include-self? (list path))
        (mapcat #(-paths % (conj path :preds-and-schemas) false) inner-schemas)
        (mapcat #(-paths % path false) inner-schemas))))
  (-aliases-at [schema path]
    (let [inner-schemas (map second (:preds-and-schemas schema))]
      (cond
        (empty? path)                       (combine-aliases-at [] inner-schemas)
        (= :preds-and-schemas (first path)) (combine-aliases-at (rest path) inner-schemas)
        :else                               (combine-aliases-at path inner-schemas))))

  ;; Default schemas (from `schema-tools`)
  schema_tools.impl.Default
  (-paths [schema path include-self?]
    (let [inner-schema (:schema schema)]
      (concat*
        (when include-self? (list path))
        (-paths inner-schema (conj path :schema) true)
        (-paths inner-schema (conj path :value) true)
        (-paths inner-schema path false))))
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        (= :value  (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  #?(:clj Object :cljs default)
  (-paths [_ path include-self?]
    (when include-self? (list path)))
  (-aliases-at [_ _] nil)

  nil
  (-paths [_ _ _] nil)
  (-aliases-at [_ _] nil))

(defn key-seqs
  "Returns a vec of unique key paths (key seqs) for `schema` and all subschemas
   that will cover all possible entries in a data described by `schema` as well
   as the `schema` itself."
  [schema]
  (->> (binding [*seen-recursion* (volatile! {})]
         (-paths schema [] true))
       (distinct)
       (vec)))

(defn compute-aliases-at
  "Given a `schema` and an `idiomatic-path` (a vector of kebab-case, unqualified
   segments), returns a map which describes how the idiomatic keys at that exact
   map level are translated back to the schema's original keys.

   - Segments and keys are considered idiomatic when they are unqualified and
     kebab-case keywords/strings, e.g. `:foo-bar`. Qualified keywords/symbols
     are ignored; generic map keys (e.g. `s/Any`, `s/Keyword`) do not produce
     aliases.
   - Only the addressed level. The result contains aliases for the entries of
     that map, not for ancestors or nested child maps.
   - Vectors are transparent. If the path walks through a vector, aliases are
     merged from its element schemas.
   - Inner schemas hops are understood. Structural hops introduced by wrapper
     or union schemas are recognized (e.g. `:schema`, `:schemas`, etc.).
   - Non-map endpoints yield `nil`. If the `idiomatic-path` doesn't land on a
     map (or there are no aliasable keys), the function returns `nil`."
  [schema idiomatic-path]
  (-aliases-at schema idiomatic-path))

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

(defn postwalk-with-path
  ([f form]
   (postwalk-with-path f [] form))
  ([f path form]
   (walk-with-path (fn [path form] (postwalk-with-path f path form))
                   f
                   path
                   form)))

(defn prewalk-with-path
  ([f form]
   (prewalk-with-path f [] form))
  ([f path form]
   (walk-with-path (fn [path form] (prewalk-with-path f path form))
                   (fn [_path form] form)
                   path
                   (f path form))))
