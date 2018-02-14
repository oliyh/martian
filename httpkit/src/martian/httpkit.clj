(ns martian.httpkit
  (:require [org.httpkit.client :as http]
            [martian.core :as martian]
            [cheshire.core :as json]
            [cognitect.transit :as transit]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [tripod.context :as tc]
            [clojure.string :as string]
            [linked.core :as linked])
  (:import [java.net URL]
           [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util.regex Pattern]))

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

(defn- choose-content-type [encoders options]
  (some (set options) (keys encoders)))

(def auto-encoder
  {:encode identity
   :decode identity
   :as :auto})

(defn default-encoders
  ([] (default-encoders keyword))
  ([key-fn]
   (linked/map
    "application/transit+msgpack" {:encode #(transit-encode % :msgpack)
                                   :decode #(transit-decode % :msgpack)
                                   :as :byte-array}
    "application/transit+json"    {:encode #(transit-encode % :json)
                                   :decode #(transit-decode (.getBytes ^String %) :json)}
    "application/edn"             {:encode pr-str
                                   :decode edn/read-string}
    "application/json"            {:encode json/encode
                                   :decode #(json/decode % key-fn)})))

(defn- compile-encoder-matches [encoders]
  (reduce-kv (fn [acc content-type encoder]
               (assoc-in acc [content-type :matcher] (re-pattern (Pattern/quote content-type))))
             encoders
             encoders))

(defn- find-encoder [encoders content-type]
  (if (string/blank? content-type)
    auto-encoder
    (loop [encoders (vals encoders)]
      (let [{:keys [matcher] :as encoder} (first encoders)]
        (cond
          (not encoder) auto-encoder

          (re-find matcher content-type) encoder

          :else
          (recur (rest encoders)))))))

(defn encode-body
  ([] (encode-body (default-encoders)))
  ([encoders]
   (let [encoders (compile-encoder-matches encoders)]
     {:name ::encode-body
      :enter (fn [{:keys [request handler] :as ctx}]
               (let [content-type (and (:body request)
                                       (not (get-in request [:headers "Content-Type"]))
                                       (choose-content-type encoders (:consumes handler)))
                     {:keys [encode] :as encoder} (find-encoder encoders content-type)]
                 (cond-> ctx
                     (get-in ctx [:request :body]) (update-in [:request :body] encode)
                     content-type (assoc-in [:request :headers "Content-Type"] content-type))))})))

(def default-encode-body (encode-body))

(defn coerce-response
  ([] (coerce-response (default-encoders)))
  ([encoders]
   (let [encoders (compile-encoder-matches encoders)]
     {:name ::coerce-response
      :enter (fn [{:keys [request handler] :as ctx}]
               (let [content-type (and (not (get-in request [:headers "Accept"]))
                                       (choose-content-type encoders (:produces handler)))
                     {:keys [as] :or {as :text}} (find-encoder encoders content-type)]

                 (-> ctx
                     (assoc-in [:request :headers "Accept"] content-type)
                     (assoc-in [:request :as] as))))

      :leave (fn [{:keys [request response handler] :as ctx}]
               (assoc ctx :response
                      (let [content-type (and (:body response)
                                              (not-empty (get-in response [:headers :content-type])))
                            {:keys [matcher decode] :as encoder} (find-encoder encoders content-type)]
                        (update response :body decode))))})))

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
  (concat martian/default-interceptors [default-encode-body default-coerce-response perform-request]))

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
