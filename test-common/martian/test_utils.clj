(ns martian.test-utils
  (:require [clojure.java.io :as io]
            [matcher-combinators.matchers :as m])
  (:import [java.io ByteArrayOutputStream File InputStream]
           [java.nio.file Files Path]
           [java.nio.file.attribute FileAttribute]))

(defmacro if-bb [then & [else]]
  (if (System/getProperty "babashka.version")
    then else))

(defn input-stream->byte-array [input-stream]
  (with-open [os (ByteArrayOutputStream.)]
    (io/copy (io/input-stream input-stream) os)
    (.toByteArray os)))

(def input-stream? #(instance? InputStream %))

(defn create-temp-file ^File []
  (let [tmp-file (Path/.toFile
                   (Files/createTempFile "martian" nil (make-array FileAttribute 0)))]
    (spit tmp-file (str "Random content: " (random-uuid)))
    tmp-file))

(defn binary-content
  ([file]
   (binary-content file (File/.getName file)))
  ([file filename]
   (let [content (slurp file)]
     {:filename filename
      :content-type "application/octet-stream"
      :tempfile content
      :size (count content)})))

(if-bb
  nil
  (defn extend-io-factory-for-path []
    (extend Path
      io/IOFactory
      (assoc
        io/default-streams-impl
        :make-input-stream (fn [^Path path opts]
                             (io/make-input-stream (Path/.toFile path) opts))
        :make-output-stream (fn [^Path path opts]
                              (io/make-output-stream (Path/.toFile path) opts))))))

(def multipart+boundary?
  (m/via #(subs % 0 30) "multipart/form-data; boundary="))
