(ns martian.backends.plumatic
  "Plumatic Schema backend for Martian. This is the default backend and provides
   full backward compatibility with existing code."
  (:require #?(:clj  [schema.core :as s]
               :cljs [schema.core :as s :refer [AnythingSchema Maybe EnumSchema EqSchema]])
            #?(:cljs [goog.Uri])
            [schema.coerce :as sc]
            [schema-tools.core :as st]
            [schema-tools.coerce :as stc]
            [clojure.string :as string]
            [martian.parameter-aliases :refer [unalias-data]]
            [martian.schema-backend :as sb])
  #?(:clj (:import [schema.core AnythingSchema Maybe EnumSchema EqSchema])))

(def Binary
  "Schema for binary data — deferred to multipart coercion on JVM, js/File in ClojureScript."
  #?(:clj  s/Any
     :cljs js/File))

(def URI
  "Schema for URI values."
  #?(:clj  java.net.URI
     :cljs goog.Uri))

(def format->separator
  {"csv"   ","
   "ssv"   " "
   "tsv"   "\t"
   "pipes" "|"})

(defn- seq->string [coll-fmt]
  (when-some [separator (get format->separator coll-fmt)]
    (fn [coll]
      (string/join separator coll))))

(defn- format-coll-coercion-matcher [schema]
  (when-some [coll-fmt (:collection-format (meta schema))]
    (seq->string coll-fmt)))

(defn- keyword->string [x]
  (if (keyword? x) (name x) x))

(def +extra-string-coercions+
  {s/Str keyword->string})

(defn- string-enum-matcher [schema]
  (when (or (and (instance? EnumSchema schema)
                 (every? string? (.-vs ^EnumSchema schema)))
            (and (instance? EqSchema schema)
                 (string? (.-v ^EqSchema schema))))
    keyword->string))

(def default-coercion-matcher
  (stc/or-matcher sc/string-coercion-matcher
                  format-coll-coercion-matcher
                  +extra-string-coercions+
                  string-enum-matcher))

(defn- build-coercion-matcher
  [{:keys [coercion-matcher use-defaults?]
    :or   {coercion-matcher default-coercion-matcher}}]
  (when (nil? coercion-matcher)
    (throw (ex-info "Coercion matcher must be a unary fn of schema" {})))
  (if use-defaults?
    (stc/or-matcher stc/default-matcher coercion-matcher)
    coercion-matcher))

(defn- ->map-matcher
  "Builds a version of `stc/map-filter-matcher` that is optional and takes
   into account a custom `coercion-matcher` that may/not happen afterwards."
  [coercion-matcher]
  (fn [schema]
    (let [f (stc/map-filter-matcher schema)
          g (coercion-matcher schema)]
      (fn [x]
        (cond-> x
                (some? f) (f)
                (some? g) (g))))))

(defrecord PlumaticBackend []
  sb/SchemaBackend

  (leaf-schema [_ {:keys [type enum format]}]
    (cond
      enum                 (apply s/enum enum)
      (= "string" type)    (case format
                             "binary"        (s/cond-pre s/Str Binary)
                             "date-time"     (s/cond-pre s/Str s/Inst)
                             "int-or-string" (s/cond-pre s/Str s/Int)
                             "uri"           (s/cond-pre s/Str URI)
                             "uuid"          (s/cond-pre s/Str s/Uuid)
                             s/Str)
      (= "integer" type)   s/Int
      (= "number" type)    s/Num
      (= "boolean" type)   s/Bool
      (= "date-time" type) s/Inst
      :else                s/Any))

  (any-schema [_] s/Any)

  (int-schema [_] s/Int)

  (maybe-schema [_ s] (s/maybe s))

  (optional-key [_ k] (s/optional-key k))

  (unwrap-key [_ k] (s/explicit-schema-key k))

  (eq-schema [_ value] (s/eq value))

  (constrained-schema [_ s pred] (s/constrained s pred))

  (with-default-value [_ {:keys [default]} schema]
    (if (some? default)
      (let [default (if (and (= schema s/Int) (= default "inf"))
                      #?(:clj  Long/MAX_VALUE
                         :cljs js/Number.MAX_SAFE_INTEGER)
                      default)]
        (st/default schema default))
      schema))

  (wrap-collection-format-schema [_ array-schema collection-format]
    (if (and collection-format
             (not= "multi" collection-format)
             (= [s/Str] array-schema))
      (vary-meta (st/schema s/Str) assoc :collection-format collection-format)
      array-schema))

  (coerce-data [_ schema data {:keys [parameter-aliases] :as opts}]
    (let [matcher (build-coercion-matcher opts)]
      (when-let [s (if (instance? Maybe schema) (:schema schema) schema)]
        (cond
          (instance? AnythingSchema s)
          ((sc/coercer! schema matcher) data)

          (map? s)
          (let [map-matcher (->map-matcher matcher)]
            (stc/coerce (unalias-data parameter-aliases data) s map-matcher))

          (coll? s) ;; primitives, arrays, arrays of maps
          ((sc/coercer! schema matcher)
           (map #(if (map? %)
                   (unalias-data parameter-aliases %)
                   %)
                data))

          :else
          ((sc/coercer! schema matcher) data)))))

  (check-schema [_ schema value]
    (s/check schema value))

  (validate-schema [_ schema value]
    (s/validate schema value)))

(def backend
  "The singleton Plumatic Schema backend instance."
  (->PlumaticBackend))
