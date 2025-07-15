(ns martian.httpkit
  (:require [cheshire.core :as json]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.file :as file]
            [martian.http-clients :as hc]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [martian.yaml :as yaml]
            [org.httpkit.client :as http]
            [tripod.context :as tc])
  (:import (java.nio ByteBuffer)))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                hc/go-async
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

(def response-encoders
  (encoders/default-encoders))

;; NB: `http-kit` does not support "Content-Type"-based coercion.
(def response-coerce-opts
  {:default-encoder-as :text})

(def default-interceptors
  (conj martian/default-interceptors
        (i/encode-request request-encoders)
        (i/coerce-response response-encoders response-coerce-opts)
        perform-request))

(defn build-custom-opts [opts]
  {:interceptors (hc/update-basic-interceptors
                   default-interceptors
                   (conj {:response-coerce-opts response-coerce-opts} opts))})

(def default-opts {:interceptors default-interceptors})

(def ^:private supported-opts
  [:request-encoders :response-encoders])

(defn prepare-opts [opts]
  (hc/prepare-opts build-custom-opts supported-opts default-opts opts))

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (prepare-opts opts)))

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
    (martian/bootstrap-openapi base-url definition (prepare-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
