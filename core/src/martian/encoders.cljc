(ns martian.encoders
  (:require [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [cognitect.transit :as transit]
            [flatland.ordered.map :refer [ordered-map]]
            #?(:clj [cheshire.core :as json])
            #?(:clj [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            #?(:clj [clojure.java.io :as io])
            #?(:clj [martian.multipart :as multipart])
            #?@(:bb []
                :clj [[ring.util.codec :as codec]]))
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

#?(:cljs
   (defn- form-encode [body]
     (str (js/URLSearchParams. (clj->js body)))))

#?(:cljs
   (defn- form-decode [body]
     (let [params (js/URLSearchParams. body)]
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
     ;; NB: This one must go first and stay separate from the following `:clj/cljs` one
     ;;     so to be overridden by the `:clj`-specific value on the JVM.
     #?@(:bb
         ["application/transit+json" {:encode #(transit-encode % :json)
                                      :decode #(transit-decode % :json)}])
     #?@(:clj
         ["application/transit+msgpack" {:encode #(transit-encode % :msgpack)
                                         :decode #(transit-decode % :msgpack)
                                         :as :byte-array}
          "application/transit+json" {:encode #(transit-encode % :json)
                                      :decode #(transit-decode (.getBytes ^String %) :json)}]
         :cljs
         ["application/transit+json" {:encode #(transit-encode % :json)
                                      :decode #(transit-decode % :json)}])
     "application/edn"  {:encode pr-str
                         :decode edn/read-string}
     "application/json" {:encode json-encode
                         :decode #(json-decode % key-fn)}
     #?@(:bb []
         :clj
         ["application/x-www-form-urlencoded" {:encode codec/form-encode
                                               :decode (comp keywordize-keys codec/form-decode)}]
         :cljs
         ["application/x-www-form-urlencoded" {:encode form-encode
                                               :decode form-decode}]))))
