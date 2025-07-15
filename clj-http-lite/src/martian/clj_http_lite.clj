(ns martian.clj-http-lite
  (:require [cheshire.core :as json]
            [clj-http.lite.client :as http]
            [martian.core :as martian]
            [martian.file :as file]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [martian.yaml :as yaml]))

(defn- prepare-response-headers [headers]
  (reduce (fn [m [k v]] (assoc m (keyword k) v)) {} headers))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (-> (http/request request)
                                     ;; convert keys to keywords
                                     (update-in [:headers] prepare-response-headers))))})

(def default-interceptors
  (conj martian/default-interceptors
        ;; `clj-http-lite` does not support 'multipart/form-data' uploads
        i/default-encode-request
        ;; `clj-http-lite` does not support "Content-Type"-based coercion
        i/default-coerce-response
        perform-request))

(defn build-custom-opts [{:keys [request-encoders response-encoders]}]
  {:interceptors (cond-> default-interceptors

                         request-encoders
                         (i/inject (i/encode-request request-encoders)
                                   :replace ::i/encode-request)

                         response-encoders
                         (i/inject (i/coerce-response response-encoders)
                                   :replace ::i/coerce-response))})

(def default-opts {:interceptors default-interceptors})

(def ^:private supported-opts
  [:request-encoders :response-encoders])

(defn prepare-opts [opts]
  (if (and (seq opts)
           (some (set (keys opts)) supported-opts))
    (merge (build-custom-opts opts)
           (apply dissoc opts supported-opts))
    default-opts))

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (prepare-opts opts)))

(defn- load-definition [url load-opts]
  (or (file/local-resource url)
      (let [body (:body (http/get url (or load-opts {})))]
        (if (yaml/yaml-url? url)
          (yaml/yaml->edn body)
          ;; `clj-http-lite` has no support for `:json` response coercion
          (json/parse-string body keyword)))))

(defn bootstrap-openapi [url & [{:keys [server-url] :as opts} load-opts]]
  (let [definition (load-definition url load-opts)
        base-url (openapi/base-url url server-url definition)]
    (martian/bootstrap-openapi base-url definition (prepare-opts opts))))

(def bootstrap-swagger bootstrap-openapi)
