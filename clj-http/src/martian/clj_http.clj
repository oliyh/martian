(ns martian.clj-http
  (:require [clj-http.client :as http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.file :as file]
            [martian.http-clients :as hc]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [martian.yaml :as yaml])
  (:import (org.apache.http.entity.mime.content ContentBody)))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (http/request request)))})

(defn custom-type? [obj]
  (instance? ContentBody obj))

(def default-request-encoders
  (assoc (encoders/default-encoders)
    "multipart/form-data" {:encode #(encoders/multipart-encode % custom-type?)}))

(def default-response-encoders
  (encoders/default-encoders))

;; NB: In accordance with the `clj-http`'s Optional Dependencies, which happen
;;     to be on the classpath already as the Martian core module dependencies,
;;     we could (or, at the very least, should allow to) skip Martian response
;;     decoding for those media types.
;;     https://github.com/dakrone/clj-http#optional-dependencies
(defn get-response-coerce-opts [use-client-output-coercion?]
  (conj {:auto-coercion-pred #{:auto}}
        (if use-client-output-coercion?
          {:skip-decoding-for (cond-> #{"application/json"
                                        "application/transit+json"
                                        "application/transit+msgpack"
                                        "application/x-www-form-urlencoded"}
                                      ;; NB: This one may not be available to the end user!
                                      http/edn-enabled? (conj "application/edn"))
           :default-encoder-as :auto}
          {:default-encoder-as :string})))

(def default-interceptors
  (conj martian/default-interceptors
        (i/encode-request default-request-encoders)
        (i/coerce-response default-response-encoders (get-response-coerce-opts false))
        perform-request))

(def supported-custom-opts
  [:request-encoders :response-encoders :use-client-output-coercion?])

(defn build-custom-opts [{:keys [use-client-output-coercion?] :as opts}]
  (let [response-coerce-opts (get-response-coerce-opts use-client-output-coercion?)]
    {:interceptors (hc/update-basic-interceptors
                     default-interceptors
                     (conj {:response-encoders default-response-encoders
                            :response-coerce-opts response-coerce-opts}
                           opts))}))

(def default-opts {:interceptors default-interceptors})

(defn prepare-opts [opts]
  (hc/prepare-opts build-custom-opts supported-custom-opts default-opts opts))

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
