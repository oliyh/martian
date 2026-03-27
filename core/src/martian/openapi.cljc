(ns martian.openapi
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys]]
            [inflections.core :refer [parameterize singular]]
            [lambdaisland.uri :as uri]
            [martian.log :as log]
            [martian.schema :as schema]
            [martian.schema-backend :as sb]
            [martian.utils :as utils]))

(defn openapi-schema? [json]
  (boolean (some #(get json %) [:openapi "openapi"])))

(defn base-url
  "Returns the API root URL derived from the spec URL, an optional
  server override, and the parsed JSON spec.

  Resolution order for OpenAPI (3.x) specs:

  1. If `server-url` is provided and non-blank, it is used as-is when
     absolute (e.g. \"https://example.com\"), or resolved against `url`
     when relative (e.g. \"/v3\" → \"http://host/v3\").

  2. Otherwise, `servers[0].url` from the spec is used, applying the
     same absolute-vs-relative logic.

  3. If neither is present (no `:servers` key in the spec), the base is
     derived from the directory of `url` via RFC 3986 resolution:
     - absolute `url`  → origin only, e.g. \"http://localhost:8888\"
     - relative `url`  → parent path, e.g. \"/api/spec.json\" → \"/api\"
     - top-level relative `url` (e.g. \"/spec.json\") → \"\" (empty string),
       which is correct because OpenAPI paths begin with \"/\", so
       api-root + path remains a valid relative URL.

  For Swagger (2.x) specs, the base is always reconstructed as
  scheme://host[:port] + basePath from the parsed `url`.

  The return value never has a trailing slash."
  [url server-url json]
  (let [first-server (get-in json [:servers 0 :url] "")
        {:keys [scheme host port]} (uri/uri url)
        api-root (or server-url first-server)]
    (if (openapi-schema? json)
      (if (str/blank? api-root)
        ;; No servers declared: derive base from "directory" of the spec URL
        (str/replace (str (uri/join url ".")) #"/$" "")
        (if (str/starts-with? api-root "/")
          ;; Relative server path (e.g. "/v3"): resolve against the spec URL
          (str (uri/join url api-root))
          ;; Absolute api-root: return verbatim
          api-root))
      ;; Swagger / non-OpenAPI: reconstruct origin + basePath
      (str scheme "://"
           host
           (when (not (str/blank? port)) (str ":" port))
           (get json :basePath "")))))

(defn- wrap [backend property schema]
  (cond-> (sb/with-default-value backend property schema)
    (:nullable property) (->> (sb/maybe-schema backend))))

(defn- openapi->schema
  ([schema components backend] (openapi->schema schema components backend #{}))
  ([schema components backend seen-set]
   (if-let [reference (:$ref schema)]
     (if (contains? seen-set reference)
       (sb/any-schema backend) ; If we've already seen this, then we're in a loop. Rather than
                               ; trying to solve for the fixpoint, just return Any.
       (recur (schema/lookup-ref reference {:components components})
              components
              backend
              (conj seen-set reference)))
     (wrap backend schema
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
             "array"  [(openapi->schema (:items schema) components backend seen-set)]
             "object" (let [required? (set (:required schema))
                            {:keys [additionalProperties properties]} schema
                            any (sb/any-schema backend)]
                        (if (or (contains? schema :properties)
                                additionalProperties)
                          (into (if additionalProperties {any any} {})
                                (map (fn [[k v]]
                                       {(if (required? (name k))
                                          (keyword k)
                                          (sb/optional-key backend (keyword k)))
                                        (openapi->schema v components backend seen-set)}))
                                properties)
                          {any any}))
             (sb/leaf-schema backend schema))))))

(defn- warn-on-no-matching-content-type
  [supported-content-types content header-name]
  (let [available-content-types (mapv utils/stringify-named (keys content))]
    (log/warn "No matching content-type available"
              {:header header-name
               :supported supported-content-types
               :available available-content-types})))

(defn- get-matching-schema [{:keys [content]} content-types header-name]
  (when (seq content)
    (or (when-some [any-type (:*/* content)]
          [(:schema any-type) nil])
        (when-some [content-type (some #(when (contains? content (keyword %)) %)
                                       content-types)]
          [(get-in content [(keyword content-type) :schema]) content-type])
        (warn-on-no-matching-content-type content-types content header-name))))

(defn- process-body [body components content-types backend]
  (when-let [[json-schema content-type] (get-matching-schema body content-types "Accept")]
    (let [required (:required body)]
      {:schema       {(if required :body (sb/optional-key backend :body))
                      (openapi->schema json-schema components backend)}
       :content-type content-type})))

(defn- process-parameters [parameters components backend]
  (not-empty
   (into {}
         (map (fn [param]
                {(if (:required param)
                   (keyword (:name param))
                   (sb/optional-key backend (keyword (:name param))))
                 (openapi->schema (:schema param) components backend)}))
         parameters)))

(defn- range-1XX [n] (<= 100 n 199))
(defn- range-2XX [n] (<= 200 n 299))
(defn- range-3XX [n] (<= 300 n 399))
(defn- range-4XX [n] (<= 400 n 499))
(defn- range-5XX [n] (<= 500 n 599))

(defn- process-responses [responses components content-types backend]
  (for [[status-code value] responses
        :let                [status-code (name status-code)
                             [json-schema content-type] (get-matching-schema value content-types "Content-Type")]]
    {:status       (cond (= status-code "default")
                         (sb/any-schema backend)

                         (number? status-code)
                         (sb/eq-schema backend status-code)

                         (and (string? status-code) (re-matches #"[12345]XX" status-code))
                         (sb/constrained-schema backend
                                                (sb/int-schema backend)
                                                (case (first status-code)
                                                  \1 range-1XX
                                                  \2 range-2XX
                                                  \3 range-3XX
                                                  \4 range-4XX
                                                  \5 range-5XX))

                         :else
                         (sb/eq-schema backend (parse-long (name status-code))))
     :body         (and json-schema (openapi->schema json-schema components backend))
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

(defn generate-route-name
  [url-pattern method]
  ;; NB: This is a simple algo based on the naming conventions:
  ;;     - GET "/users/{uid}/orders/"      -> :get-user-orders
  ;;     - GET "/users/{uid}/orders/{oid}" -> :get-user-order
  (->> (tokenise-path url-pattern)
       (partition-all 2)
       (map (fn [[part param]]
              (cond-> (parameterize (str/replace part "/" ""))
                      param (singular))))
       (cons (name method))
       (str/join "-")))

(defn produce-route-name
  [route-name-sources url-pattern method definition]
  (loop [sources (or route-name-sources [:operationId])]
    (let [[source & rest] sources
          route-name (cond
                       (= :operationId source) (:operationId definition)
                       (= :method+path source) (generate-route-name url-pattern method)
                       (fn? source) (source url-pattern method definition))]
      (if (some? route-name)
        (->kebab-case-keyword route-name)
        (if (empty? rest)
          (log/warn "No route name, ignoring endpoint" {:url-pattern url-pattern :method method})
          (recur rest))))))

(defn unique-route-name?
  [route-name route-names]
  (if (contains? @route-names route-name)
    (log/warn "Non-unique route name, ignoring endpoint" {:route-name route-name})
    (do (swap! route-names conj route-name) true)))

(defn openapi->handlers
  "Converts an OpenAPI JSON spec into a sequence of handler maps.

   Accepts an optional opts map as the fourth argument, which may contain:
   - `:schema-backend` — a SchemaBackend implementation; defaults to the Plumatic backend."
  ([openapi-json content-types]
   (openapi->handlers openapi-json content-types nil nil))
  ([openapi-json content-types route-name-sources]
   (openapi->handlers openapi-json content-types route-name-sources nil))
  ([openapi-json {:keys [encodes decodes] :as _content-types} route-name-sources opts]
   (let [backend      (schema/get-backend opts)
         openapi-spec (keywordize-keys openapi-json)
         resolve-ref  (schema/resolve-ref-fn openapi-spec)
         components   (:components openapi-spec)
         route-names  (atom #{})]
     (for [[url-pattern methods] (:paths openapi-spec)
           :let [common-parameters (map resolve-ref (:parameters methods))]
           [method definition] (dissoc methods :parameters)
           :let [route-name (produce-route-name route-name-sources url-pattern method definition)]
           ;; NB: We only care about routes that have a unique name
           ;;     and which aren't the associated HTTP OPTIONS call.
           :when (and (some? route-name)
                      (unique-route-name? route-name route-names)
                      (not= :options method))
           :let [parameters (->> (map resolve-ref (:parameters definition))
                                 (concat common-parameters)
                                 (group-by (comp keyword :in)))
                 body       (process-body (resolve-ref (:requestBody definition)) components encodes backend)
                 responses  (-> (:responses definition)
                                (update-vals resolve-ref)
                                (process-responses components decodes backend))]]
       (-> {:path-parts         (vec (tokenise-path url-pattern))
            :method             method
            :path-schema        (process-parameters (:path parameters) components backend)
            :query-schema       (process-parameters (:query parameters) components backend)
            :body-schema        (:schema body)
            :form-schema        (process-parameters (:form parameters) components backend)
            :headers-schema     (process-parameters (:header parameters) components backend)
            :response-schemas   (vec (keep #(dissoc % :content-type) responses))
            :produces           (vec (keep :content-type responses))
            :consumes           (when-let [content-type (:content-type body)]
                                  [content-type])
            :summary            (:summary definition)
            :description        (:description definition)
            :openapi-definition definition
            :route-name         route-name}
           (cond-> (:deprecated definition) (assoc :deprecated? true)))))))
