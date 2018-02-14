(ns martian.encoding
  (:require [cheshire.core :as json]
            [cognitect.transit :as transit]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [linked.core :as linked]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util.regex Pattern]))

(defn transit-decode [bytes type]
  (transit/read (transit/reader (ByteArrayInputStream. bytes) type)))

(defn transit-encode [body type]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out type)]
    (transit/write writer body)
    (io/input-stream (.toByteArray out))))

(defn choose-content-type [encoders options]
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

(defn compile-encoder-matches [encoders]
  (reduce-kv (fn [acc content-type encoder]
               (assoc-in acc [content-type :matcher] (re-pattern (Pattern/quote content-type))))
             encoders
             encoders))

(defn find-encoder [encoders content-type]
  (if (string/blank? content-type)
    auto-encoder
    (loop [encoders (vals encoders)]
      (let [{:keys [matcher] :as encoder} (first encoders)]
        (cond
          (not encoder) auto-encoder

          (re-find matcher content-type) encoder

          :else
          (recur (rest encoders)))))))
