(ns martian.test-utils
  (:require [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream]))

(defn input-stream->byte-array [input-stream]
  (with-open [os (ByteArrayOutputStream.)]
    (io/copy (io/input-stream input-stream) os)
    (.toByteArray os)))
