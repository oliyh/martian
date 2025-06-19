(ns martian.test-utils
  (:require [clojure.java.io :as io])
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
  (Path/.toFile
    (Files/createTempFile "martian" nil (make-array FileAttribute 0))))

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
