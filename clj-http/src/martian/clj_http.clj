(ns martian.clj-http
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [cognitect.transit :as transit]
            [martian.core :as martian])
  (:import [java.io ByteArrayInputStream]))

(def supported-encodings
  ["application/transit+msgpack"
   "application/transit+json"
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
                               "application/json" json/encode
                               "application/edn" pr-str
                               "application/transit+json" #(http/transit-encode % :json)
                               "application/transit+msgpack" #(http/transit-encode % :msgpack)
                               identity))
                  (assoc-in [:request :headers "Content-Type"] content-type))
              ctx))})

(defn coerce-response
  ([] (coerce-response {}))
  ([{:keys [key-fn] :or {key-fn keyword}}]
   {:name ::coerce-response
    :enter (fn [{:keys [request handler] :as ctx}]
             (if-let [content-type (and (not (get-in request [:headers "Accept"]))
                                        (choose-content-type (:produces handler)))]
               (cond-> (assoc-in ctx [:request :headers "Accept"] content-type)
                 (= "application/transit+msgpack" content-type) (assoc-in [:request :as] :byte-array))

               (assoc-in ctx [:request :as] :auto)))

    :leave (fn [{:keys [request response handler] :as ctx}]
             (if-let [content-type (and (:body response)
                                        (not= :auto (:as request))
                                        (not-empty (get-in response [:headers "Content-Type"])))]
               (update-in ctx [:response :body]
                          (condp re-find content-type
                            #"application/json" #(json/decode % key-fn)
                            #"application/edn" http/parse-edn
                            #"application/transit\+json" #(http/parse-transit (ByteArrayInputStream. (.getBytes %)) :json)
                            #"application/transit\+msgpack" #(http/parse-transit (ByteArrayInputStream. %) :msgpack)
                            identity))
               ctx))}))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (assoc ctx :response (http/request request)))})

(def default-interceptors
  (concat martian/default-interceptors [encode-body (coerce-response) perform-request]))

(defn bootstrap-swagger [url & [{:keys [interceptors] :as params}]]
  (let [swagger-definition (:body (http/get url {:as :json}))
        {:keys [scheme server-name server-port]} (http/parse-url url)
        base-url (format "%s://%s%s%s" (name scheme) server-name (if server-port (str ":" server-port) "") (get swagger-definition :basePath ""))]
    (martian/bootstrap-swagger
     base-url
     swagger-definition
     {:interceptors (or interceptors default-interceptors)})))
