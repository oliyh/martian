(ns martian.clj-http
  (:require [clj-http.client :as http]
            [martian.core :as martian]
            [martian.interceptors :as interceptors]
            [martian.openapi :refer [openapi-schema?] :as openapi])
  (:import [java.io ByteArrayInputStream]))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (http/request request)))})

(def default-interceptors
  (concat martian/default-interceptors [interceptors/default-encode-body interceptors/default-coerce-response perform-request]))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [opts]]
  (let [definition (:body (http/get url {:as :json}))
        {:keys [scheme server-name server-port]} (http/parse-url url)
        base-url (format "%s://%s%s%s" (name scheme) server-name (if server-port (str ":" server-port) "")
                         (if (openapi-schema? definition)
                           (openapi/base-url definition)
                           (get definition :basePath "")))]
    (martian/bootstrap-openapi base-url definition (merge default-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
