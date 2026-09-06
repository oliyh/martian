(ns martian.parameter-aliases
  (:require [martian.backends.plumatic :as plumatic]
            [martian.parameter-keys :as parameter-keys]
            [martian.schema-backend :as sb]))

(defn- aliases-at
  "Internal helper. Given a `backend`, a `schema`, a path-local `cache` (atom),
   an `interner` (atom), and a `path` (vector or seq), returns the alias map
   for that path.

   - Delegates the actual computation to the backend's `aliases-at`, which
     understands the backend's schema representation.
   - Caches results per-path in the provided `cache` to avoid recomputation.
   - Interns identical alias maps across the paths via `interner`, so equal maps
     share a single canonical instance (reduces memory churn in large APIs).
   - Returns `nil` when there are no aliases at the given path."
  [backend schema cache interner path]
  (let [path' (if (vector? path) path (vec path))]
    (or (get @cache path')
        (let [m (sb/aliases-at backend schema path')
              m' (when m
                   (or (get @interner m)
                       (-> interner
                           (swap! #(if (contains? % m) % (assoc % m m)))
                           (get m))))]
          (swap! cache assoc path' m')
          m'))))

#?(:bb nil

   :clj
   (deftype LazyRegistry [backend schema cache interner]
     clojure.lang.ILookup
     (valAt [_ k]
       (aliases-at backend schema cache interner k))
     (valAt [_ k not-found]
       (or (aliases-at backend schema cache interner k) not-found))
     Object
     (toString [_] (str "#LazyRegistry (cached " (count @cache) ")")))

   :cljs
   (deftype LazyRegistry [backend schema cache interner]
     cljs.core/ILookup
     (-lookup [_ k]
       (aliases-at backend schema cache interner k))
     (-lookup [_ k not-found]
       (or (aliases-at backend schema cache interner k) not-found))
     cljs.core/IPrintWithWriter
     (-pr-writer [_ writer _opts]
       (-write writer (str "#LazyRegistry (cached " (count @cache) ")")))))

(defn aliases-hash-map
  "Eagerly computes the registry as a data structure for the given `schema`.

   Produces a plain hash map with idiomatic keys (aliases) mappings per path
   in a (possibly, deeply nested) `schema` for all its unqualified keys.

   The result is then used with `alias-schema` and `unalias-data` functions."
  ([schema]
   (aliases-hash-map plumatic/backend schema))
  ([backend schema]
   ;; `key-paths` yields plain keys for every backend, so the backend-neutral
   ;; `parameter-keys` helpers apply directly — no need to unwrap Plumatic's
   ;; optional/required key wrappers here.
   (reduce (fn [acc path]
             (let [leaf (peek path)
                   idiomatic-key (some-> leaf (parameter-keys/->idiomatic))]
               (if (and idiomatic-key (not= leaf idiomatic-key))
                 (update acc (parameter-keys/idiomatic-path (pop path)) assoc idiomatic-key leaf)
                 acc)))
           {}
           (sb/key-paths backend schema))))

(defn registry
  "Builds a lookupable registry of parameter alias maps for the given `schema`.

  - On JVM/CLJS:
    Returns an instance of a lazy registry.

    Aliases are computed on demand (via the backend's `aliases-at`), so
    materializing massive alias maps upfront is avoided. Per-path results are
    memoized within the registry. Identical alias maps are shared to cut
    memory usage.

    A returned value implements `ILookup` and is indexed by \"idiomatic paths\".
    Looking up a path gives an alias map for that level, mapping idiomatic keys
    (kebab-case, unqualified) to their original schema keys.

  - On Babashka:
    Returns a plain hash map registry that is computed eagerly via the
    backend's `key-paths`."
  ([schema]
   (registry plumatic/backend schema))
  ([backend schema]
   (when schema
     #?(:bb      (aliases-hash-map backend schema)
        :default (new LazyRegistry backend schema (atom {}) (atom {}))))))

;; TODO: An alias for backward compatibility. Remove later on.
(def parameter-aliases registry)

(defn unalias-data
  "Given a (possibly, deeply nested) `data` structure, returns it with all its
   keys renamed from \"idiomatic\" (aliases) using the given parameter aliases
   `registry`."
  [registry data]
  (parameter-keys/unalias-data registry data))

(defn alias-schema
  "Given a (possibly, deeply nested) `schema`, renames all keys (in it and its
   subschemas) into corresponding \"idiomatic\" keys (aliases) using the given
   parameter aliases `registry`."
  ([registry schema]
   (alias-schema plumatic/backend registry schema))
  ([backend registry schema]
   (if registry
     (sb/alias-schema backend registry schema)
     schema)))
