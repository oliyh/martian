(ns martian.cljs-http
  (:require [cljs-http.client :as http]
            [cljs-http.util :as util]
            [cljs.core.async :refer [<!]]
            [martian.core :as martian]
            [cljs.reader :refer [read-string]])
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
                                       (choose-content-type (get-in handler [:swagger-definition :consumes])))]
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
                                       (choose-content-type (get-in handler [:swagger-definition :produces])))]
              (cond-> (assoc-in ctx [:request :headers "Accept"] content-type)
                (= "application/transit+msgpack" content-type) (assoc-in [:request :as] :byte-array))

              (assoc-in ctx [:request :as] :auto)))

   :leave (fn [{:keys [request response handler] :as ctx}]
            (assoc ctx :response
                   (go
                     (let [response (<! response)]
                       (if-let [content-type (and (:body response)
                                                  (not= :auto (:as request))
                                                  (not-empty (get-in response [:headers "Content-Type"])))]
                         (update response :body
                                 (condp re-find content-type
                                   #"application/json" util/json-decode
                                   #"application/edn" read-string
                                   #"application/transit\+json" #(util/transit-decode % :json {})
                                   identity))
                         response)))))})

(def perform-request
  {:name ::perform-request
   :enter (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (http/request (-> request (dissoc :params)))))})

(defn bootstrap-swagger [url & [{:keys [interceptors] :as params}]]
  (go (let [swagger-definition (:body (<! (http/get url {:as :json})))
            {:keys [scheme server-name server-port]} (http/parse-url url)
            base-url (str (name scheme) "://" server-name (if server-port (str ":" server-port) "") (get swagger-definition :basePath ""))]
        (martian/bootstrap-swagger
         base-url
         swagger-definition
         {:interceptors (or interceptors (concat martian/default-interceptors [encode-body coerce-response perform-request]))}))))
