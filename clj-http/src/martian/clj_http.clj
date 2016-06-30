(ns martian.clj-http
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [martian.core :as martian]))

(def encode-body
  {:name ::encode-body
   :enter (fn [{:keys [request] :as ctx}]
            (-> ctx
                (assoc-in [:request :content-type] :json)
                (assoc-in [:request :form-params] (:body request))
                (update :request dissoc :body)))})

(def perform-request
  {:name ::perform-request
   :enter (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (http/request
                                  (-> {:as :auto}
                                      (merge request)
                                      (dissoc :params)))))})

(defn bootstrap-swagger [url & [params]]
  (let [swagger-definition (:body (http/get url (merge params {:as :json})))
        {:keys [scheme server-name server-port]} (http/parse-url url)
        base-url (format "%s://%s%s%s" (name scheme) server-name (if server-port (str ":" server-port) "") (get swagger-definition :basePath ""))]
    (martian/bootstrap-swagger base-url swagger-definition {:interceptors [encode-body perform-request]})))
