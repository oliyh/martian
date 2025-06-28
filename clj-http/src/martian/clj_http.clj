(ns martian.clj-http
  (:require [clj-http.client :as http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.file :as file]
            [martian.interceptors :as interceptors]
            [martian.openapi :as openapi]
            [martian.yaml :as yaml])
  (:import (org.apache.http.entity.mime.content ContentBody)))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (http/request request)))})

(defn custom-type? [obj]
  (instance? ContentBody obj))

(def request-encoders
  (assoc (encoders/default-encoders)
    "multipart/form-data" {:encode #(encoders/multipart-encode % custom-type?)}))

;; TODO: This actually doesn't work for some reason... Double-check and fix!
;; NB: In accordance with the `clj-http`'s Optional Dependencies which happen
;;     to be on the classpath already as the Martian core module dependencies,
;;     we should skip decoding some media types.
;;     https://github.com/dakrone/clj-http#optional-dependencies
(def response-coerce-opts
  {:skip-decode #{"application/edn"
                  "application/json"
                  "application/transit+json"
                  "application/transit+msgpack"
                  "application/x-www-form-urlencoded"}})

(def default-interceptors
  (conj martian/default-interceptors
        (interceptors/encode-request request-encoders)
        (interceptors/coerce-response (encoders/default-encoders) response-coerce-opts)
        perform-request))

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
