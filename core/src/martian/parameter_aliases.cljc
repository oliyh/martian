(ns martian.parameter-aliases
  (:require [clojure.set :refer [rename-keys]]
            [martian.schema-tools :as schema-tools]
            [schema.core :as s]))

(defn- aliases-at
  "Internal helper. Given a `schema`, a path-local `cache` (atom), an `interner`
   (atom), and a `path` (vector or seq), returns the alias map for that path.

   - Delegates the actual computation to `compute-aliases-at`, which understands
     schema wrappers (e.g. `:schema`, `:schemas`, etc.) and vector transparency.
   - Caches results per-path in the provided `cache` to avoid recomputation.
   - Interns identical alias maps across the paths via `interner`, so equal maps
     share a single canonical instance (reduces memory churn in large APIs).
   - Returns `nil` when there are no aliases at the given path."
  [schema cache interner path]
  (let [path' (if (vector? path) path (vec path))]
    (or (get @cache path')
        (let [m (schema-tools/compute-aliases-at schema path')
              m' (when m
                   (or (get @interner m)
                       (-> interner
                           (swap! #(if (contains? % m) % (assoc % m m)))
                           (get m))))]
          (swap! cache assoc path' m')
          m'))))

#?(:bb nil

   :clj
   (deftype LazyRegistry [schema cache interner]
     clojure.lang.ILookup
     (valAt [_ k]
       (aliases-at schema cache interner k))
     (valAt [_ k not-found]
       (or (aliases-at schema cache interner k) not-found))
     Object
     (toString [_] (str "#LazyRegistry (cached " (count @cache) ")")))

   :cljs
   (deftype LazyRegistry [schema cache interner]
     cljs.core/ILookup
     (-lookup [_ k]
       (aliases-at schema cache interner k))
     (-lookup [_ k not-found]
       (or (aliases-at schema cache interner k) not-found))
     cljs.core/IPrintWithWriter
     (-pr-writer [_ writer _opts]
       (-write writer (str "#LazyRegistry (cached " (count @cache) ")")))))

(defn- idiomatic-path [path]
  (vec (keep schema-tools/->idiomatic path)))

(defn aliases-hash-map
  "Eagerly computes the registry as a data structure for the given `schema`.

   Produces a plain hash map with idiomatic keys (aliases) mappings per path
   in a (possibly, deeply nested) `schema` for all its unqualified keys.

   The result is then used with `alias-schema` and `unalias-data` functions."
  [schema]
  (reduce (fn [acc path]
            (let [leaf (peek path)
                  idiomatic-key (some-> leaf (schema-tools/->idiomatic))]
              (if (and idiomatic-key (not= leaf idiomatic-key))
                (update acc (idiomatic-path (pop path)) assoc idiomatic-key leaf)
                acc)))
          {}
          (schema-tools/key-seqs schema)))

(defn registry
  "Builds a lookupable registry of parameter alias maps for the given `schema`.

  - On JVM/CLJS:
    Returns an instance of a lazy registry.

    Aliases are computed on demand (via `compute-aliases-at`), so materializing
    massive alias maps upfront is avoided. Per-path results are memoized within
    the registry. Identical alias maps are shared to cut memory usage.

    A returned value implements `ILookup` and is indexed by \"idiomatic paths\".
    Looking up a path gives an alias map for that level, mapping idiomatic keys
    (kebab-case, unqualified) to their original schema keys.

  - On Babashka:
    Returns a plain hash map registry that is computed eagerly via `key-seqs`."
  [schema]
  (when schema
    #?(:bb      (aliases-hash-map schema)
       :default (new LazyRegistry schema (atom {}) (atom {})))))

;; TODO: An alias for backward compatibility. Remove later on.
(def parameter-aliases registry)

(defn unalias-data
  "Given a (possibly, deeply nested) `data` structure, returns it with all its
   keys renamed from \"idiomatic\" (aliases) using the given parameter aliases
   `registry`."
  [registry data]
  (if registry
    (schema-tools/prewalk-with-path
      (fn [path x]
        (if (map? x)
          (rename-keys x (get registry (idiomatic-path path)))
          x))
      data)
    data))

(defn alias-schema
  "Given a (possibly, deeply nested) `schema`, renames all keys (in it and its
   subschemas) into corresponding \"idiomatic\" keys (aliases) using the given
   parameter aliases `registry`."
  [registry schema]
  (if registry
    (schema-tools/prewalk-with-path
      (fn [path subschema]
        (if (map? subschema)
          (let [kmap (reduce-kv (fn [kmap idiomatic original]
                                  (assoc kmap
                                    original idiomatic
                                    (s/optional-key original) (s/optional-key idiomatic)))
                                {}
                                (get registry (idiomatic-path path)))]
            (rename-keys subschema kmap))
          subschema))
      schema)
    schema))
