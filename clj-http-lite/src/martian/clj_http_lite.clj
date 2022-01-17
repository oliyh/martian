(ns martian.clj-http-lite
  (:require [clj-http.lite.client :as http]
            [cheshire.core :as json]
            [clojure.string :as string]
            [martian.core :as martian]
            [martian.interceptors :as interceptors]
            [martian.openapi :refer [openapi-schema?] :as openapi]))

(defn- prepare-response-headers [headers]
  (reduce (fn [m [k v]] (assoc m (keyword k) v)) {} headers))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (-> (http/request request)
                                     ;; convert keys to keywords
                                     (update-in [:headers] prepare-response-headers))))})

(def default-interceptors
  (concat martian/default-interceptors [interceptors/default-encode-body interceptors/default-coerce-response perform-request]))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [{:keys [server-url] :as opts} get-swagger-opts]]
  (let [definition (-> (http/get url (or get-swagger-opts {})) :body (json/parse-string keyword)) ;; clj-http-lite does not support {:as :json} body conversion (yet) so we do it right here
        {:keys [scheme server-name server-port]} (http/parse-url url)
        api-root (or server-url (openapi/base-url definition))
        base-url (if (and (openapi-schema? definition) (not (string/starts-with? api-root "/")))
                   api-root
                   (str (name scheme) "://"
                        server-name (when server-port (str ":" server-port))
                        (if (openapi-schema? definition)
                          api-root
                          (get definition :basePath ""))))]
    (martian/bootstrap-openapi base-url definition (merge default-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
