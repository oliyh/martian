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
  #?(:clj (:import [java.io ByteArrayInputStream ByteArrayOutputStream InputStream PushbackReader])))

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

(defn transit-encode
  ([body type]
   (transit-encode body type {}))
  ([body type opts]
   #?(:clj  (let [out (ByteArrayOutputStream. 4096)
                  writer (transit/writer out type opts)]
              (transit/write writer body)
              ;; TODO: Is it necessary to wrap into stream?
              (io/input-stream (.toByteArray out)))
      :cljs (transit/write (transit/writer type opts) body))))

(defn transit-decode
  ([body type]
   (transit-decode body type {}))
  ([body type opts]
   #?(:clj  (transit/read (transit/reader (as-stream body) type opts))
      :cljs (transit/read (transit/reader type opts) body))))

(defn json-encode
  ([body]
   (json-encode body nil))
  ([body {:keys [key-fn] :as opts}]
   #?(:clj  (json/generate-string body opts)
      :cljs (-> body
                (clj->js {:keyword-fn (or key-fn name)})
                (js/JSON.stringify)))))

(defn json-decode
  ([body]
   (json-decode body {:key-fn keyword}))
  ([body opts-or-fn]
   (let [{:keys [key-fn array-coerce-fn]} (if (fn? opts-or-fn)
                                            {:key-fn opts-or-fn}
                                            opts-or-fn)]
     #?(:clj  (json/parse-string body key-fn array-coerce-fn)
        :cljs (when (and (string? body) (not (string/blank? body)))
                (-> body
                    (js/JSON.parse)
                    (js->clj :keywordize-keys (boolean key-fn))))))))

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

(defn edn-encode
  ([body]
   (edn-encode body nil))
  ([body opts]
   ((if (:trailing-newline opts) prn-str pr-str) body)))

(defn edn-decode
  [body opts]
  (let [opts (merge {:eof nil} opts)]
    #?(:clj  (if (string? body)
               (edn/read-string opts body)
               (with-open [rdr (io/reader body)
                           pb-rdr (PushbackReader. rdr)]
                 (edn/read opts pb-rdr)))
       :cljs (edn/read-string opts body))))

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
  ([opts-or-fn]
   (let [opts (if (fn? opts-or-fn)
                {:json {:decode {:key-fn opts-or-fn}}}
                opts-or-fn)
         {transit-opts :transit, json-opts :json, edn-opts :edn} opts]
     ;; NB: The order in this map is critically important, since we choose an appropriate
     ;;     encoder for a particular media type sequentially (see `martian.encoding` ns),
     ;;     as well as preserve the order when collecting supported content types for the
     ;;     OpenAPI definition parsing.
     (ordered-map
       #?@(:bb []
           :clj
           ["application/transit+msgpack" {:encode #(transit-encode % :msgpack (:encode transit-opts))
                                           :decode #(transit-decode % :msgpack (:decode transit-opts))
                                           :as :byte-array}])
       "application/transit+json" {:encode #(transit-encode % :json (:encode transit-opts))
                                   :decode #(transit-decode % :json (:decode transit-opts))}
       "application/edn" {:encode #(edn-encode % (:encode edn-opts))
                          :decode #(edn-decode % (:decode edn-opts))}
       "application/json" {:encode #(json-encode % (:encode json-opts))
                           :decode #(json-decode % (:decode json-opts))}
       #?@(:bb []
           :clj
           ["application/x-www-form-urlencoded" {:encode form-encode
                                                 :decode form-decode}]
           :cljs
           ["application/x-www-form-urlencoded" {:encode form-encode
                                                 :decode form-decode
                                                 :as :text}])))))
