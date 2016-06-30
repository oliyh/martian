(ns martian.cljs-http
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [martian.core :as martian])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def encode-body
  {:name ::encode-body
   :enter (fn [{:keys [request] :as ctx}]
            (-> ctx
                (assoc-in [:request :json-params] (:body request))
                (update :request dissoc :body)))})

(def perform-request
  {:name ::perform-request
   :enter (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (http/request
                                  (-> {:as :auto}
                                      (merge request)
                                      (dissoc :params)))))})

(defn bootstrap-swagger [url & [params]]
  (go (let [swagger-definition (:body (<! (http/get url (merge params {:as :json}))))
            {:keys [scheme server-name server-port]} (http/parse-url url)
            base-url (str (name scheme) "://" server-name (if server-port (str ":" server-port) "") (get swagger-definition :basePath ""))]
        (martian/bootstrap-swagger base-url swagger-definition {:interceptors [encode-body perform-request]}))))
