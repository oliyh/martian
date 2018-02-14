(ns martian.clj-http
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [cognitect.transit :as transit]
            [martian.core :as martian]
            [martian.interceptors :as interceptors])
  (:import [java.io ByteArrayInputStream]))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (http/request request)))})

(def default-interceptors
  (concat martian/default-interceptors [interceptors/default-encode-body interceptors/default-coerce-response perform-request]))

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge {:interceptors default-interceptors} opts)))

(defn bootstrap-swagger [url & [{:keys [interceptors] :as params}]]
  (let [swagger-definition (:body (http/get url {:as :json}))
        {:keys [scheme server-name server-port]} (http/parse-url url)
        base-url (format "%s://%s%s%s" (name scheme) server-name (if server-port (str ":" server-port) "") (get swagger-definition :basePath ""))]
    (martian/bootstrap-swagger
     base-url
     swagger-definition
     {:interceptors (or interceptors default-interceptors)})))
