(ns martian.httpkit
  (:require [org.httpkit.client :as http]
            [martian.core :as martian]
            [cheshire.core :as json])
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
   :enter (fn [{:keys [request] :as ctx}]
            (println request)
            (assoc ctx :response (http/request
                                  (-> {:as :auto}
                                      (merge request)
                                      (dissoc :params)))))})

(defn bootstrap-swagger [url & [params]]
  (let [swagger-definition @(http/get url (merge params {:as :text})
                                      (fn [{:keys [body]}]
                                        (json/decode body keyword)))
        {:keys [scheme server-name server-port]} (parse-url url)
        base-url (format "%s://%s%s%s" (name scheme) server-name (if server-port (str ":" server-port) "") (get swagger-definition :basePath ""))]
    (martian/bootstrap-swagger base-url swagger-definition {:interceptors [perform-request]})))
