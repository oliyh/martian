(ns martian.httpkit
  (:require [org.httpkit.client :as http]
            [martian.core :as martian]
            [martian.interceptors :as interceptors]
            [martian.openapi :refer [openapi-schema?] :as openapi]
            [cheshire.core :as json]
            [clojure.string :as string]
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
        {:keys [scheme server-name server-port]} (parse-url url)
        api-root (or server-url (openapi/base-url definition))
        base-url (if (and (openapi-schema? definition) (not (string/starts-with? api-root "/")))
                   api-root
                   (format "%s://%s%s%s" (name scheme) server-name (if server-port (str ":" server-port) "")
                           (if (openapi-schema? definition)
                             api-root
                             (get definition :basePath ""))))]
    (martian/bootstrap-openapi base-url definition (merge default-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
