(ns martian.schema-tools
  (:require [camel-snake-kebab.core :refer [->kebab-case]]
            [schema.core :as s]
            [schema-tools.impl])
  #?(:clj (:import (clojure.lang IDeref))))

(defn explicit-key [k]
  (if (s/specific-key? k) (s/explicit-schema-key k) k))

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

(defprotocol PathAliases
  "Internal traversal API used to locate alias maps inside schemas.

   Implementations should walk `schema` along an `idiomatic-path` (a vector of
   kebab-case, unqualified segments) and, when the path lands on a map schema,
   return a map `{idiomatic -> original-explicit}` for that level; otherwise,
   return `nil`, i.e. don't traverse past the target level or build whole-tree
   results."
  (-aliases-at [schema idiomatic-path]
    "Returns the alias map available at `idiomatic-path` inside the `schema`,
     or `nil` if that location is not a map level or has no aliasable keys."))

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
  (-aliases-at [ms path]
    (if (empty? path)
      (map-entry-aliases ms)
      (let [seg (first path)]
        (when (or (keyword? seg) (string? seg))
          (when-some [child (child-by-idiomatic ms seg)]
            (-aliases-at child (rest path)))))))

  ;; Vector schemas are transparent: we merge aliases from element schemas,
  ;; including when `path` is empty, so deeply nested vectors work as well.
  #?(:clj  clojure.lang.APersistentVector
     :cljs cljs.core.PersistentVector)
  (-aliases-at [vs path]
    (combine-aliases-at path vs))

  ;; Single-child wrappers: aware of the inner `:schema` hop.

  schema.core.NamedSchema
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  schema.core.Maybe
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  schema.core.Constrained
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  schema.core.One
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  schema.core.Record
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  ;; Multi-variant unions: combine the alternatives; aware of any inner hops.

  schema.core.Both
  (-aliases-at [schema path]
    (let [inner-schemas (:schemas schema)]
      (cond
        (empty? path)             (combine-aliases-at [] inner-schemas)
        (= :schemas (first path)) (combine-aliases-at (rest path) inner-schemas)
        :else                     (combine-aliases-at path inner-schemas))))

  schema.core.Either
  (-aliases-at [schema path]
    (let [inner-schemas (:schemas schema)]
      (cond
        (empty? path)             (combine-aliases-at [] inner-schemas)
        (= :schemas (first path)) (combine-aliases-at (rest path) inner-schemas)
        :else                     (combine-aliases-at path inner-schemas))))

  schema.core.CondPre
  (-aliases-at [schema path]
    (let [inner-schemas (:schemas schema)]
      (cond
        (empty? path)             (combine-aliases-at [] inner-schemas)
        (= :schemas (first path)) (combine-aliases-at (rest path) inner-schemas)
        :else                     (combine-aliases-at path inner-schemas))))

  schema.core.ConditionalSchema
  (-aliases-at [schema path]
    (let [inner-schemas (map second (:preds-and-schemas schema))]
      (cond
        (empty? path)                       (combine-aliases-at [] inner-schemas)
        (= :preds-and-schemas (first path)) (combine-aliases-at (rest path) inner-schemas)
        :else                               (combine-aliases-at path inner-schemas))))

  ;; The `schema-tools`'s defaults: aware of the `:schema` and `:value` hops.
  schema_tools.impl.Default
  (-aliases-at [schema path]
    (let [inner-schema (:schema schema)]
      (cond
        (empty? path)            (-aliases-at inner-schema [])
        (= :schema (first path)) (-aliases-at inner-schema (rest path))
        (= :value  (first path)) (-aliases-at inner-schema (rest path))
        :else                    (-aliases-at inner-schema path))))

  #?(:clj Object :cljs default)
  (-aliases-at [_ _] nil)

  nil
  (-aliases-at [_ _] nil))

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
