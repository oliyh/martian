(ns martian.cljs-http
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.http-clients :as hc]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [tripod.context :as tc])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                hc/go-async
                (assoc :response
                       (go (let [response (<! (http/request request))]
                             (:response (tc/execute (assoc ctx :response response))))))))})

(def default-response-encoders
  (encoders/default-encoders))

(def response-coerce-opts
  {:skip-decoding-for #{"application/edn"
                        "application/json"
                        "application/transit+json"}
   :request-key :response-type
   :missing-encoder-as :default
   ;; NB: This must not be `:text`, since this previous global default value
   ;;     never actually affected `cljs-http` response coercion due to being
   ;;     passed under the wrong request key, `:as`.
   :default-encoder-as :default})

(def default-interceptors
  (conj martian/default-interceptors
        i/default-encode-request
        (i/coerce-response default-response-encoders response-coerce-opts)
        perform-request))

(def supported-custom-opts
  [:request-encoders :response-encoders])

(defn build-custom-opts [opts]
  {:interceptors (hc/update-basic-interceptors
                   default-interceptors
                   (conj {:response-encoders default-response-encoders
                          :response-coerce-opts response-coerce-opts}
                         opts))})

(def default-opts {:interceptors default-interceptors})

(defn prepare-opts [opts]
  (hc/prepare-opts build-custom-opts supported-custom-opts default-opts opts))

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (prepare-opts opts)))

(defn bootstrap-openapi [url & [{:keys [server-url trim-base-url?] :as opts} load-opts]]
  (go (let [definition (:body (<! (http/get url load-opts)))
            raw-base-url (openapi/base-url url server-url definition)
            base-url (if trim-base-url?
                       (str/replace raw-base-url #"/$" "")
                       raw-base-url)]
        (martian/bootstrap-openapi base-url definition (prepare-opts opts)))))

(def bootstrap-swagger bootstrap-openapi)
