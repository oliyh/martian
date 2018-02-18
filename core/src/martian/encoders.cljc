(ns martian.encoders
  (:require [clojure.string :as string]
            [linked.core :as linked]
            [cognitect.transit :as transit]
            #?(:clj [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            #?(:clj [cheshire.core :as json])
            #?(:clj [clojure.java.io :as io]))
  #?(:clj (:import [java.io ByteArrayInputStream ByteArrayOutputStream])))

(defn transit-decode [body type]
  #?(:clj (transit/read (transit/reader (ByteArrayInputStream. body) type))
     :cljs (transit/read (transit/reader type {}) body)))

(defn transit-encode [body type]
  #?(:clj
     (let [out (ByteArrayOutputStream. 4096)
           writer (transit/writer out type)]
       (transit/write writer body)
       (io/input-stream (.toByteArray out)))

     :cljs
     (transit/write (transit/writer type {}) body)))

(defn json-encode [body]
  #?(:clj (json/encode body)
     :cljs (js/JSON.stringify (clj->js body))))

(defn json-decode [body key-fn]
  #?(:clj (json/decode body key-fn)
     :cljs
     (if-let [v (if-not (string/blank? body) (js/JSON.parse body))]
       (js->clj v :keywordize-keys key-fn))))

(defn default-encoders
  ([] (default-encoders keyword))
  ([key-fn]
   (merge
    #?(:clj
       {"application/transit+msgpack" {:encode #(transit-encode % :msgpack)
                                       :decode #(transit-decode % :msgpack)
                                       :as :byte-array}
        "application/transit+json"    {:encode #(transit-encode % :json)
                                       :decode #(transit-decode (.getBytes ^String %) :json)}})
    #?(:cljs
       {"application/transit+json"    {:encode #(transit-encode % :json)
                                       :decode #(transit-decode % :json)}})
    (linked/map

     "application/edn"             {:encode pr-str
                                    :decode edn/read-string}
     "application/json"            {:encode json-encode
                                    :decode #(json-decode % key-fn)}))))
