(ns martian.babashka.http-client
  (:require [babashka.http-client :as http]
            [babashka.json :as json]
            [martian.core :as martian]
            [martian.interceptors :as interceptors]
            [martian.openapi :as openapi]
            [martian.yaml :as yaml]
            [clojure.walk :refer [keywordize-keys stringify-keys]]
            [tripod.context :as tc])
  (:import [java.util.function Function]))

(defn normalize-request [request]
  (cond-> request
    (not (:uri request))
    (assoc :uri (:url request))
    (= :byte-array (:as request))
    (assoc :as :bytes)
    (:throw-exceptions? request)
    (assoc :throw false)
    (= :http-1.1 (:version request))
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
                interceptors/remove-stack
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

(def babashka-http-client-interceptors
  (concat martian/default-interceptors [interceptors/default-encode-body interceptors/default-coerce-response
                                        keywordize-headers default-to-http-1]))

(def default-interceptors
  (concat babashka-http-client-interceptors [perform-request]))

(def default-interceptors-async
  (concat babashka-http-client-interceptors [perform-request-async]))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [{:keys [server-url] :as opts} get-swagger-opts]]
  (let [body (:body (http/get url (dissoc get-swagger-opts :as)))
        definition (if (yaml/yaml-url? url)
                     (yaml/yaml->edn body)
                     (json/read-str body))
        base-url (openapi/base-url url server-url definition)]
    (martian/bootstrap-openapi base-url definition (merge default-opts opts))))

(def bootstrap-swagger bootstrap-openapi)

