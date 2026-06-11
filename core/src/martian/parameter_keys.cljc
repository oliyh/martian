(ns martian.parameter-keys
  "Backend-agnostic helpers for idiomatic parameter keys and for renaming the
   keys of request data back and forth.

   This namespace deliberately carries no schema-library dependency, so that any
   schema backend (Plumatic Schema, Malli, ...) can build on it without pulling
   in another backend's machinery."
  (:require [camel-snake-kebab.core :refer [->kebab-case]]
            [clojure.set :refer [rename-keys]]))

(defn renamable-key?
  "Only unqualified keyword and string keys can be idiomatized/renamed. Qualified
   keywords and symbols are left untouched."
  [k]
  ;; NB: See the `camel-snake-kebab.internals.alter-name` ns.
  (or (and (keyword? k) (not (namespace k)))
      (string? k)))

(defn ->idiomatic
  "Returns the idiomatic (kebab-case) form of the plain key `k`, or `nil` when
   `k` is not a renamable key (see `renamable-key?`)."
  [k]
  (when (renamable-key? k)
    (->kebab-case k)))

(defn idiomatic-path
  "Converts a `path` of original keys into its idiomatic form, dropping any keys
   that cannot be idiomatized."
  [path]
  (vec (keep ->idiomatic path)))

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

(defn unalias-data
  "Given a (possibly, deeply nested) `data` structure, returns it with all its
   keys renamed from \"idiomatic\" (aliases) using the given parameter aliases
   `registry`."
  [registry data]
  (if registry
    (prewalk-with-path
      (fn [path x]
        (if (map? x)
          (rename-keys x (get registry (idiomatic-path path)))
          x))
      data)
    data))
