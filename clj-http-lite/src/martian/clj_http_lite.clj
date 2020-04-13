(ns martian.clj-http-lite
  (:require [clj-http.lite.client :as http]
            [cheshire.core :as json]
            [martian.core :as martian]
            [martian.interceptors :as interceptors]))

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

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge {:interceptors default-interceptors} opts)))

(defn bootstrap-swagger [url & [{:keys [interceptors] :as params}]]
  ;; clj-http-lite does not support {:as :json} body conversion (yet) so we do it right here
  (let [swagger-definition (-> (http/get url) :body (json/parse-string keyword))
        {:keys [scheme server-name server-port]} (http/parse-url url)
        base-url (format "%s://%s%s%s" (name scheme) server-name (if server-port (str ":" server-port) "") (get swagger-definition :basePath ""))]
    (martian/bootstrap-swagger
     base-url
     swagger-definition
     {:interceptors (or interceptors default-interceptors)})))
