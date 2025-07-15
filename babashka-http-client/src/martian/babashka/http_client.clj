(ns martian.babashka.http-client
  (:require [babashka.http-client :as http]
            [babashka.json :as json]
            [clojure.walk :refer [keywordize-keys stringify-keys]]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.file :as file]
            [martian.http-clients :as hc]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [martian.utils :as utils]
            [martian.yaml :as yaml]
            [tripod.context :as tc])
  (:import [java.util.function Function]))

(defn normalize-request
  [{:keys [version uri url as throw-exceptions?] :as request}]
  (cond-> request
    (not uri)
    (assoc :uri url)

    ;; NB: This value is no longer passed by the library itself.
    ;;     Leaving this clause intact for better compatibility.
    (= :byte-array as)
    (assoc :as :bytes)

    ;; NB: This value is no longer passed by the library itself.
    ;;     Leaving this clause intact for better compatibility.
    (= :text as)
    (dissoc :as)

    throw-exceptions?
    (assoc :throw false)

    (= :http-1.1 version)
    (assoc :version :http1.1)))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (http/request (normalize-request request))))})

(defn- process-async-response [ctx response]
  (:response (tc/execute (assoc ctx :response response))))

(defn- process-async-error [ctx error]
  (:response (tc/execute (assoc ctx ::tc/error error))))

(def perform-request-async
  {:name ::perform-request-async
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                hc/go-async
                (assoc :response
                       (-> (http/request (-> (normalize-request request) (assoc :async true)))
                           (.thenApply
                            (reify Function
                              (apply [_ resp]
                                (process-async-response ctx resp))))
                           (.exceptionally
                            (reify Function
                              (apply [_ e]
                                (process-async-error ctx e))))))))})

(def default-to-http-1
  {:name ::default-to-http-1
   :enter (fn [ctx]
            (update-in ctx [:request :version] #(or % :http-1.1)))})

(def keywordize-headers
  {:name ::keywordize-headers
   :enter (fn [ctx]
            (update-in ctx [:request :headers] stringify-keys))
   :leave (fn [ctx]
            (update-in ctx [:response :headers] keywordize-keys))})

(def default-request-encoders
  (assoc (encoders/default-encoders)
    "multipart/form-data" {:encode encoders/multipart-encode}))

(def default-response-encoders
  (utils/update* (encoders/default-encoders)
                 "application/transit+msgpack" assoc :as :bytes))

;; NB: `babashka-http-client` does not support "Content-Type"-based coercion.
(def response-coerce-opts
  {:type-aliases {:string :text
                  :byte-array :bytes}
   :missing-encoder-as nil
   :default-encoder-as nil})

(def babashka-http-client-interceptors
  (conj martian/default-interceptors
        (i/encode-request default-request-encoders)
        (i/coerce-response default-response-encoders response-coerce-opts)
        keywordize-headers
        default-to-http-1))

(def supported-custom-opts
  [:async? :request-encoders :response-encoders])

(defn build-custom-opts [{:keys [async?] :as opts}]
  {:interceptors (-> babashka-http-client-interceptors
                     (hc/update-basic-interceptors
                       (conj {:response-encoders default-response-encoders
                              :response-coerce-opts response-coerce-opts}
                             opts))
                     (conj (if async? perform-request-async perform-request)))})

(def default-interceptors
  (conj babashka-http-client-interceptors perform-request))

(def default-interceptors-async
  (conj babashka-http-client-interceptors perform-request-async))

(def default-opts {:interceptors default-interceptors})

(defn prepare-opts [opts]
  (hc/prepare-opts build-custom-opts supported-custom-opts default-opts opts))

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (prepare-opts opts)))

(defn- load-definition [url load-opts]
  (or (file/local-resource url)
      (let [body (:body (http/get url (normalize-request load-opts)))]
        (if (yaml/yaml-url? url)
          (yaml/yaml->edn body)
          ;; `babashka-http-client` has no support for `:json` response coercion
          (json/read-str body)))))

(defn bootstrap-openapi [url & [{:keys [server-url] :as opts} load-opts]]
  (let [definition (load-definition url load-opts)
        base-url (openapi/base-url url server-url definition)]
    (martian/bootstrap-openapi base-url definition (prepare-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
