(ns martian.schema
  (:require #?(:clj [schema.core :as s]
               :cljs [schema.core :as s :refer [AnythingSchema Maybe EnumSchema EqSchema]])
            #?(:cljs [goog.Uri])
            [schema.coerce :as sc]
            [clojure.walk :refer [postwalk-replace]])
  #?(:clj (:import [schema.core AnythingSchema Maybe EnumSchema EqSchema])))

(defn- keyword->string [s]
  (if (keyword? s) (name s) s))

(defn- string-enum-matcher [schema]
  (when (or (and (instance? EnumSchema schema)
                 (every? string? (.-vs ^EnumSchema schema)))
            (and (instance? EqSchema schema)
                 (string? (.-v ^EqSchema schema))))
    keyword->string))

(defn coercion-matchers [schema]
  (or (sc/string-coercion-matcher schema)
      (string-enum-matcher schema)))

(defn- from-maybe [s]
  (if (instance? Maybe s)
    (:schema s)
    s))

(defn- unalias-parameters [data parameter-aliases]
  (postwalk-replace parameter-aliases data))

(defn coerce-data
  "Extracts the data referred to by the schema's keys and coerces it"
  [schema data & [parameter-aliases]]
  (when-let [s (from-maybe schema)]
    (cond
      (instance? AnythingSchema s)
      ((sc/coercer! schema coercion-matchers) data)

      (map? s)
      (if (every? s/specific-key? (keys s))
        (some->> (keys s)
                 (map s/explicit-schema-key)
                 (select-keys (unalias-parameters data parameter-aliases))
                 ((sc/coercer! schema coercion-matchers)))
        (some->> (unalias-parameters data parameter-aliases)
                 ((sc/coercer! schema coercion-matchers))))

      (coll? s) ;; primitives, arrays, arrays of maps
      ((sc/coercer! schema coercion-matchers)
       (map #(if (map? %)
               (unalias-parameters % parameter-aliases)
               %)
            data))

      :else
      ((sc/coercer! schema coercion-matchers) data))))

(defn- extract-keys-from-map-schema [schema]
  (->> (keys schema)
       (filter s/specific-key?)
       (map s/explicit-schema-key)))

(defn parameter-keys [schemas]
  (mapcat
   (fn [schema]
     (when-let [s (from-maybe schema)]
       (cond
         (and (map? s) (not (record? s)))
         (concat (extract-keys-from-map-schema s) (parameter-keys (vals s)))

         (coll? s)
         (parameter-keys s)

         :else
         nil)))
   schemas))

(declare make-schema)

(defn schemas-for-parameters
  "Given a collection of swagger parameters returns a schema map"
  [ref-lookup parameters]
  (->> parameters
       (map (fn [{:keys [name required] :as param}]
              {(cond-> (keyword name)
                 (not required)
                 s/optional-key)
               (make-schema ref-lookup param)}))
       (into {})))

(defn- resolve-ref [ref-lookup ref]
  (let [[_ category k] (re-find #"#/(definitions|parameters)/(.*)" ref)]
    (get-in ref-lookup [(keyword category) (keyword k)])))

(def URI
  #?(:clj java.net.URI
     :cljs goog.Uri))

(defn- schema-type [ref-lookup {:keys [type enum format $ref] :as param}]
  (cond
    enum (apply s/enum enum)
    (= "string" type) (case format
                        "uuid" (s/cond-pre s/Str s/Uuid)
                        "uri" (s/cond-pre s/Str URI)
                        s/Str)
    (= "integer" type) s/Int
    (= "boolean" type) s/Bool
    (or (= "object" type) $ref) (make-schema ref-lookup param)

    :else
    s/Any))

(def ^:dynamic *visited-refs* #{})

(defn make-schema
  "Takes a swagger parameter and returns a schema"
  [ref-lookup {:keys [name required type enum schema properties $ref items additionalProperties] :as param}]

  (if (let [ref (or $ref (:$ref schema))]
        (and ref (contains? *visited-refs* ref)))
    s/Any ;; avoid potential recursive loops

    (cond
      $ref
      (binding [*visited-refs* (conj *visited-refs* $ref)]
        (make-schema ref-lookup (-> (dissoc param :$ref)
                                    (merge (resolve-ref ref-lookup $ref)))))

      (:$ref schema)
      (binding [*visited-refs* (conj *visited-refs* (:$ref schema))]
        (make-schema ref-lookup (-> (dissoc param :schema)
                                    (merge (resolve-ref ref-lookup (:$ref schema))))))

      :else
      (cond-> (cond
                (= "array" type)
                [(schema-type ref-lookup (assoc items :required true))]

                (= "array" (:type schema))
                [(schema-type ref-lookup (assoc (:items schema) :required true))]

                (= "object" type)
                (cond-> (schemas-for-parameters ref-lookup (map (fn [[name p]] (assoc p :name name)) properties))
                  additionalProperties (assoc s/Any s/Any))

                :else
                (schema-type ref-lookup param))
        (and (not required) (not= "array" type) (not= "array" (:type schema)))
        s/maybe))))
