(ns martian.multipart
  "An adaptor that prepares multipart content for all supported HTTP clients"
  (:require [clojure.java.io :as io])
  (:import (java.io File InputStream)))

;; http-kit = {String | File, InputStream, byte[] | ByteBuffer! | Number!??}
;; clj-http = {String | File, InputStream, byte[] | o.a.h.e.m.c.ContentBody}
;; hato     = {String | File, InputStream, byte[] | URL, URI, Socket, Path!}
;; bb/http  = {String | File, InputStream, byte[] | URL, URI, Socket, Path?}

(defn common-binary? [obj]
  (or (instance? File obj)
      (instance? InputStream obj)
      (bytes? obj)))

(def default-make-input-stream-impl
  (:make-input-stream io/default-streams-impl))

(defn implements-make-input-stream? [obj]
  (not= default-make-input-stream-impl
        (find-protocol-method io/IOFactory :make-input-stream obj)))

(defn coerce-content
  ([obj]
   (coerce-content obj nil))
  ([obj pass-pred]
   (when (some? obj) ; let it fail downstream
     (cond
       (or (string? obj)
           (common-binary? obj)
           (and pass-pred (pass-pred obj)))
       obj

       (implements-make-input-stream? obj)
       (io/input-stream obj)

       :else
       (str obj)))))
