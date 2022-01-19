(ns martian.httpkit
  (:require [org.httpkit.client :as http]
            [martian.core :as martian]
            [martian.interceptors :as interceptors]
            [martian.openapi :as openapi]
            [cheshire.core :as json]
            [tripod.context :as tc])
  (:import [java.net URL]))

(def go-async interceptors/remove-stack)

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                go-async
                (assoc :response
                       (http/request request
                                     (fn [response]
                                       (:response (tc/execute (assoc ctx :response response))))))))})

(def default-interceptors
  (concat martian/default-interceptors [interceptors/default-encode-body
                                        interceptors/default-coerce-response
                                        perform-request]))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [{:keys [server-url] :as opts} get-swagger-opts]]
  (let [definition @(http/get url
                              (merge {:as :text} get-swagger-opts)
                              (fn [{:keys [body]}] (json/decode body keyword)))
        base-url (openapi/base-url url server-url definition)]
    (martian/bootstrap-openapi base-url definition (merge default-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
