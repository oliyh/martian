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
                    (cond (map? %) (unalias-keys parameter-aliases %)
                          (coll? %) (map (partial unalias-keys parameter-aliases) %)
                          :else %)
                    (st/decode spec % st/string-transformer)
                    (if (map? %)
                      (st/decode spec % st/strip-extra-keys-transformer)
                      %))]
    (if (spec/invalid? conformed)
      (throw (ex-info "Value cannot be coerced to match spec"
                      (spec/explain-data spec data)))
      conformed)))

(defn parameter-keys [spec]
  (let [{:keys [type keys] :as s} (stp/parse-spec (spec/form spec))]
    (condp = type
      :vector (parameter-keys (second (spec/form spec)))
      :map keys
      nil)))

(spec/def ::int sts/int?)
(spec/def ::map-of-int (spec/keys :req-un [::int]))
(spec/def ::coll-of-int (spec/coll-of ::int))
(spec/def ::coll-of-map-of-int (spec/coll-of ::map-of-int))

(second (spec/form ::coll-of-map-of-int))
(parameter-keys ::coll-of-map-of-int)
