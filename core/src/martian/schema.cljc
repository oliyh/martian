(ns martian.schema
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [schema.core :as s]
            [schema.coerce :as sc]))

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

(defn coerce-data
  "Extracts the data referred to by the schema's keys and coerces it"
  [schema data]
  (some->> (keys schema)
           (map s/explicit-schema-key)
           (select-keys data)
           ((sc/coercer! schema coercion-matchers))))

(declare make-schema)

(defn schemas-for-parameters
  "Given a collection of swagger parameters returns a schema map"
  [definitions parameters]
  (->> (for [{:keys [name required] :as parameter} parameters
             :let [name (keyword name)
                   schema (make-schema definitions parameter)]]
         [(if required name (s/optional-key name))
          (if required schema (s/maybe schema))])
       (into {})))

(defn- resolve-ref [definitions ref]
  (some->> ref
           (re-find #"#/definitions/(.*)")
           second
           keyword
           definitions))

(defn make-schema
  "Takes a swagger parameter and returns a schema"
  [definitions {:keys [name required type enum schema properties $ref]}]
  (cond
    enum (apply s/enum enum)
    (= "string" type) s/Str
    (= "integer" type) s/Int
    (= "boolean" type) s/Bool

    $ref
    (make-schema definitions (resolve-ref definitions $ref))

    (:$ref schema)
    {(->kebab-case-keyword name) (make-schema definitions (resolve-ref definitions (:$ref schema)))}

    (= "object" type)
    (schemas-for-parameters definitions (map (fn [[name p]]
                                               (assoc p :name name)) properties))

    (= "array" (:type schema))
    {(->kebab-case-keyword name) [(make-schema definitions (:items schema))]}

    :default s/Any))
