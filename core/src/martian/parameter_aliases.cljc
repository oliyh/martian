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

(def ^:dynamic *max-aliases-path-length*
  "Maximum idiomatic path length allowed during the alias-driven expansion."
  20)

(defn aliases-hash-map
  "Eagerly compute the registry as a plain hash map for the given `schema`.

   NB: This covers schema wrapper-aware paths (e.g. `[:baz :schema :quux]`)
       and equivalent data-level paths (e.g. `[:baz :quux]`) uniformly."
  [schema]
  (let [*amap (volatile! {})
        *seen (volatile! #{})
        *pick (volatile! [])

        explore! (fn [path]
                   (when-not (contains? @*seen path)
                     (vswap! *seen conj path)
                     (when-let [m (schema-tools/compute-aliases-at schema path)]
                       (vswap! *amap assoc path m)
                       (when (< (count path) *max-aliases-path-length*)
                         (vswap! *pick into (map #(conj path %) (keys m)))))))]
    ;; structure-driven seeding
    (schema-tools/prewalk-with-path
      (fn [p x] (explore! (idiomatic-path p)) x)
      []
      schema)
    ;; drain alias-driven paths (covers data-level hops)
    (loop []
      (when-some [paths (not-empty @*pick)]
        (vreset! *pick (pop paths))
        (explore! (peek paths))
        (recur)))
    @*amap))

(defn parameter-aliases
  "Builds a lookupable registry of parameter alias maps for the given `schema`.

  - On JVM/CLJS:
    Returns an instance of a lazy registry.

    Aliases are computed on demand (via `compute-aliases-at`), so materializing
    massive alias maps upfront is avoided. Per-path results are memoized within
    the registry. Identical alias maps are shared to cut memory usage.

    The returned value implements `ILookup` and is indexed by an idiomatic path
    (a vector of segments as used when walking data/schemas). Looking up a path
    yields the alias map for that level, mapping \"idiomatic keys\" (kebab-case,
    unqualified) to their original explicit schema keys (with optional/required
    wrappers when applicable).

  - On Babashka:
    Returns a plain hash map that is computed eagerly via `compute-aliases-at`."
  [schema]
  (when schema
    #?(:bb      (aliases-hash-map schema)
       :default (new LazyRegistry schema (atom {}) (atom {})))))

(defn unalias-data
  "Given a (possibly, deeply nested) data `x`, returns the data with all keys
   renamed from \"idiomatic\" using the given `parameter-aliases` registry."
  [parameter-aliases x]
  (if parameter-aliases
    (schema-tools/prewalk-with-path
      (fn [path x]
        (if (map? x)
          (rename-keys x (get parameter-aliases (idiomatic-path path)))
          x))
      []
      x)
    x))

(defn alias-schema
  "Given a (possibly, deeply nested) `schema`, renames all keys (in it and its
   subschemas) into corresponding \"idiomatic\" keys (aliases) using the given
   `parameter-aliases` registry."
  [parameter-aliases schema]
  (if parameter-aliases
    (schema-tools/prewalk-with-path
      (fn [path x]
        (if (map? x)
          (let [kmap (reduce-kv (fn [kmap k v]
                                  (assoc kmap v k (s/optional-key v) (s/optional-key k)))
                                {}
                                (get parameter-aliases (idiomatic-path path)))]
            (rename-keys x kmap))
          x))
      []
      schema)
    schema))
