(ns martian.openapi
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.string :as string]
            [schema.core :as s]))

(defn- tokenise-path [url-pattern]
  (for [part (re-seq #"[^{]+|\{[^}]+?\}" url-pattern)]
    (if-let [param (re-matches #"^\{(.*)\}$" part)]
      (keyword (second param))
      part)))

(defn- lookup-ref [components reference]
  (if (string/starts-with? reference "#/components/")
    (or (get-in components (drop 2 (string/split reference #"/")))
        (throw (ex-info "Cannot find reference"
                        {:reference reference})))
    (throw (ex-info "References start with something other than #/components/ aren't supported yet. :("
                    {:reference reference}))))

(def ^:private URI
  #?(:clj java.net.URI
     :cljs goog.Uri))

(defn- openapi->schema
  ([schema components]
   (openapi->schema schema components #{}))
  ([schema components seen-set]
   (if-let [reference (get schema "$ref")]
     (if (contains? seen-set reference)
       s/Any ; If we've already seen this, then we're in a loop. Rather than
             ; trying to solve for the fixpoint, just return Any.
       (recur (lookup-ref components reference)
              components
              (conj seen-set reference)))
     (let [wrap (if (get schema "nullable") s/maybe identity)]
       (wrap
        (condp = (get schema "type")
          "string"  (if-let [enum (get schema "enum")]
                      (apply s/enum enum)
                      (condp = (get schema "format")
                        "uuid" s/Uuid
                        "uri" URI
                        s/Str))
          "integer" s/Int
          "number"  s/Num
          "boolean" s/Bool
          "array"   [(openapi->schema (get schema "items") components seen-set)]
          "object"  (let [required? (set (get schema "required"))]
                      (into {}
                            (map (fn [[k v]]
                                   {(if (required? k)
                                      (keyword k)
                                      (s/optional-key (keyword k)))
                                    (openapi->schema v components seen-set)}))
                            (get schema "properties")))
          (throw (ex-info "Cannot convert OpenAPI type to schema" {:definition schema}))))))))

(defn- get-matching-schema [object content-types]
  (if-let [content-type (first (filter #(get-in object ["content" % "schema"]) content-types))]
    [(get-in object ["content" content-type "schema"])
     content-type]
    (when (seq (get object "content"))
      (throw (ex-info "No matching content-type available"
                      {:allowed-content-types  content-types
                       :response-content-types (keys (get object "content"))})))))

(defn- process-body [body components content-types]
  (when-let [[json-schema content-type] (get-matching-schema body content-types)]
    (let [required (get body "required")]
      {:schema       {(if required :body (s/optional-key :body))
                      (openapi->schema json-schema components)}
       :content-type content-type})))

(defn- process-parameters [parameters components]
  (into {}
        (map (fn [param]
               {(if (get param "required")
                  (keyword (get param "name"))
                  (s/optional-key (keyword (get param "name"))))
                (openapi->schema (get param "schema") components)}))
        parameters))

(defn- process-responses [responses components content-types]
  (for [[code value] responses
        :let         [[json-schema content-type] (get-matching-schema value content-types)]]
    {:status       (if (= code "default")
                     s/Any
                     (s/eq (Long/parseLong code)))
     :body         (and json-schema (openapi->schema json-schema components))
     :content-type content-type}))

(defn openapi->handlers [swagger-json content-types]
  (let [components (get swagger-json "components")]
    (for [[url methods] (get swagger-json "paths")
          [method definition] methods
          ;; We only care about things which have a defined operationId, and
          ;; which aren't the associated OPTIONS call.
          :when (and (get definition "operationId")
                     (not= method "options"))
          :let [parameters (group-by #(get % "in") (get definition "parameters"))
                body       (process-body (get definition "requestBody") components content-types)
                responses  (process-responses (get definition "responses") components content-types)]]
      {:path-parts         (vec (tokenise-path url))
       :method             (keyword method)
       :path-schema        (process-parameters (get parameters "path") components)
       :query-schema       (process-parameters (get parameters "query") components)
       :body-schema        (:schema body)
       :form-schema        (process-parameters (get parameters "form") components)
       :headers-schema     (process-parameters (get parameters "header") components)
       :response-schemas   (vec (keep #(dissoc % :content-type) responses))
       :produces           (vec (keep :content-type responses))
       :consumes           [(:content-type body)]
       :summary            (get definition "summary")
       :description        (get definition "description")
       :openapi-definition definition
       :route-name         (->kebab-case-keyword (get definition "operationId"))})))
