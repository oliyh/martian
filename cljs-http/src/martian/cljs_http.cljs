(ns martian.cljs-http
  (:require [cljs-http.client :as http-client]
            [cljs-http.core :as http]
            [cljs.core.async :refer [<!]]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [tripod.context :as tc]
            [clojure.string :as string])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^:private go-async
  i/remove-stack)

;; NB: The `cljs-http` by default decodes EDN, JSON, and Transit JSON via mws.
;;     Martian does the same and more via the `::coerce-response` interceptor.
(defn wrap-request
  [request]
  (-> request
      http-client/wrap-accept
      http-client/wrap-form-params
      http-client/wrap-multipart-params
      http-client/wrap-edn-params
      #_http-client/wrap-edn-response
      http-client/wrap-transit-params
      #_http-client/wrap-transit-response
      http-client/wrap-json-params
      #_http-client/wrap-json-response
      http-client/wrap-content-type
      http-client/wrap-query-params
      http-client/wrap-basic-auth
      http-client/wrap-oauth
      http-client/wrap-method
      http-client/wrap-url
      http-client/wrap-channel-from-request-map
      http-client/wrap-default-headers))

(def make-request! (wrap-request http/request))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                go-async
                (assoc :response
                       (go (let [response (<! (make-request! request))]
                             (:response (tc/execute (assoc ctx :response response))))))))})

(def default-interceptors
  (conj martian/default-interceptors
        i/default-encode-body
        (i/coerce-response (encoders/default-encoders)
                           {:request-key :response-type
                            :missing-encoder-as :default
                            :default-encoder-as :default})
        perform-request))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [{:keys [server-url trim-base-url?] :as opts} load-opts]]
  (go (let [definition (:body (<! (http-client/get url load-opts)))
            raw-base-url (openapi/base-url url server-url definition)
            base-url (if trim-base-url?
                       (string/replace raw-base-url #"/$" "")
                       raw-base-url)]
        (martian/bootstrap-openapi base-url definition (merge default-opts opts)))))

(def bootstrap-swagger bootstrap-openapi)
