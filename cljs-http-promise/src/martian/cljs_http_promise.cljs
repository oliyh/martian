(ns martian.cljs-http-promise
  (:require [cljs-http.client :as http]
            [clojure.string :as str]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [promesa.core :as prom]
            [tripod.context :as tc]))

(def ^:private go-async
  i/remove-stack)

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                go-async
                (assoc :response
                       (prom/then (http/request request)
                                  (fn [response]
                                    (:response (tc/execute (assoc ctx :response response))))))))})

(def response-encoders
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
        (i/coerce-response response-encoders response-coerce-opts)
        perform-request))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [{:keys [server-url trim-base-url?] :as opts} load-opts]]
  (prom/then (http/get url load-opts)
             (fn [response]
               (let [definition (:body response)
                     raw-base-url (openapi/base-url url server-url definition)
                     base-url (if trim-base-url?
                                (str/replace raw-base-url #"/$" "")
                                raw-base-url)]
                 (martian/bootstrap-openapi base-url definition (merge default-opts opts))))))

(def bootstrap-swagger bootstrap-openapi)
