(ns martian.cljs-http
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [martian.core :as martian]
            [martian.interceptors :as i]
            [tripod.context :as tc]
            [clojure.string :as str])
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

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge {:interceptors default-interceptors} opts)))

(defn bootstrap-swagger [url & [{:keys [interceptors trim-base-url?] :as params}]]
  (go (let [swagger-definition (:body (<! (http/get url {:as :json})))
            {:keys [scheme server-name server-port]} (http/parse-url url)
            raw-base-url (str (when-not (re-find #"^/" url)
                                (str (name scheme) "://" server-name (when server-port (str ":" server-port))))
                              (get swagger-definition :basePath ""))
            base-url (if trim-base-url?
                       (str/replace raw-base-url #"/$" "")
                       raw-base-url)]
        (martian/bootstrap-swagger
         base-url
         swagger-definition
         {:interceptors (or interceptors default-interceptors)}))))
