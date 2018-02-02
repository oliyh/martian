(ns martian.schema
  (:require [schema.core :as s]
            [schema.coerce :as sc]
            [clojure.walk :refer [postwalk-replace]]))

(defn- keyword->string [s]
  (if (keyword? s) (name s) s))

(defn- string-enum-matcher [schema]
  (when (or (and (instance? schema.core.EnumSchema schema)
                 (every? string? (.-vs ^schema.core.EnumSchema schema)))
            (and (instance? schema.core.EqSchema schema)
                 (string? (.-v ^schema.core.EqSchema schema))))
    keyword->string))

(defn coercion-matchers [schema]
  (or (sc/string-coercion-matcher schema)
      (string-enum-matcher schema)))

(defn- from-maybe [s]
  (if (instance? schema.core.Maybe s)
    (:schema s)
    s))

(defn- unalias-parameters [data parameter-aliases]
  (postwalk-replace parameter-aliases data))

(defn coerce-data
  "Extracts the data referred to by the schema's keys and coerces it"
  [schema data & [parameter-aliases]]
  (when-let [s (from-maybe schema)]
    (cond
      (instance? schema.core.AnythingSchema s)
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

(defn parameter-keys [schemas]
  (mapcat
   (fn [schema]
     (when-let [s (from-maybe schema)]
       (cond
         (and (map? s) (not (record? s)))
         (concat (map s/explicit-schema-key (keys s)) (parameter-keys (vals s)))

         (coll? s)
         (parameter-keys s)

         :else
         nil)))
   schemas))

(declare make-schema)

(defn schemas-for-parameters
  "Given a collection of swagger parameters returns a schema map"
  [definitions parameters]
  (->> parameters
       (map (fn [{:keys [name required] :as param}]
              {(cond-> (keyword name)
                 (not required)
                 s/optional-key)
               (make-schema definitions param)}))
       (into {})))

(defn- resolve-ref [definitions ref]
  (some->> ref
           (re-find #"#/definitions/(.*)")
           second
           keyword
           definitions))

(defn- schema-type [definitions {:keys [type enum format $ref] :as param}]
  (cond
    enum (apply s/enum enum)
    (= "string" type) (if (= "uuid" format)
                        (s/cond-pre s/Str s/Uuid)
                        s/Str)
    (= "integer" type) s/Int
    (= "boolean" type) s/Bool
    (or (= "object" type) $ref) (make-schema definitions param)

    :else
    s/Any))

(defn make-schema
  "Takes a swagger parameter and returns a schema"
  [definitions {:keys [name required type enum schema properties $ref items] :as param}]

  (cond
    $ref
    (make-schema definitions (-> (dissoc param :$ref)
                                 (merge (resolve-ref definitions $ref))))

    (:$ref schema)
    (make-schema definitions (-> (dissoc param :schema)
                                 (merge (resolve-ref definitions (:$ref schema)))))

    :else
    (cond-> (cond
              (= "array" type)
              [(schema-type definitions (assoc items :required true))]

              (= "array" (:type schema))
              [(schema-type definitions (assoc (:items schema) :required true))]

              (= "object" type)
              (schemas-for-parameters definitions (map (fn [[name p]] (assoc p :name name)) properties))

              :else
              (schema-type definitions param))
      (and (not required) (not= "array" type) (not= "array" (:type schema)))
      s/maybe)))
