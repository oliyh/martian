(ns martian.backends.malli
  "Malli backend for Martian. Schemas are represented as Malli schemas (most
   often vector forms such as [:map ...], but also plain keywords like :string),
   so handler schemas remain plain, readable data.

   Select it by passing `:schema-backend martian.backends.malli/backend` in
   the opts of any bootstrap function."
  (:require #?(:cljs [goog.Uri])
            [clojure.string :as string]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [malli.util :as mu]
            [martian.parameter-keys :as pk :refer [unalias-data]]
            [martian.schema-backend :as sb]))

(def binary-schema
  "Schema for binary data — deferred to multipart coercion on JVM, js/File in ClojureScript."
  #?(:clj  :any
     :cljs [:fn {:error/message "should be a js/File"} #(instance? js/File %)]))

(def uri-schema
  "Schema for URI values."
  #?(:clj  [:fn {:error/message "should be a URI"} #(instance? java.net.URI %)]
     :cljs [:fn {:error/message "should be a goog.Uri"} #(instance? goog.Uri %)]))

;; ---------------------------------------------------------------------------
;; Schema introspection helpers
;; ---------------------------------------------------------------------------
;;
;; These lean on Malli's own introspection API (`m/schema`, `m/type`,
;; `m/children`, `m/walk`, `mu/subschemas`) rather than picking Malli vector
;; forms apart by hand, so they keep working for every schema type uniformly.

(defn- map-schema? [schema]
  (= :map (m/type schema)))

(defn- map-entries
  "Returns [key child-schema] pairs for the concrete entries of a :map schema,
   ignoring the ::m/default catch-all entry of open maps."
  [schema]
  (for [[k _properties child] (m/children schema)
        :when (not= ::m/default k)]
    [k child]))

(defn- plain-key-path
  "Drops Malli's sequence-element markers (and tuple indices) from an `:in`
   path, leaving a path of plain map keys."
  [in]
  (into [] (remove #(or (= ::m/in %) (integer? %))) in))

(defn- entry-aliases
  "Returns a map of idiomatic keys to original keys for the immediate entries
   of the given :map schema, or nil when there are none."
  [schema]
  (not-empty
   (into {}
         (keep (fn [[k _child]]
                 (when-some [idiomatic-key (pk/->idiomatic k)]
                   (when (not= idiomatic-key k)
                     [idiomatic-key k]))))
         (map-entries schema))))

;; ---------------------------------------------------------------------------
;; Coercion
;; ---------------------------------------------------------------------------

(def ^:private collection-format->separator
  {"csv"   ","
   "ssv"   " "
   "tsv"   "\t"
   "pipes" "|"})

(defn- collection-format-decoder [schema _]
  (when-some [separator (collection-format->separator
                         (::collection-format (m/properties schema)))]
    (fn [x] (if (sequential? x) (string/join separator x) x))))

(defn- keyword->string [x]
  (if (keyword? x) (name x) x))

(defn- string-values-decoder
  "Turns keywords into strings for schemas whose values are all strings."
  [schema _]
  (when (every? string? (m/children schema))
    keyword->string))

(def default-transformer
  "The transformer used for parameters coercion by default. Replicates the
   behaviour of the Plumatic backend's default-coercion-matcher: strips keys
   that are not in the schema, coerces strings to the schema's leaf types and
   keywords to strings, and joins arrays with a collection format."
  (mt/transformer
   (mt/strip-extra-keys-transformer)
   (mt/string-transformer)
   {:decoders {:string keyword->string
               :enum {:compile string-values-decoder}
               :=    {:compile string-values-decoder}}}
   {:decoders {:string {:compile collection-format-decoder}}}))

(defn- build-transformer
  [{:keys [transformer use-defaults?]}]
  (let [transformer (or transformer default-transformer)]
    (if use-defaults?
      (mt/transformer (mt/default-value-transformer) transformer)
      transformer)))

;; ---------------------------------------------------------------------------
;; The backend
;; ---------------------------------------------------------------------------

(defrecord MalliBackend []
  sb/SchemaBackend

  (leaf-schema [_ {:keys [type enum format]}]
    (cond
      enum                 (into [:enum] enum)
      (= "string" type)    (case format
                             "binary"        [:or :string binary-schema]
                             "date-time"     [:or :string inst?]
                             "int-or-string" [:or :string :int]
                             "uri"           [:or :string uri-schema]
                             "uuid"          [:or :string :uuid]
                             :string)
      (= "integer" type)   :int
      (= "number" type)    number?
      (= "boolean" type)   :boolean
      (= "date-time" type) inst?
      :else                :any))

  (any-schema [_] :any)

  (int-schema [_] :int)

  (map-schema [_ entries {:keys [open?]}]
    ;; Malli maps are open for validation already, but the default transformer
    ;; strips undeclared keys on coercion. An open map (OpenAPI's
    ;; `additionalProperties`) adds a ::m/default catch-all entry so those keys
    ;; survive coercion instead of being stripped.
    (-> [:map]
        (into (map (fn [{:keys [key required? schema]}]
                     (if required?
                       [key schema]
                       [key {:optional true} schema])))
              entries)
        (cond-> open? (conj [::m/default :any]))))

  (seq-schema [_ item-schema] [:sequential item-schema])

  (maybe-schema [_ s] [:maybe s])

  (eq-schema [_ value] [:= value])

  (constrained-schema [_ s pred] [:and s [:fn pred]])

  (with-default-value [_ {:keys [default]} schema]
    (if (some? default)
      (let [default (if (and (= :int schema) (= "inf" default))
                      #?(:clj  Long/MAX_VALUE
                         :cljs (.-MAX_SAFE_INTEGER js/Number))
                      default)]
        (m/form (mu/update-properties (m/schema schema) assoc :default default)))
      schema))

  (wrap-collection-format-schema [_ array-schema collection-format]
    (if (and collection-format
             (not= "multi" collection-format)
             (= [:sequential :string] array-schema))
      [:string {::collection-format collection-format}]
      array-schema))

  (map-schema-keys [_ schema]
    (when (some? schema)
      (let [s (m/schema schema)]
        (when (map-schema? s)
          (not-empty (mapv first (map-entries s)))))))

  (merge-map-schemas [_ schemas]
    (or (some-> (reduce mu/merge nil schemas) (m/form))
        [:map]))

  (eq-schema-value [_ schema]
    (when (and (vector? schema) (= := (first schema)))
      (peek schema)))

  (key-paths [_ schema]
    (into [] (comp (map (comp plain-key-path :in)) (distinct))
          (mu/subschemas (m/schema schema))))

  (aliases-at [_ schema idiomatic-path]
    (let [idiomatic-path (vec idiomatic-path)]
      (not-empty
       (reduce (fn [acc {:keys [in schema]}]
                 (if (and (map-schema? schema)
                          (= idiomatic-path (pk/idiomatic-path in)))
                   (merge acc (entry-aliases schema))
                   acc))
               {}
               (mu/subschemas (m/schema schema))))))

  (alias-schema [_ aliases schema]
    (m/walk (m/schema schema)
            (fn [s path children _opts]
              (if (map-schema? s)
                (let [kmap (reduce-kv (fn [kmap idiomatic-key original-key]
                                        (assoc kmap original-key idiomatic-key))
                                      {}
                                      (get aliases (pk/idiomatic-path path)))]
                  (into [:map]
                        (map (fn [[k properties child]]
                               (let [k (get kmap k k)]
                                 (if properties [k properties child] [k child]))))
                        children))
                (m/form (m/-set-children s children))))))

  (coerce-data [_ schema data {:keys [parameter-aliases] :as opts}]
    (when schema
      (m/coerce schema
                (unalias-data parameter-aliases data)
                (build-transformer opts))))

  (check-schema [_ schema value]
    (some-> (m/explain schema value) (me/humanize)))

  (validate-schema [_ schema value]
    (if-some [explanation (m/explain schema value)]
      (throw (ex-info (str "Value does not match schema: " (pr-str (me/humanize explanation)))
                      {:explanation explanation}))
      value)))

(def backend
  "The singleton Malli backend instance."
  (->MalliBackend))
