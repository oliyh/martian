(ns martian.encoding
  (:require [clojure.string :as string]))

(defn choose-content-type [encoders options]
  (some (set options) (keys encoders)))

(def auto-encoder
  {:encode identity
   :decode identity
   :as :auto})

(defn find-encoder [encoders content-type]
  (if (string/blank? content-type)
    auto-encoder
    (loop [encoders encoders]
      (let [[ct encoder] (first encoders)]
        (cond
          (not content-type) auto-encoder

          (not encoder) auto-encoder

          (string/includes? content-type ct) encoder

          :else
          (recur (rest encoders)))))))
