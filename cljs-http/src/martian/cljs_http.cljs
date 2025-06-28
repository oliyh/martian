(ns martian.cljs-http
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [tripod.context :as tc])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^:private go-async
  i/remove-stack)

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                go-async
                (assoc :response
                       (go (let [response (<! (http/request request))]
                             (:response (tc/execute (assoc ctx :response response))))))))})

(def response-coerce-opts
  {:skip-decode #{"application/edn"
                  "application/json"
                  "application/transit+json"}
   :request-key :response-type
   :missing-encoder-as :default
   :default-encoder-as :default})

(def default-interceptors
  (conj martian/default-interceptors
        i/default-encode-body
        (i/coerce-response (encoders/default-encoders) response-coerce-opts)
        perform-request))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [{:keys [server-url trim-base-url?] :as opts} load-opts]]
  (go (let [definition (:body (<! (http/get url load-opts)))
            raw-base-url (openapi/base-url url server-url definition)
            base-url (if trim-base-url?
                       (str/replace raw-base-url #"/$" "")
                       raw-base-url)]
        (martian/bootstrap-openapi base-url definition (merge default-opts opts)))))

(def bootstrap-swagger bootstrap-openapi)
