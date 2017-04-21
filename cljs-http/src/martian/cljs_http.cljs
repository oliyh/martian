(ns martian.cljs-http
  (:require [cljs-http.client :as http]
            [cljs-http.util :as util]
            [cljs.core.async :refer [<!]]
            [martian.core :as martian]
            [cljs.reader :refer [read-string]]
            [tripod.context :as tc])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def supported-encodings
  ["application/transit+json"
   "application/edn"
   "application/json"])

(defn- choose-content-type [options]
  (some (set options) supported-encodings))

(def encode-body
  {:name ::encode-body
   :enter (fn [{:keys [request handler] :as ctx}]
            (if-let [content-type (and (:body request)
                                       (not (get-in request [:headers "Content-Type"]))
                                       (choose-content-type (:consumes handler)))]
              (-> ctx
                  (update-in [:request :body]
                             (condp = content-type
                               "application/json" util/json-encode
                               "application/edn" pr-str
                               "application/transit+json" #(util/transit-encode % :json {})
                               identity))
                  (assoc-in [:request :headers "Content-Type"] content-type))
              ctx))})

(def coerce-response
  {:name ::coerce-response
   :enter (fn [{:keys [request handler] :as ctx}]
            (if-let [content-type (and (not (get-in request [:headers "Accept"]))
                                       (choose-content-type (:produces handler)))]
              (assoc-in ctx [:request :headers "Accept"] content-type)
              (assoc-in ctx [:request :as] :auto)))

   :leave (fn [{:keys [request response handler] :as ctx}]
            (assoc ctx :response
                   (if-let [content-type (and (:body response)
                                              (not= :auto (:as request))
                                              (not-empty (get-in response [:headers "Content-Type"])))]
                     (update response :body
                             (condp re-find content-type
                               #"application/json" util/json-decode
                               #"application/edn" read-string
                               #"application/transit\+json" #(util/transit-decode % :json {})
                               identity))
                     response)))})

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
  (concat martian/default-interceptors [encode-body coerce-response perform-request]))

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
