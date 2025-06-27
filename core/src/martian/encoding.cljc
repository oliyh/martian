(ns martian.encoding
  (:require [clojure.string :as string]))

(defn choose-media-type [encoders options]
  (some (set options) (keys encoders)))

(def auto-encoder
  {:encode identity
   :decode identity})

(defn find-encoder [encoders media-type]
  (if (string/blank? media-type)
    auto-encoder
    (loop [encoders encoders]
      (let [[encoder-media-type encoder] (first encoders)]
        (cond
          (not encoder)
          auto-encoder

          (string/includes? media-type encoder-media-type)
          encoder

          :else
          (recur (rest encoders)))))))
