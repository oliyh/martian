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

;; NB: In accordance with the `clj-http`'s Optional Dependencies, which happen
;;     to be on the classpath already as the Martian core module dependencies,
;;     we could (or, at the very least, should allow to) skip Martian response
;;     decoding for those media types.
;;     https://github.com/dakrone/clj-http#optional-dependencies
(defn response-coerce-opts [use-client-output-coercion?]
  (if use-client-output-coercion?
    {:skip-decoding-for (cond-> #{"application/json"
                                  "application/transit+json"
                                  "application/transit+msgpack"
                                  "application/x-www-form-urlencoded"}
                                ;; NB: This one may not be available to the end user!
                                http/edn-enabled? (conj "application/edn"))
     :default-encoder-as :auto}
    {:default-encoder-as :string}))

(defn build-default-interceptors [use-client-output-coercion?]
  (conj martian/default-interceptors
        (interceptors/encode-request request-encoders)
        (interceptors/coerce-response (encoders/default-encoders)
                                      (response-coerce-opts use-client-output-coercion?))
        perform-request))

(defn build-default-opts [use-client-output-coercion?]
  {:interceptors (build-default-interceptors use-client-output-coercion?)})

(def default-interceptors (build-default-interceptors false))

(def default-opts {:interceptors default-interceptors})

(defn prepare-opts [{:keys [use-client-output-coercion?] :as opts}]
  (merge (if (some? use-client-output-coercion?)
           (build-default-opts use-client-output-coercion?)
           default-opts)
         (dissoc opts :use-client-output-coercion?)))

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
