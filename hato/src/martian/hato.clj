(ns martian.hato
  (:require [hato.client :as http]
            [martian.core :as martian]
            [martian.interceptors :as interceptors]
            [martian.openapi :refer [openapi-schema?] :as openapi]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys stringify-keys]]
            [tripod.context :as tc])
  (:import [java.net URL]))

(defn- parse-url
  "Parse a URL string into a map of interesting parts. Lifted from clj-http."
  [url]
  (let [url-parsed (URL. url)]
    {:scheme (keyword (.getProtocol url-parsed))
     :server-name (.getHost url-parsed)
     :server-port (let [port (.getPort url-parsed)]
                    (when (pos? port) port))}))

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

(def hato-interceptors
  (concat martian/default-interceptors [interceptors/default-encode-body interceptors/default-coerce-response
                                        keywordize-headers default-to-http-1]))

(def default-interceptors
  (concat hato-interceptors [perform-request]))

(def default-interceptors-async
  (concat hato-interceptors [perform-request-async]))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [opts get-swagger-opts]]
  (let [definition (:body (http/get url (merge {:as :json} get-swagger-opts)))
        {:keys [scheme server-name server-port]} (parse-url url)
        api-root (or (:server-url opts) (openapi/base-url definition))
        base-url (if (and (openapi-schema? definition) (not (string/starts-with? api-root "/")))
                   api-root
                   (format "%s://%s%s%s" (name scheme) server-name (if server-port (str ":" server-port) "")
                           (if (openapi-schema? definition)
                             api-root
                             (get definition :basePath ""))))]
    (martian/bootstrap-openapi base-url definition (merge default-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
