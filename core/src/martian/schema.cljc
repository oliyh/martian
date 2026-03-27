(ns martian.schema
  (:require [clojure.string :as string]
            [martian.schema-backend :as sb]
            [martian.backends.plumatic :as plumatic]
            [martian.parameter-aliases :refer [unalias-data]]))

;; ---------------------------------------------------------------------------
;; Backward-compatible re-exports from the Plumatic backend.
;; These allow existing code that imports martian.schema to keep working.
;; ---------------------------------------------------------------------------

(def Binary
  "Schema for binary data."
  plumatic/Binary)

(def URI
  "Schema for URI values."
  plumatic/URI)

(def default-coercion-matcher
  "The default coercion matcher used by the Plumatic backend."
  plumatic/default-coercion-matcher)

(defn leaf-schema
  "Returns a Plumatic schema for the given OpenAPI property descriptor."
  [property]
  (sb/leaf-schema plumatic/backend property))

(defn wrap-default
  "Wraps schema with a default value from property's :default key."
  [property schema]
  (sb/with-default-value plumatic/backend property schema))

(defn coerce-data
  "Extracts the data referred to by the schema's keys and coerces it."
  ([schema data]
   (coerce-data schema data nil))
  ([schema data opts]
   (sb/coerce-data plumatic/backend schema data opts)))

;; ---------------------------------------------------------------------------
;; Spec-parsing utilities — no schema backend coupling.
;; These work with the raw OpenAPI/Swagger spec format.
;; ---------------------------------------------------------------------------

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
  "Returns a function that receives an object and resolves it using resolve-ref-object."
  [ref-lookup]
  (fn [obj] (resolve-ref-object obj ref-lookup)))

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

;; ---------------------------------------------------------------------------
;; Schema construction — walking OpenAPI/Swagger specs via the backend.
;; The backend parameter defaults to plumatic/backend for backward compat.
;; These functions are mutually recursive; declare them upfront.
;; ---------------------------------------------------------------------------

(declare make-schema schemas-for-parameters)

(defn- schema-type [ref-lookup {:keys [type $ref] :as param} backend]
  (let [schema (if (or (= "object" type) $ref)
                 (make-schema ref-lookup param backend)
                 (sb/leaf-schema backend param))]
    (sb/with-default-value backend param schema)))

(defn- make-object-schema [ref-lookup {:keys [additionalProperties] :as schema} backend]
  ;; It's possible for an 'object' to omit properties and
  ;; additionalProperties. If this is the case - anything is allowed.
  (if (or (contains? schema :properties)
          (contains? schema :additionalProperties))
    (cond-> (schemas-for-parameters ref-lookup (denormalise-object-properties schema) backend)
      additionalProperties (assoc (sb/any-schema backend) (sb/any-schema backend)))
    {(sb/any-schema backend) (sb/any-schema backend)}))

(defn schemas-for-parameters
  "Given a collection of swagger parameters returns a schema map."
  ([ref-lookup parameters]
   (schemas-for-parameters ref-lookup parameters plumatic/backend))
  ([ref-lookup parameters backend]
   (->> parameters
        (map (fn [{:keys [name required required?] :as param}]
               (let [k (keyword name)
                     schema-k (if (not (or required?
                                           (and (boolean? required) required)
                                           (and (string? required) (= "true" required))))
                                (sb/optional-key backend k)
                                k)]
                 {schema-k (make-schema ref-lookup param backend)})))
        (into {}))))

(defn make-schema
  "Takes a swagger parameter and returns a schema via the given backend.
   Defaults to the Plumatic backend for backward compatibility."
  ([ref-lookup param]
   (make-schema ref-lookup param plumatic/backend))
  ([ref-lookup {:keys [required required? type schema $ref items] :as param} backend]
   (if (let [ref (or $ref (:$ref schema))]
         (and ref (contains? *visited-refs* ref)))
     (sb/any-schema backend) ;; avoid potential recursive loops

     (cond
       $ref
       (binding [*visited-refs* (conj *visited-refs* $ref)]
         (make-schema ref-lookup (-> (dissoc param :$ref)
                                     (merge (resolve-ref-object param ref-lookup)))
                      backend))

       (:$ref schema)
       (binding [*visited-refs* (conj *visited-refs* (:$ref schema))]
         (make-schema ref-lookup (-> (dissoc param :schema)
                                     (merge (resolve-ref-object schema ref-lookup)))
                      backend))

       :else
       (let [base-schema (cond
                           (= "array" type)
                           [(schema-type ref-lookup (assoc items :required true) backend)]

                           (= "array" (:type schema))
                           [(schema-type ref-lookup (assoc (:items schema) :required true) backend)]

                           (= "object" type)
                           (make-object-schema ref-lookup param backend)

                           (= "object" (:type schema))
                           (make-object-schema ref-lookup schema backend)

                           :else
                           (schema-type ref-lookup param backend))
             maybe-wrapped (if (and (not required)
                                    (not required?)
                                    (not= "array" type)
                                    (not= "array" (:type schema)))
                             (sb/maybe-schema backend base-schema)
                             base-schema)]
         (if (and (= "array" type) (:collectionFormat param))
           (sb/wrap-collection-format-schema backend maybe-wrapped (:collectionFormat param))
           maybe-wrapped))))))
