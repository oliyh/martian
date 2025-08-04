(ns martian.swagger
  (:require [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys]]
            [martian.openapi :refer [produce-route-name tokenise-path]]
            [martian.schema :as schema]
            [schema.core :as s]))

(defn resolve-swagger-params [ref-lookup swagger-params category]
  (->> swagger-params
       (map (schema/resolve-ref-fn ref-lookup))
       (filter #(= category (:in %)))
       not-empty))

(defn- body-schema [ref-lookup swagger-params]
  (when-let [body-params (resolve-swagger-params ref-lookup swagger-params "body")]
    (schema/schemas-for-parameters ref-lookup body-params)))

(defn- form-schema [ref-lookup swagger-params]
  (when-let [form-params (resolve-swagger-params ref-lookup swagger-params "formData")]
    (schema/schemas-for-parameters ref-lookup form-params)))

(defn- path-schema [ref-lookup swagger-params]
  (when-let [path-params (resolve-swagger-params ref-lookup swagger-params "path")]
    (schema/schemas-for-parameters ref-lookup path-params)))

(defn- query-schema [ref-lookup swagger-params]
  (when-let [query-params (resolve-swagger-params ref-lookup swagger-params "query")]
    (schema/schemas-for-parameters ref-lookup query-params)))

(defn- headers-schema [ref-lookup swagger-params]
  (when-let [header-params (resolve-swagger-params ref-lookup swagger-params "header")]
    (schema/schemas-for-parameters ref-lookup header-params)))

(defn- response-schemas [ref-lookup swagger-responses]
  (for [[status response] swagger-responses
        :let [status-code (cond (number? status) status
                                (= "default" (name status)) 'default
                                :else (parse-long (name status)))]]
    {:status (s/eq status-code)
     :body (schema/make-schema ref-lookup (assoc (:schema response) :required true))}))

(defn swagger->handlers
  ([swagger-json]
   (swagger->handlers swagger-json nil))
  ([swagger-json route-name-sources]
   (let [swagger-spec (keywordize-keys swagger-json)]
     (for [[url-pattern swagger-handlers] (:paths swagger-spec)
           :let [common-parameters (:parameters swagger-handlers)]
           [method definition] (dissoc swagger-handlers :parameters)
           :let [route-name (produce-route-name route-name-sources url-pattern method definition)]
           ;; NB: We only care about things which have a route name.
           :when (some? route-name)
           :let [path-parts (tokenise-path url-pattern)
                 ref-lookup (select-keys swagger-spec [:definitions :parameters])
                 parameters (concat common-parameters (:parameters definition))]]
       {;; Common
        :route-name         route-name
        :path-parts         path-parts
        :method             method
        :path-schema        (path-schema ref-lookup parameters)
        :query-schema       (query-schema ref-lookup parameters)
        :body-schema        (body-schema ref-lookup parameters)
        :form-schema        (form-schema ref-lookup parameters)
        :headers-schema     (headers-schema ref-lookup parameters)
        :response-schemas   (response-schemas ref-lookup (:responses definition))
        :produces           (some :produces [definition swagger-spec])
        :consumes           (some :consumes [definition swagger-spec])
        :summary            (:summary definition)

        ;; Swagger-specific
        :path               (str/join path-parts)
        ;; TODO: Also parse all path constraints — required?
        ;; :path-constraints {:id "(\\d+)"},
        ;; {:in "path", :name "id", :description "", :required true, :type "string", :format "uuid" ...}
        :swagger-definition definition}))))
