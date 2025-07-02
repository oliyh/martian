(ns martian.hato
  (:require [clojure.walk :refer [keywordize-keys stringify-keys]]
            [hato.client :as http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.file :as file]
            [martian.interceptors :as interceptors]
            [martian.openapi :as openapi]
            [martian.yaml :as yaml]
            [tripod.context :as tc]))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (http/request request)))})

(defn- process-async-response [ctx response]
  (:response (tc/execute (assoc ctx :response response))))

(defn- process-async-error [ctx error]
  (:response (tc/execute (assoc ctx ::tc/error error))))

(def perform-request-async
  {:name ::perform-request-async
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                interceptors/remove-stack
                (assoc :response
                       (http/request (assoc request :async? true)
                                     (partial process-async-response ctx)
                                     (partial process-async-error ctx)))))})

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

(def request-encoders
  (assoc (encoders/default-encoders)
    "multipart/form-data" {:encode encoders/multipart-encode}))

(def hato-interceptors
  (conj martian/default-interceptors
        (interceptors/encode-request request-encoders)
        interceptors/default-coerce-response
        keywordize-headers
        default-to-http-1))

(def default-interceptors
  (conj hato-interceptors perform-request))

(def default-interceptors-async
  (conj hato-interceptors perform-request-async))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn- load-definition [url load-opts]
  (or (file/local-resource url)
      (if (yaml/yaml-url? url)
        (yaml/yaml->edn (:body (http/get url (dissoc load-opts :as))))
        (:body (http/get url (merge {:as :json} load-opts))))))

(defn bootstrap-openapi [url & [{:keys [server-url] :as opts} load-opts]]
  (let [definition (load-definition url load-opts)
        base-url (openapi/base-url url server-url definition)]
    (martian/bootstrap-openapi base-url definition (merge default-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
