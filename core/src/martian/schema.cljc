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

(defn- from-maybe [s]
  (if (instance? schema.core.Maybe s)
    (:schema s)
    s))

(defn coerce-data
  "Extracts the data referred to by the schema's keys and coerces it"
  [schema data]
  (when-let [s (from-maybe schema)]
    (if (map? s)
      (some->> (keys s)
               (map s/explicit-schema-key)
               (select-keys data)
               ((sc/coercer! schema coercion-matchers)))
      ((sc/coercer! s coercion-matchers) data))))

(defn parameter-keys [schemas]
  (mapcat
   (fn [schema]
     (when-let [s (from-maybe schema)]
       (when (and (map? s) (not (record? s)))
         (concat (map s/explicit-schema-key (keys s)) (parameter-keys (vals s))))))
   schemas))

(declare make-schema)

(defn schemas-for-parameters
  "Given a collection of swagger parameters returns a schema map"
  ([definitions parameters] (schemas-for-parameters definitions parameters keyword))
  ([definitions parameters key-fn]
   (->> parameters
        (map (fn [{:keys [name required] :as param}]
               {(cond-> (key-fn name)
                  (not required)
                  s/optional-key)
                (make-schema definitions param)}))
        (into {}))))

(defn- resolve-ref [definitions ref]
  (some->> ref
           (re-find #"#/definitions/(.*)")
           second
           keyword
           definitions))

(defn- schema-type [definitions {:keys [type enum $ref] :as param}]
  (cond
    enum (apply s/enum enum)
    (= "string" type) s/Str
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
