(ns martian.cljs-http
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [martian.core :as martian]
            [martian.interceptors :as i]
            [martian.openapi :as openapi]
            [tripod.context :as tc]
            [clojure.string :as string])
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

(def default-interceptors
  (concat martian/default-interceptors [i/default-encode-body i/default-coerce-response perform-request]))

(def default-opts {:interceptors default-interceptors})

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge default-opts opts)))

(defn bootstrap-openapi [url & [{:keys [server-url trim-base-url?] :as opts} load-opts]]
  (go (let [definition (:body (<! (http/get url (merge {:as :json} load-opts))))
            raw-base-url (openapi/base-url url server-url definition)
            base-url (if trim-base-url?
                       (string/replace raw-base-url #"/$" "")
                       raw-base-url)]
        (martian/bootstrap-openapi base-url definition (merge default-opts opts)))))

(def bootstrap-swagger bootstrap-openapi)
