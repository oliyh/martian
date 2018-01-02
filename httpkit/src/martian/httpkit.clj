(ns martian.httpkit
  (:require [org.httpkit.client :as http]
            [martian.core :as martian]
            [cheshire.core :as json]
            [cognitect.transit :as transit]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [tripod.context :as tc])
  (:import [java.net URL]
           [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn- parse-url
  "Parse a URL string into a map of interesting parts. Lifted from clj-http."
  [url]
  (let [url-parsed (URL. url)]
    {:scheme (keyword (.getProtocol url-parsed))
     :server-name (.getHost url-parsed)
     :server-port (let [port (.getPort url-parsed)]
                    (when (pos? port) port))}))

(defn transit-decode [bytes type]
  (transit/read (transit/reader (ByteArrayInputStream. bytes) type)))

(defn transit-encode [body type]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out type)]
    (transit/write writer body)
    (io/input-stream (.toByteArray out))))

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
                               "application/transit+json" #(transit-encode % :json)
                               "application/transit+msgpack" #(transit-encode % :msgpack)
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
                 :always (assoc-in [:request :as] :text)
                 (= "application/transit+msgpack" content-type) (assoc-in [:request :as] :byte-array))

               (assoc-in ctx [:request :as] :auto)))

    :leave (fn [{:keys [request response handler] :as ctx}]
             (assoc ctx :response
                    (if-let [content-type (and (:body response)
                                               (not-empty (get-in response [:headers :content-type])))]
                      (update response :body
                              (condp re-find content-type
                                #"application/json" #(json/decode % key-fn)
                                #"application/edn" edn/read-string
                                #"application/transit\+json" #(transit-decode (.getBytes ^String %) :json)
                                #"application/transit\+msgpack" #(transit-decode % :msgpack)
                                identity))
                      response)))}))

(def default-coerce-response (coerce-response))

(defn- go-async [ctx]
  (-> ctx tc/terminate (dissoc ::tc/stack)))

(def perform-request
  {:name ::perform-request
   :leave (fn [{:keys [request] :as ctx}]
            (-> ctx
                go-async
                (assoc :response
                       (http/request request
                                     (fn [response]
                                       (:response (tc/execute (assoc ctx :response response))))))))})

(def default-interceptors
  (concat martian/default-interceptors [encode-body default-coerce-response perform-request]))

(defn bootstrap [api-root concise-handlers & [opts]]
  (martian/bootstrap api-root concise-handlers (merge {:interceptors default-interceptors} opts)))

(defn bootstrap-swagger [url & [{:keys [interceptors] :as params}]]
  (let [swagger-definition @(http/get url {:as :text} (fn [{:keys [body]}] (json/decode body keyword)))
        {:keys [scheme server-name server-port]} (parse-url url)
        base-url (format "%s://%s%s%s" (name scheme) server-name (if server-port (str ":" server-port) "") (get swagger-definition :basePath ""))]
    (martian/bootstrap-swagger
     base-url
     swagger-definition
     {:interceptors (or interceptors default-interceptors)})))
