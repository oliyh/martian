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

(spec/def ::x integer?)
(spec/def ::y (spec/keys :req-un [::x]))

;; strip-extra-keys
;; fail-on-extra-keys
(st/decode ::y {:x "123"} st/string-transformer)



(spec/def ::age integer?)
(spec/def ::name string?)

(spec/def ::languages
  (spec/coll-of
    (spec/and sts/keyword? #{:clj :cljs})
    :into #{}))

(spec/def ::user
  (spec/keys
    :req-un [::name ::languages ::age]))

(def data
  {:name "Ilona"
   :age "48"
   :languages ["clj" "cljs"]
   :birthdate "1968-01-02T15:04:05Z"})

(st/decode ::user data st/string-transformer)
