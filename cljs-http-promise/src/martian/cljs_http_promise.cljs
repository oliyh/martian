(ns martian.cljs-http-promise
  (:require [cljs-http.client :as http]
            [clojure.string :as str]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.http-clients :as hc]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [promesa.core :as prom]
            [tripod.context :as tc]))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                hc/go-async
                (assoc :response
                       (prom/then (http/request request)
                                  (fn [response]
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

(defn build-custom-opts [opts]
  {:interceptors (hc/update-basic-interceptors
                   default-interceptors
                   (conj {:response-coerce-opts response-coerce-opts} opts))})

(def default-opts {:interceptors default-interceptors})

(def ^:private supported-opts
  [:request-encoders :response-encoders])

(defn prepare-opts [opts]
  (hc/prepare-opts build-custom-opts supported-opts default-opts opts))

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (prepare-opts opts)))

(defn bootstrap-openapi [url & [{:keys [server-url trim-base-url?] :as opts} load-opts]]
  (prom/then (http/get url load-opts)
             (fn [response]
               (let [definition (:body response)
                     raw-base-url (openapi/base-url url server-url definition)
                     base-url (if trim-base-url?
                                (str/replace raw-base-url #"/$" "")
                                raw-base-url)]
                 (martian/bootstrap-openapi base-url definition (prepare-opts opts))))))

(def bootstrap-swagger bootstrap-openapi)
