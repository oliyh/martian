(ns martian.swagger
  (:require [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys]]
            [martian.openapi :refer [produce-route-name tokenise-path unique-route-name?]]
            [martian.schema :as schema]
            [martian.schema-backend :as sb]))

(defn resolve-swagger-params [ref-lookup swagger-params category]
  (->> swagger-params
       (map (schema/resolve-ref-fn ref-lookup))
       (filter #(= category (:in %)))
       not-empty))

(defn- body-schema [ref-lookup swagger-params backend]
  (when-let [body-params (resolve-swagger-params ref-lookup swagger-params "body")]
    (schema/schemas-for-parameters ref-lookup body-params backend)))

(defn- form-schema [ref-lookup swagger-params backend]
  (when-let [form-params (resolve-swagger-params ref-lookup swagger-params "formData")]
    (schema/schemas-for-parameters ref-lookup form-params backend)))

(defn- path-schema [ref-lookup swagger-params backend]
  (when-let [path-params (resolve-swagger-params ref-lookup swagger-params "path")]
    (schema/schemas-for-parameters ref-lookup path-params backend)))

(defn- query-schema [ref-lookup swagger-params backend]
  (when-let [query-params (resolve-swagger-params ref-lookup swagger-params "query")]
    (schema/schemas-for-parameters ref-lookup query-params backend)))

(defn- headers-schema [ref-lookup swagger-params backend]
  (when-let [header-params (resolve-swagger-params ref-lookup swagger-params "header")]
    (schema/schemas-for-parameters ref-lookup header-params backend)))

(defn- response-schemas [ref-lookup swagger-responses backend]
  (for [[status response] swagger-responses
        :let [status-code (cond (number? status) status
                                (= "default" (name status)) 'default
                                :else (parse-long (name status)))]]
    {:status (sb/eq-schema backend status-code)
     :body   (schema/make-schema ref-lookup (assoc (:schema response) :required true) backend)}))

(defn swagger->handlers
  "Converts a Swagger JSON spec into a sequence of handler maps.

   Accepts an optional opts map as the third argument, which may contain:
   - `:schema-backend` — a SchemaBackend implementation; defaults to the Plumatic backend."
  ([swagger-json]
   (swagger->handlers swagger-json nil nil))
  ([swagger-json route-name-sources]
   (swagger->handlers swagger-json route-name-sources nil))
  ([swagger-json route-name-sources opts]
   (let [backend      (schema/get-backend opts)
         swagger-spec (keywordize-keys swagger-json)
         route-names  (atom #{})]
     (for [[url-pattern swagger-handlers] (:paths swagger-spec)
           :let [common-parameters (:parameters swagger-handlers)]
           [method definition] (dissoc swagger-handlers :parameters)
           :let [route-name (produce-route-name route-name-sources url-pattern method definition)]
           ;; NB: We only care about routes that have a unique name.
           :when (and (some? route-name)
                      (unique-route-name? route-name route-names))
           :let [path-parts (tokenise-path url-pattern)
                 ref-lookup (select-keys swagger-spec [:definitions :parameters])
                 parameters (concat common-parameters (:parameters definition))]]
       {:path               (str/join path-parts)
        :path-parts         path-parts
        ;; TODO: Also parse all path constraints — required?
        ;; :path-constraints {:id "(\\d+)"},
        ;; {:in "path", :name "id", :description "", :required true, :type "string", :format "uuid" ...}
        :method             method
        :path-schema        (path-schema ref-lookup parameters backend)
        :query-schema       (query-schema ref-lookup parameters backend)
        :body-schema        (body-schema ref-lookup parameters backend)
        :form-schema        (form-schema ref-lookup parameters backend)
        :headers-schema     (headers-schema ref-lookup parameters backend)
        :response-schemas   (response-schemas ref-lookup (:responses definition) backend)
        :produces           (some :produces [definition swagger-spec])
        :consumes           (some :consumes [definition swagger-spec])
        :summary            (:summary definition)
        :swagger-definition definition
        :route-name         route-name}))))
