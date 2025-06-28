(ns martian.httpkit
  (:require [cheshire.core :as json]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.file :as file]
            [martian.interceptors :as interceptors]
            [martian.openapi :as openapi]
            [martian.yaml :as yaml]
            [org.httpkit.client :as http]
            [tripod.context :as tc])
  (:import (java.nio ByteBuffer)))

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

;; NB: Although 'http-kit' has built-in support for numbers, we omit it.
(defn custom-type? [obj]
  (instance? ByteBuffer obj))

(def request-encoders
  (assoc (encoders/default-encoders)
    "multipart/form-data" {:encode #(encoders/multipart-encode % custom-type?)}))

(def default-interceptors
  (conj martian/default-interceptors
        (interceptors/encode-request request-encoders)
        (interceptors/coerce-response (encoders/default-encoders)
                                      {:delegate-on-missing? false})
        perform-request))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn- load-definition [url load-opts]
  (or (file/local-resource url)
      @(http/get url
                 (merge {:as :text} load-opts)
                 (fn [{:keys [body]}]
                   (if (yaml/yaml-url? url)
                     (yaml/yaml->edn body)
                     ;; `http-kit` has no support for `:json` response coercion
                     (json/decode body keyword))))))

(defn bootstrap-openapi [url & [{:keys [server-url] :as opts} load-opts]]
  (let [definition (load-definition url load-opts)
        base-url (openapi/base-url url server-url definition)]
    (martian/bootstrap-openapi base-url definition (merge default-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
