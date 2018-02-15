(ns martian.cljs-http
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [martian.core :as martian]
            [martian.interceptors :as i]
            [tripod.context :as tc])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- go-async [ctx]
  (-> ctx tc/terminate (dissoc ::tc/stack)))

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

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge {:interceptors default-interceptors} opts)))

(defn bootstrap-swagger [url & [{:keys [interceptors] :as params}]]
  (go (let [swagger-definition (:body (<! (http/get url {:as :json})))
            {:keys [scheme server-name server-port]} (http/parse-url url)
            base-url (str (when-not (re-find #"^/" url)
                            (str (name scheme) "://" server-name (when server-port (str ":" server-port))))
                          (get swagger-definition :basePath ""))]
        (martian/bootstrap-swagger
         base-url
         swagger-definition
         {:interceptors (or interceptors default-interceptors)}))))
