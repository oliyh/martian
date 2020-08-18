(ns martian.swagger
  (:require [martian.openapi :refer [tokenise-path]]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [martian.schema :as schema]
            [schema.core :as s]
            #?(:cljs [cljs.reader :refer [read-string]])))

(defn- body-schema [ref-lookup swagger-params]
  (when-let [body-params (not-empty (filter #(= "body" (:in %)) swagger-params))]
    (schema/schemas-for-parameters ref-lookup body-params)))

(defn- form-schema [ref-lookup swagger-params]
  (when-let [form-params (not-empty (filter #(= "formData" (:in %)) swagger-params))]
    (schema/schemas-for-parameters ref-lookup form-params)))

(defn- path-schema [ref-lookup swagger-params]
  (when-let [path-params (not-empty (filter #(= "path" (:in %)) swagger-params))]
    (schema/schemas-for-parameters ref-lookup path-params)))

(defn- query-schema [ref-lookup swagger-params]
  (when-let [query-params (not-empty (filter #(= "query" (:in %)) swagger-params))]
    (schema/schemas-for-parameters ref-lookup query-params)))

(defn- headers-schema [ref-lookup swagger-params]
  (when-let [header-params (not-empty (filter #(= "header" (:in %)) swagger-params))]
    (schema/schemas-for-parameters ref-lookup header-params)))

(defn- response-schemas [ref-lookup swagger-responses]
  (for [[status response] swagger-responses
        :let [status-code (if (number? status) status (read-string (name status)))]]
    {:status (s/eq status-code)
     :body (schema/make-schema ref-lookup (assoc (:schema response) :required true))}))

(defn- ->handler
  [swagger-map
   path-item-parameters
   url-pattern
   [method swagger-definition]]
  (when-let [route-name (some-> (:operationId swagger-definition) ->kebab-case-keyword)]
    (let [ref-lookup (select-keys swagger-map [:definitions :parameters])
          path-parts (tokenise-path url-pattern)
          uri (string/join (map str path-parts))
          parameters (concat path-item-parameters (:parameters swagger-definition))]
      {:path uri
       :path-parts path-parts
       :method method
       :path-schema (path-schema ref-lookup parameters)
       :query-schema (query-schema ref-lookup parameters)
       :body-schema (body-schema ref-lookup parameters)
       :form-schema (form-schema ref-lookup parameters)
       :headers-schema (headers-schema ref-lookup parameters)
       :response-schemas (response-schemas ref-lookup (:responses swagger-definition))
       :produces (some :produces [swagger-definition swagger-map])
       :consumes (some :consumes [swagger-definition swagger-map])
       :summary (:summary swagger-definition)
       :swagger-definition swagger-definition
       ;; todo path constraints - required?
       ;; :path-constraints {:id "(\\d+)"},
       ;; {:in "path", :name "id", :description "", :required true, :type "string", :format "uuid"
       :route-name route-name})))

(defn swagger->handlers [swagger-json]
  (let [swagger-spec (keywordize-keys swagger-json)]
    (reduce-kv
     (fn [handlers url-pattern swagger-handlers]
       (into handlers (keep (partial ->handler
                                     swagger-spec
                                     (:parameters swagger-handlers)
                                     url-pattern)
                            swagger-handlers)))
     []
     (:paths swagger-spec))))
