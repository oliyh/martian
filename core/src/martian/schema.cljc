(ns martian.schema
  (:require #?(:clj [schema.core :as s]
               :cljs [schema.core :as s :refer [AnythingSchema Maybe EnumSchema EqSchema]])
            #?(:cljs [goog.Uri])
            [schema.coerce :as sc]
            [schema-tools.core :as st]
            [schema-tools.coerce :as stc]
            [clojure.string :as string]
            [martian.parameter-aliases :refer [unalias-data]])
  #?(:clj (:import [schema.core AnythingSchema Maybe EnumSchema EqSchema OptionalKey])))

(def format->separator
  {"csv"   ","
   "ssv"   " "
   "tsv"   "\t"
   "pipes" "|"})

(defn seq->string [coll-fmt]
  (when-some [separator (get format->separator coll-fmt)]
    (fn [coll]
      (string/join separator coll))))

(defn format-coll-coercion-matcher [schema]
  (when-some [coll-fmt (:collection-format (meta schema))]
    (seq->string coll-fmt)))

(defn keyword->string [x]
  (if (keyword? x) (name x) x))

(def +extra-string-coercions+
  {s/Str keyword->string})

(defn string-enum-matcher [schema]
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

(defn build-coercion-matcher
  [{:keys [coercion-matcher use-defaults?]
    :or   {coercion-matcher default-coercion-matcher}}]
  (when (nil? coercion-matcher)
    (throw (ex-info "Coercion matcher must be a unary fn of schema" {})))
  (if use-defaults?
    (stc/or-matcher stc/default-matcher coercion-matcher)
    coercion-matcher))

(defn ->map-matcher
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

(defn- from-maybe [s]
  (if (instance? Maybe s)
    (:schema s)
    s))

(defn coerce-data
  "Extracts the data referred to by the schema's keys and coerces it"
  ([schema data]
   (coerce-data schema data nil))
  ([schema data {:keys [parameter-aliases] :as opts}]
   (let [matcher (build-coercion-matcher opts)]
     (when-let [s (from-maybe schema)]
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
         ((sc/coercer! schema matcher) data))))))

(declare make-schema)

(defn schemas-for-parameters
  "Given a collection of swagger parameters returns a schema map"
  [ref-lookup parameters]
  (->> parameters
       (map (fn [{:keys [name required required?] :as param}]
              {(cond-> (keyword name)
                 (not (or required?
                          (and (boolean? required) required)
                          (and (string? required) (= "true" required))))
                 s/optional-key)
               (make-schema ref-lookup param)}))
       (into {})))

(defn lookup-ref
  [ref ref-lookup]
  (if (string/starts-with? ref "#/")
    (or (get-in ref-lookup (drop 1 (map keyword (string/split ref #"/"))))
        (throw (ex-info "Cannot find reference"
                        {:reference ref})))
    (throw (ex-info "Non-local references are not supported yet. References should start ref with '#/'"
                    {:reference ref}))))

(defn resolve-ref-object
  "resolve-ref-object receives a map with '$ref` key and returns the referenced object.
  Throws an exception if the reference is cyclic, it doesn't exist or is not supported"
  ([ref-object ref-lookup]
   (resolve-ref-object ref-object ref-lookup nil))
  ([ref-object ref-lookup visited-list]
   (if-let [ref (:$ref ref-object)]
     (if (contains? (set visited-list) ref)
       (throw (ex-info "Cyclic reference" {:type :cyclic-reference :reference ref :visited visited-list}))
       (resolve-ref-object (lookup-ref ref ref-lookup) ref-lookup (conj (vec visited-list) ref)))
     ref-object)))

(defn resolve-ref-fn
  "returns a function that receives an object and resolves it using resolve-ref-object"
  [ref-lookup]
  (fn [obj] (resolve-ref-object obj ref-lookup)))

(def URI
  #?(:clj java.net.URI
     :cljs goog.Uri))

(defn leaf-schema [{:keys [type enum format]}]
  (cond
    enum                 (apply s/enum enum)
    (= "string" type)    (case format
                           "uuid" (s/cond-pre s/Str s/Uuid)
                           "uri" (s/cond-pre s/Str URI)
                           "date-time" (s/cond-pre s/Str s/Inst)
                           "int-or-string" (s/cond-pre s/Str s/Int)
                           s/Str)
    (= "integer" type)   s/Int
    (= "number" type)    s/Num
    (= "boolean" type)   s/Bool
    (= "date-time" type) s/Inst

    :else
    s/Any))

(defn wrap-default [{:keys [default]} schema]
  (if (some? default)
    (let [default
          ;patch for `inf` as a default value
          (if (and (= schema s/Int) (= default "inf"))
            #?(:clj Long/MAX_VALUE
               :cljs js/Number.MAX_SAFE_INTEGER)
            default)]
      (st/default schema default))
    schema))

(defn- schema-type [ref-lookup {:keys [type $ref] :as param}]
  (let [schema (if (or (= "object" type) $ref)
                 (make-schema ref-lookup param)
                 (leaf-schema param))]
    (wrap-default param schema)))

(def ^:dynamic *visited-refs* #{})

(defn- denormalise-object-properties [{:keys [required properties] :as s}]
  (map (fn [[parameter-name param]]
         (assoc param
                :name parameter-name
                :required? (or (when-not (= "object" (:type param))
                                 (:required param))
                               (and (coll? required)
                                    (contains? (set required) (name parameter-name))))))
       properties))

(defn- make-object-schema [ref-lookup {:keys [additionalProperties] :as schema}]
  ;; It's possible for an 'object' to omit properties and
  ;; additionalProperties. If this is the case - anything is allowed.
  (if (or (contains? schema :properties)
          (contains? schema :additionalProperties))
    (cond-> (schemas-for-parameters ref-lookup (denormalise-object-properties schema))
      additionalProperties (assoc s/Any s/Any))
    {s/Any s/Any}))

(defn- wrap-collection-format [array-schema collection-format]
  (if (and collection-format (not= "multi" collection-format) (= [s/Str] array-schema))
    (vary-meta (st/schema s/Str) assoc :collection-format collection-format)
    array-schema))

(defn make-schema
  "Takes a swagger parameter and returns a schema"
  [ref-lookup {:keys [required required? type schema $ref items] :as param}]
  (if (let [ref (or $ref (:$ref schema))]
        (and ref (contains? *visited-refs* ref)))
    s/Any ;; avoid potential recursive loops

    (cond
      $ref
      (binding [*visited-refs* (conj *visited-refs* $ref)]
        (make-schema ref-lookup (-> (dissoc param :$ref)
                                    (merge (resolve-ref-object param ref-lookup)))))

      (:$ref schema)
      (binding [*visited-refs* (conj *visited-refs* (:$ref schema))]
        (make-schema ref-lookup (-> (dissoc param :schema)
                                    (merge (resolve-ref-object schema ref-lookup)))))

      :else
      (cond-> (cond
                (= "array" type)
                [(schema-type ref-lookup (assoc items :required true))]

                (= "array" (:type schema))
                [(schema-type ref-lookup (assoc (:items schema) :required true))]

                (= "object" type)
                (make-object-schema ref-lookup param)

                (= "object" (:type schema))
                (make-object-schema ref-lookup schema)

                :else
                (schema-type ref-lookup param))
        (and (or (not required)
                 ;;(= "object" type) ;; todo work out something better here
                 )
             (not required?)
             (not= "array" type) (not= "array" (:type schema)))
        s/maybe

        (and (= "array" type) (:collectionFormat param))
        (wrap-collection-format (:collectionFormat param))))))
