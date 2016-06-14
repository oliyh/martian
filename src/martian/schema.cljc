(ns martian.schema
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [schema.core :as s]
            [schema.coerce :as sc]))

(defn coerce-data
  "Extracts the data referred to by the schema's keys and coerces it"
  [schema data]
  (some->> (keys schema)
           (map s/explicit-schema-key)
           (select-keys data)
           ((sc/coercer! schema sc/string-coercion-matcher))))

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
  [definitions {:keys [name required type enum schema properties]}]
  (cond
    enum (apply s/enum enum)
    (= "string" type) s/Str
    (= "integer" type) s/Int

    (:$ref schema)
    (make-schema definitions (resolve-ref definitions (:$ref schema)))

    (= "object" type)
    (schemas-for-parameters definitions (map (fn [[name p]]
                                               (assoc p :name name)) properties))

    :default s/Any))
