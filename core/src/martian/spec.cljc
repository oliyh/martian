(ns martian.spec
  (:require
   #?(:clj [clojure.spec.alpha :as spec]
      :cljs [cljs.spec.alpha :as spec])
   [spec-tools.core :as st]
   [spec-tools.spec :as sts]
   [spec-tools.parse :as stp]
   [clojure.walk :refer [postwalk-replace]]))

(defn unalias-keys [parameter-aliases data]
  (if (map? data)
    (postwalk-replace parameter-aliases data)
    data))

(defn conform-data
  "Extracts the data referred to by the spec's keys and coerces it"
  [spec data & [parameter-aliases]]
  (let [conformed (as-> data %
                    (unalias-keys parameter-aliases %)
                    (st/decode spec % st/string-transformer)
                    (st/decode spec % st/strip-extra-keys-transformer))]
    (if (spec/invalid? conformed)
      (throw (ex-info "Value cannot be coerced to match spec"
                      (spec/explain-data spec data)))
      conformed)))

(defn parameter-keys [spec]
  (:keys (stp/parse-spec (spec/form spec))))

(spec/def ::a sts/int?)
(spec/def ::z sts/int?)
(spec/def ::x sts/int?)
(spec/def ::y (spec/keys :req-un [::x] :opt-un [::z] :req [::a]))

(parameter-keys ::y)

(stp/parse-spec (spec/form ::y))

(spec/def :pet/sort #{"asc" "desc"})
(spec/def :pet/sorting (spec/keys :req-un [:pet/sort]))

(spec/valid? (spec/keys :req-un [::x]) {:x 1})

;; strip-extra-keys
;; fail-on-extra-keys
(st/encode :pet/sorting {:sort "asc"} st/string-transformer)
