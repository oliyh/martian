(ns martian.encoding
  (:require [clojure.string :as str])
  #?(:clj (:import (java.io InputStream))))

(defn choose-media-type [encoders options]
  (some (set options) (keys encoders)))

(def auto-encoder
  {:encode identity
   :decode identity})

(defn get-type-subtype [media-type]
  (when (and (string? media-type) (not (str/blank? media-type)))
    (str/trim
      (if-some [params-sep-idx (str/index-of media-type \;)]
        (subs media-type 0 params-sep-idx)
        media-type))))

(defn find-encoder [encoders media-type]
  (or (when-some [type-subtype (get-type-subtype media-type)]
        (get encoders type-subtype auto-encoder))
      auto-encoder))

(defn coerce-as
  [encoders media-type {:keys [missing-encoder-as default-encoder-as]}]
  (let [encoder (find-encoder encoders media-type)]
    (if (= auto-encoder encoder)
      [:missing missing-encoder-as]
      (if-let [encoder-as (:as encoder)]
        [:encoder encoder-as]
        [:default default-encoder-as]))))

(def raw-type?
  #?(:clj  #(or (string? %) (bytes? %) (instance? InputStream %))
     :cljs string?))
