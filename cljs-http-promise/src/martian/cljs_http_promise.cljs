(ns martian.cljs-http-promise
  (:require [cljs-http.client :as http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [tripod.context :as tc]
            [clojure.string :as string]
            [promesa.core :as prom]))

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

;; NB: The `cljs-http` by default decodes EDN, JSON, and Transit JSON via mws.
(def response-encoders
  (dissoc (encoders/default-encoders)
          "application/edn"
          "application/json"
          "application/transit+json"))

(def default-interceptors
  (conj martian/default-interceptors
        i/default-encode-body
        (i/coerce-response response-encoders
                           {:request-key :response-type
                            :missing-encoder-as :default
                            :default-encoder-as :default})
        perform-request))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [{:keys [server-url trim-base-url?] :as opts} load-opts]]
  (prom/then (http/get url (merge {:as :json} load-opts))
             (fn [response]
               (let [definition (:body response)
                     raw-base-url (openapi/base-url url server-url definition)
                     base-url (if trim-base-url?
                                (string/replace raw-base-url #"/$" "")
                                raw-base-url)]
                 (martian/bootstrap-openapi base-url definition (merge default-opts opts))))))

(def bootstrap-swagger bootstrap-openapi)
