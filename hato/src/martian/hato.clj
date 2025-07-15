(ns martian.hato
  (:require [clojure.walk :refer [keywordize-keys stringify-keys]]
            [hato.client :as http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.file :as file]
            [martian.http-clients :as hc]
            [martian.interceptors :as i]
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
                hc/go-async
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

(def response-encoders
  (encoders/default-encoders))

;; NB: In accordance with the `hato`'s Optional Dependencies, which all happen
;;     to be on the classpath already as the Martian core module dependencies,
;;     we could (or, at the very least, should allow to) skip Martian response
;;     decoding for those media types.
;;     https://github.com/gnarroway/hato#request-options
(defn response-coerce-opts [use-client-output-coercion?]
  (conj {:auto-coercion-pred #{:auto}}
        (if use-client-output-coercion?
          {:skip-decoding-for #{"application/edn"
                                "application/json"
                                "application/transit+json"
                                "application/transit+msgpack"}
           :default-encoder-as :auto}
          {:default-encoder-as :string})))

(def hato-interceptors
  (conj martian/default-interceptors
        (i/encode-request request-encoders)
        (i/coerce-response response-encoders (response-coerce-opts false))
        keywordize-headers
        default-to-http-1))

(defn build-custom-opts [{:keys [async? use-client-output-coercion?] :as opts}]
  (let [response-coerce-opts (response-coerce-opts use-client-output-coercion?)]
    {:interceptors (-> hato-interceptors
                       (hc/update-basic-interceptors
                         (conj {:response-coerce-opts response-coerce-opts} opts))
                       (conj (if async? perform-request-async perform-request)))}))

(def default-interceptors
  (conj hato-interceptors perform-request))

(def default-interceptors-async
  (conj hato-interceptors perform-request-async))

(def default-opts {:interceptors default-interceptors})

(def ^:private supported-opts
  [:async? :request-encoders :response-encoders :use-client-output-coercion?])

(defn prepare-opts [opts]
  (hc/prepare-opts build-custom-opts supported-opts default-opts opts))

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (prepare-opts opts)))

(defn- load-definition [url load-opts]
  (or (file/local-resource url)
      (if (yaml/yaml-url? url)
        (yaml/yaml->edn (:body (http/get url (dissoc load-opts :as))))
        (:body (http/get url (merge {:as :json} load-opts))))))

(defn bootstrap-openapi [url & [{:keys [server-url] :as opts} load-opts]]
  (let [definition (load-definition url load-opts)
        base-url (openapi/base-url url server-url definition)]
    (martian/bootstrap-openapi base-url definition (prepare-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
