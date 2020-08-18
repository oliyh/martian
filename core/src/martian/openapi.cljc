(ns martian.openapi
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [schema.core :as s]
            #?(:cljs [cljs.reader :refer [read-string]])))

(defn openapi-schema? [json]
  (some #(get json %) [:openapi "openapi"]))

(defn base-url [json]
  (get-in json [:servers 0 :url] ""))

(defn- lookup-ref [components reference]
  (if (string/starts-with? reference "#/components/")
    (or (get-in components (drop 2 (map keyword (string/split reference #"/"))))
        (throw (ex-info "Cannot find reference"
                        {:reference reference})))
    (throw (ex-info "References start with something other than #/components/ aren't supported yet. :("
                    {:reference reference}))))

(def ^:private URI
  #?(:clj java.net.URI
     :cljs goog.Uri))

(defn- openapi->schema
  ([schema components] (openapi->schema schema components #{}))
  ([schema components seen-set]
   (if-let [reference (:$ref schema)]
     (if (contains? seen-set reference)
       s/Any ; If we've already seen this, then we're in a loop. Rather than
             ; trying to solve for the fixpoint, just return Any.
       (recur (lookup-ref components reference)
              components
              (conj seen-set reference)))
     (let [wrap (if (:nullable schema) s/maybe identity)]
       (wrap
        (condp = (:type schema)
          "string"  (if-let [enum (:enum schema)]
                      (apply s/enum enum)
                      (condp = (:format schema)
                        "uuid" s/Uuid
                        "uri" URI
                        s/Str))
          "integer" s/Int
          "number"  s/Num
          "boolean" s/Bool
          "array"   [(openapi->schema (:items schema) components seen-set)]
          "object"  (let [required? (set (:required schema))]
                      (into {}
                            (map (fn [[k v]]
                                   {(if (required? (name k))
                                      (keyword k)
                                      (s/optional-key (keyword k)))
                                    (openapi->schema v components seen-set)}))
                            (:properties schema)))
          (throw (ex-info "Cannot convert OpenAPI type to schema" {:definition schema}))))))))

(defn- get-matching-schema [object content-types]
  (if-let [content-type (first (filter #(get-in object [:content (keyword %) :schema]) content-types))]
    [(get-in object [:content (keyword content-type) :schema])
     content-type]
    (when (seq (:content object))
      #?(:clj (println "No matching content-type available" {:supported-content-types content-types
                                                             :available-content-types (map name (keys (:content object)))})
         :cljs (js/console.warn "No matching content-type available" {:supported-content-types content-types
                                                                      :available-content-types (keys (get object "content"))})))))

(defn- process-body [body components content-types]
  (when-let [[json-schema content-type] (get-matching-schema body content-types)]
    (let [required (:required body)]
      {:schema       {(if required :body (s/optional-key :body))
                      (openapi->schema json-schema components)}
       :content-type content-type})))

(defn- process-parameters [parameters components]
  (into {}
        (map (fn [param]
               {(if (:required param)
                  (keyword (:name param))
                  (s/optional-key (keyword (:name param))))
                (openapi->schema (:schema param) components)}))
        parameters))

(defn- process-responses [responses components content-types]
  (for [[status-code value] responses
        :let                [status-code (name status-code)
                             [json-schema content-type] (get-matching-schema value content-types)]]
    {:status       (if (= status-code "default")
                     s/Any
                     (s/eq (if (number? status-code) status-code (read-string (name status-code)))))
     :body         (and json-schema (openapi->schema json-schema components))
     :content-type content-type}))

(defn- sanitise [x]
  (if (string? x)
    x
    ;; consistent across clj and cljs
    (-> (str x)
        (string/replace-first ":" ""))))

(defn tokenise-path [url-pattern]
  (let [url-pattern (sanitise url-pattern)
        parts (map first (re-seq #"([^{}]+|\{.+?\})" url-pattern))]
    (map #(if-let [param-name (second (re-matches #"^\{(.*)\}" %))]
            (keyword param-name)
            %) parts)))

(defn openapi->handlers [openapi-json content-types]
  (let [openapi-spec (keywordize-keys openapi-json)
        components (:components openapi-spec)]
    (for [[url methods] (:paths openapi-spec)
          [method definition] methods
          ;; We only care about things which have a defined operationId, and
          ;; which aren't the associated OPTIONS call.
          :when (and (:operationId definition)
                     (not= :options method))
          :let [parameters (group-by :in (:parameters definition))
                body       (process-body (:requestBody definition) components (:encodes content-types))
                responses  (process-responses (:responses definition) components (:decodes content-types))]]
      {:path-parts         (vec (tokenise-path url))
       :method             method
       :path-schema        (process-parameters (:path parameters) components)
       :query-schema       (process-parameters (:query parameters) components)
       :body-schema        (:schema body)
       :form-schema        (process-parameters (:form parameters) components)
       :headers-schema     (process-parameters (:header parameters) components)
       :response-schemas   (vec (keep #(dissoc % :content-type) responses))
       :produces           (vec (keep :content-type responses))
       :consumes           [(:content-type body)]
       :summary            (:summary definition)
       :description        (:description definition)
       :openapi-definition definition
       :route-name         (->kebab-case-keyword (:operationId definition))})))
