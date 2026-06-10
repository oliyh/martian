(ns martian.backends.malli
  "Malli backend for Martian. Schemas are represented as Malli vector forms,
   so handler schemas remain plain, readable data.

   Select it by passing `:schema-backend martian.backends.malli/backend` in
   the opts of any bootstrap function."
  (:require #?(:cljs [goog.Uri])
            [clojure.string :as string]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [malli.util :as mu]
            [martian.schema-backend :as sb]
            [martian.schema-tools :as mst :refer [unalias-data]]))

(def binary-schema
  "Schema for binary data — deferred to multipart coercion on JVM, js/File in ClojureScript."
  #?(:clj  :any
     :cljs [:fn {:error/message "should be a js/File"} #(instance? js/File %)]))

(def uri-schema
  "Schema for URI values."
  #?(:clj  [:fn {:error/message "should be a URI"} #(instance? java.net.URI %)]
     :cljs [:fn {:error/message "should be a goog.Uri"} #(instance? goog.Uri %)]))

;; ---------------------------------------------------------------------------
;; Form inspection helpers
;; ---------------------------------------------------------------------------

(def ^:private transparent-containers
  "Container types whose child schemas describe values at the same data path
   as the container itself."
  #{:maybe :or :and :sequential :vector :set :tuple})

(defn- form-properties [form]
  (when (map? (second form)) (second form)))

(defn- form-head [form]
  (subvec form 0 (if (form-properties form) 2 1)))

(defn- form-children [form]
  (if (form-properties form) (nnext form) (next form)))

(defn- map-form? [form]
  (and (vector? form) (= :map (first form))))

(defn- container-form? [form]
  (and (vector? form) (contains? transparent-containers (first form))))

(defn- map-form-entries
  "Returns [key value-schema] pairs for the concrete entries of a :map form,
   ignoring the ::m/default catch-all entry of open maps."
  [form]
  (for [entry (form-children form)
        :when (and (vector? entry) (not= ::m/default (first entry)))]
    [(first entry) (peek entry)]))

;; ---------------------------------------------------------------------------
;; Parameter aliases — walking Malli forms
;; ---------------------------------------------------------------------------

(defn- form-key-paths [form path include-self?]
  (cond
    (map-form? form)
    (concat (when include-self? [path])
            (mapcat (fn [[k child]]
                      (let [path' (conj path k)]
                        (cons path' (form-key-paths child path' false))))
                    (map-form-entries form)))

    (container-form? form)
    (concat (when include-self? [path])
            (mapcat #(form-key-paths % path false) (form-children form)))

    :else
    (when include-self? [path])))

(defn- entry-aliases
  "Returns a map of idiomatic keys to original keys for the immediate entries
   of the given :map form, or nil when there are none."
  [form]
  (not-empty
   (into {}
         (keep (fn [[k _]]
                 (when-some [idiomatic-key (mst/->idiomatic k)]
                   (when (not= idiomatic-key k)
                     [idiomatic-key k]))))
         (map-form-entries form))))

(defn- form-aliases-at [form idiomatic-path]
  (cond
    (map-form? form)
    (if (empty? idiomatic-path)
      (entry-aliases form)
      (let [seg (first idiomatic-path)]
        (some (fn [[k child]]
                (when (= seg (mst/->idiomatic k))
                  (form-aliases-at child (rest idiomatic-path))))
              (map-form-entries form))))

    (container-form? form)
    (not-empty (apply merge (keep #(form-aliases-at % idiomatic-path) (form-children form))))

    :else nil))

(defn- alias-form
  "Renames the :map entry keys of the given form (and its subforms) to their
   idiomatic counterparts using the given aliases registry."
  [aliases path form]
  (cond
    (map-form? form)
    (let [kmap (into {}
                     (map (fn [[idiomatic-key original-key]] [original-key idiomatic-key]))
                     (get aliases (mst/idiomatic-path path)))]
      (into (form-head form)
            (map (fn [entry]
                   (if (and (vector? entry) (not= ::m/default (first entry)))
                     (let [k (first entry)]
                       (-> entry
                           (assoc 0 (get kmap k k))
                           (assoc (dec (count entry)) (alias-form aliases (conj path k) (peek entry)))))
                     entry)))
            (form-children form)))

    (container-form? form)
    (into (form-head form)
          (map #(alias-form aliases path %))
          (form-children form))

    :else form))

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
    (when (map-form? schema)
      (not-empty (mapv first (map-form-entries schema)))))

  (merge-map-schemas [_ schemas]
    (or (some-> (reduce mu/merge nil schemas) (m/form))
        [:map]))

  (eq-schema-value [_ schema]
    (when (and (vector? schema) (= := (first schema)))
      (peek schema)))

  (key-paths [_ schema]
    (vec (distinct (form-key-paths schema [] true))))

  (aliases-at [_ schema idiomatic-path]
    (form-aliases-at schema idiomatic-path))

  (alias-schema [_ aliases schema]
    (alias-form aliases [] schema))

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
