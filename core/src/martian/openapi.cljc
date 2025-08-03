(ns martian.openapi
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys]]
            [lambdaisland.uri :as uri]
            [martian.schema :as schema]
            [schema.core :as s]))

(defn openapi-schema? [json]
  (boolean (some #(get json %) [:openapi "openapi"])))

(defn base-url [url server-url json]
  (let [first-server (get-in json [:servers 0 :url] "")
        {:keys [scheme host port]} (uri/uri url)
        api-root (or server-url first-server)]
    (if (and (openapi-schema? json) (not (str/starts-with? api-root "/")))
      api-root
      (str scheme "://"
           host
           (when (not (str/blank? port)) (str ":" port))
           (if (openapi-schema? json)
             api-root
             (get json :basePath ""))))))

(defn- wrap-nullable [{:keys [nullable]} schema]
  (if nullable
    (s/maybe schema)
    schema))

(defn- wrap [property schema]
  (reduce (fn [schema f]
            (f property schema))
          schema
          [schema/wrap-default wrap-nullable]))

(defn- openapi->schema
  ([schema components] (openapi->schema schema components #{}))
  ([schema components seen-set]
   (if-let [reference (:$ref schema)]
     (if (contains? seen-set reference)
       s/Any ; If we've already seen this, then we're in a loop. Rather than
             ; trying to solve for the fixpoint, just return Any.
       (recur (schema/lookup-ref reference {:components components})
              components
              (conj seen-set reference)))
     (wrap schema
           (condp = (if-let [typ (:type schema)]
                      typ
                      ;; If a schema has no :type key, and the only key it contains is a :properties key,
                      ;; then the :type can reasonably be inferred as "object".
                      ;;
                      ;; See https://github.com/OAI/OpenAPI-Specification/issues/1657
                      ;;
                      ;; Excerpt:
                      ;; A particularly common form of this is a schema that omits type, but specifies properties.
                      ;; Strictly speaking, this does not mean that the value must be an object.
                      ;; It means that if the value is an object, and it includes any of those properties,
                      ;; the property values must conform to the corresponding property subschemas.
                      ;;
                      ;; In reality, this construct almost always means that the user intends type: object,
                      ;; and I think it would be reasonable for a code generator to assume this,
                      ;; maybe with a validation: strict|lax config option to control that behavior.
                      (when (= #{:properties} (set (keys schema))) "object"))
             "array"   [(openapi->schema (:items schema) components seen-set)]
             "object"  (let [required? (set (:required schema))]
                         (if (or (contains? schema :properties)
                                 (contains? schema :additionalProperties))
                           (into {}
                                 (map (fn [[k v]]
                                        {(if (required? (name k))
                                           (keyword k)
                                           (s/optional-key (keyword k)))
                                         (openapi->schema v components seen-set)}))
                                 (:properties schema))
                           {s/Any s/Any}))
             (schema/leaf-schema schema))))))

(defn- stringify-ns-keyword [k]
  (if (keyword? k)
    (if-let [ns (namespace k)]
      (str ns "/" (name k))
      (name k))
    k))

(defn- get-matching-schema [{:keys [content]} content-types header-name]
  (if-let [content-type (first (filter #(contains? content (keyword %)) content-types))]
    [(get-in content [(keyword content-type) :schema])
     content-type]
    (when (seq content)
      #?(:clj (println "No matching content-type available" {:supported-content-types content-types
                                                             :available-content-types (map stringify-ns-keyword (keys content))
                                                             :header header-name})
         :cljs (js/console.warn "No matching content-type available" {:supported-content-types content-types
                                                                      :available-content-types (map stringify-ns-keyword (keys content))
                                                                      :header header-name})))))

(defn- process-body [body components content-types]
  (when-let [[json-schema content-type] (get-matching-schema body content-types "Accept")]
    (let [required (:required body)]
      {:schema       {(if required :body (s/optional-key :body))
                      (openapi->schema json-schema components)}
       :content-type content-type})))

(defn- process-parameters [parameters components]
  (not-empty
   (into {}
         (map (fn [param]
                {(if (:required param)
                   (keyword (:name param))
                   (s/optional-key (keyword (:name param))))
                 (openapi->schema (:schema param) components)}))
         parameters)))

(defn- process-responses [responses components content-types]
  (for [[status-code value] responses
        :let                [status-code (name status-code)
                             [json-schema content-type] (get-matching-schema value content-types "Content-Type")]]
    {:status       (if (= status-code "default")
                     s/Any
                     (s/eq (if (number? status-code) status-code (parse-long (name status-code)))))
     :body         (and json-schema (openapi->schema json-schema components))
     :content-type content-type}))

(defn- sanitise-url [url-pattern]
  (if (string? url-pattern)
    url-pattern
    ;; NB: This is consistent across CLJ and CLJS.
    (str/replace-first (str url-pattern) ":" "")))

(defn tokenise-path [url-pattern]
  (let [sanitised (sanitise-url url-pattern)
        url-parts (map first (re-seq #"([^{}]+|\{.+?\})" sanitised))]
    (map #(if-let [param-name (second (re-matches #"^\{(.*)\}" %))]
            (keyword param-name)
            %)
         url-parts)))

(defn produce-route-name [url-pattern method definition]
  (->kebab-case-keyword
    (or (:operationId definition)
        (let [prepared-path (->> (tokenise-path url-pattern)
                                 (remove keyword?)
                                 (map #(str/replace % "/" ""))
                                 (map #(str/replace % #"[^a-zA-Z0-9\-]" "-"))
                                 (str/join "-"))]
          (str (name method) "-" prepared-path)))))

(defn openapi->handlers [openapi-json content-types]
  (let [openapi-spec (keywordize-keys openapi-json)
        resolve-ref (schema/resolve-ref-fn openapi-spec)
        components (:components openapi-spec)]
    (for [[url-pattern methods] (:paths openapi-spec)
          :let [common-parameters (map resolve-ref (:parameters methods))]
          [method definition] (dissoc methods :parameters)
          ;; NB: We don't care about any associated HTTP OPTIONS calls.
          :when (not= :options method)
          :let [parameters (->> (map resolve-ref (:parameters definition))
                                (concat common-parameters)
                                (group-by (comp keyword :in)))
                body       (process-body (:requestBody definition) components (:encodes content-types))
                responses  (-> (:responses definition)
                               (update-vals resolve-ref)
                               (process-responses components (:decodes content-types)))]]
      (-> {:path-parts         (vec (tokenise-path url-pattern))
           :method             method
           :path-schema        (process-parameters (:path parameters) components)
           :query-schema       (process-parameters (:query parameters) components)
           :body-schema        (:schema body)
           :form-schema        (process-parameters (:form parameters) components)
           :headers-schema     (process-parameters (:header parameters) components)
           :response-schemas   (vec (keep #(dissoc % :content-type) responses))
           :produces           (vec (keep :content-type responses))
           :consumes           (when-let [content-type (:content-type body)]
                                 [content-type])
           :summary            (:summary definition)
           :description        (:description definition)
           :openapi-definition definition
           :route-name         (produce-route-name url-pattern method definition)}
          (cond-> (:deprecated definition) (assoc :deprecated? true))))))
