(ns martian.parameter-aliases
  (:require [camel-snake-kebab.core :refer [->kebab-case]]
            [clojure.set :refer [rename-keys]]
            [martian.schema-tools :refer [explicit-key key-seqs prewalk-with-path]]
            [schema.core :as s]))

(defn can-be-renamed? [k]
  ;; NB: See `camel-snake-kebab.internals.alter-name` ns.
  (or (and (keyword? k) (not (namespace k))) (string? k)))

(defn ->idiomatic [k]
  (when-some [k' (when k (explicit-key k))]
    (when (can-be-renamed? k')
      (->kebab-case k'))))

(defn- idiomatic-path [path]
  (vec (keep ->idiomatic path)))

(defn parameter-aliases
  "Produces a data structure with idiomatic keys (aliases) mappings per path
   in a (possibly, deeply nested) `schema` for all its unqualified keys.

   The result is then used with `alias-schema` and `unalias-data` functions."
  [schema]
  (reduce (fn [acc path]
            (if-let [idiomatic-key (some-> path last ->idiomatic)]
              (if-not (= (last path) idiomatic-key)
                (update acc (idiomatic-path (drop-last path)) merge {idiomatic-key (last path)})
                acc)
              acc))
          {}
          (key-seqs schema)))

(defn unalias-data
  "Given a (possibly, deeply nested) data `x`, returns the data with all keys
   renamed as described by the `parameter-aliases`."
  [parameter-aliases x]
  (if parameter-aliases
    (prewalk-with-path (fn [path x]
                         (if (map? x)
                           (rename-keys x (get parameter-aliases (idiomatic-path path)))
                           x))
                       []
                       x)
    x))

(defn alias-schema
  "Given a (possibly, deeply nested) `schema`, renames all keys (in it and its
   subschemas) into corresponding idiomatic keys (aliases) as described by the
   `parameter-aliases`."
  [parameter-aliases schema]
  (if parameter-aliases
    (prewalk-with-path (fn [path x]
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
