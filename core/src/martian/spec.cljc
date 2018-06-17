(ns martian.spec
  (:require
   #?(:clj [clojure.spec.alpha :as spec]
      :cljs [cljs.spec.alpha :as spec])
   [spec-tools.core :as st]
   [spec-tools.spec :as sts]))

(defn conform-data
  "Extracts the data referred to by the spec's keys and coerces it"
  [spec data & [parameter-aliases]]
  (println "spec" spec "data" data)
  (let [result (st/decode spec data st/string-transformer)]
    (println "Result of conforming is" result)
    result))

(spec/def ::x sts/int?)
(spec/def ::y (spec/keys :req-un [::x]))

;; strip-extra-keys
;; fail-on-extra-keys
(st/decode ::y {:x "123"} st/string-transformer)
