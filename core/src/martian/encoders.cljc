(ns martian.encoders
  (:require [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [cognitect.transit :as transit]
            [flatland.ordered.map :refer [ordered-map]]
            #?(:clj [cheshire.core :as json])
            #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            #?(:clj [clojure.java.io :as io])
            #?(:clj [martian.multipart :as multipart])
            #?@(:bb  []
                :clj [[ring.util.codec :as codec]]))
  #?(:clj (:import [java.io ByteArrayInputStream ByteArrayOutputStream InputStream])))

#?(:clj
   (defn as-bytes [obj]
     (cond
       (bytes? obj) obj

       (string? obj)
       (.getBytes ^String obj)

       (instance? InputStream obj)
       (with-open [^InputStream is obj, baos (ByteArrayOutputStream.)]
         (io/copy is baos)
         (.toByteArray baos))

       ;; Leave as is and let it fail downstream
       :else obj)))

#?(:clj
   (defn as-stream [obj]
     (if (instance? InputStream obj)
       obj
       (ByteArrayInputStream. (as-bytes obj)))))

#?(:clj
   (defn as-string
     ([obj]
      (if (string? obj)
        obj
        (as-string obj "UTF-8")))
     ([obj ^String charset]
      (if (string? obj)
        obj
        (slurp obj :encoding charset)))))

(defn transit-encode [body type]
  #?(:clj  (let [out (ByteArrayOutputStream. 4096)
                 writer (transit/writer out type)]
             (transit/write writer body)
             ;; TODO: Is it necessary to wrap into stream?
             (io/input-stream (.toByteArray out)))
     :cljs (transit/write (transit/writer type {}) body)))

(defn transit-decode [body type]
  #?(:clj  (transit/read (transit/reader (as-stream body) type))
     :cljs (transit/read (transit/reader type {}) body)))

(defn json-encode [body]
  #?(:clj  (json/encode body)
     :cljs (js/JSON.stringify (clj->js body))))

(defn json-decode [body key-fn]
  #?(:clj  (json/decode body key-fn)
     :cljs (when-let [v (if-not (string/blank? body) (js/JSON.parse body))]
             (js->clj v :keywordize-keys key-fn))))

#?(:clj
   (defn multipart-encode
     ([body]
      (mapv (fn [[k v]]
              {:name (name k) :content (multipart/coerce-content v)})
            body))
     ([body pass-pred]
      (mapv (fn [[k v]]
              {:name (name k) :content (multipart/coerce-content v pass-pred)})
            body))))

(defn form-encode [body]
  #?(:bb   nil
     :clj  (codec/form-encode body)
     :cljs (str (js/URLSearchParams. (clj->js body)))))

(defn form-decode [body]
  #?(:bb   nil
     :clj  (keywordize-keys (codec/form-decode (as-string body)))
     :cljs (let [params (js/URLSearchParams. body)]
             (reduce (fn [acc k]
                       (let [v (.getAll params k)]
                         (assoc acc (keyword k) (if (= 1 (count v))
                                                  (first v)
                                                  (vec v)))))
                     {}
                     (.keys params)))))

(defn default-encoders
  ([] (default-encoders keyword))
  ([key-fn]
   ;; NB: The order in this map is critically important, since we choose an appropriate
   ;;     encoder for a particular media type sequentially (see `martian.encoding` ns).
   (ordered-map
     ;; NB: The `transit+msgpack` is not available when running in BB, but is on the JVM.
     ;;     j.l.NoClassDefFoundError: Could not initialize class org.msgpack.MessagePack
     #?@(:bb []
         :clj
         ["application/transit+msgpack" {:encode #(transit-encode % :msgpack)
                                         :decode #(transit-decode % :msgpack)
                                         :as :byte-array}])
     "application/transit+json" {:encode #(transit-encode % :json)
                                 :decode #(transit-decode % :json)}
     "application/edn"  {:encode pr-str
                         :decode edn/read-string}
     "application/json" {:encode json-encode
                         :decode #(json-decode % key-fn)}
     #?@(:bb []
         :clj
         ["application/x-www-form-urlencoded" {:encode form-encode
                                               :decode form-decode}]
         :cljs
         ["application/x-www-form-urlencoded" {:encode form-encode
                                               :decode form-decode
                                               :as :text}]))))
