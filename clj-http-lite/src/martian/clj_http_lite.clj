(ns martian.clj-http-lite
  (:require [cheshire.core :as json]
            [clj-http.lite.client :as http]
            [martian.core :as martian]
            [martian.file :as file]
            [martian.interceptors :as interceptors]
            [martian.openapi :as openapi]
            [martian.yaml :as yaml]))

(defn- prepare-response-headers [headers]
  (reduce (fn [m [k v]] (assoc m (keyword k) v)) {} headers))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (-> (http/request request)
                                     ;; convert keys to keywords
                                     (update-in [:headers] prepare-response-headers))))})

(def default-interceptors
  (conj martian/default-interceptors
        ;; clj-http-lite does not support 'multipart/form-data' uploads
        interceptors/default-encode-body
        interceptors/default-coerce-response
        perform-request))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn- load-definition [url load-opts]
  (or (file/local-resource url)
      (let [body (:body (http/get url (or load-opts {})))]
        (if (yaml/yaml-url? url)
          (yaml/yaml->edn body)
          ;; clj-http-lite does not support {:as :json} body conversion (yet) so we do it right here
          (json/parse-string body keyword)))))

(defn bootstrap-openapi [url & [{:keys [server-url] :as opts} load-opts]]
  (let [definition (load-definition url load-opts)
        base-url (openapi/base-url url server-url definition)]
    (martian/bootstrap-openapi base-url definition (merge default-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
