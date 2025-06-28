(ns martian.encoding
  (:require [clojure.string :as str]))

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
