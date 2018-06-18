(ns martian.spec-test
  (:require [martian.spec :refer [conform-data]]
            [spec-tools.spec :as sts]
            #?(:clj [clojure.spec.alpha :as spec]
               :cljs [cljs.spec.alpha :as spec])
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

#?(:cljs
   (def Throwable js/Error))

(spec/def ::int sts/int?)
(spec/def ::map-of-int (spec/keys :req-un [::int]))
(spec/def ::coll-of-int (spec/coll-of ::int))
(spec/def ::coll-of-map-of-int (spec/coll-of ::map-of-int))

(deftest conform-data-test
  (testing "primitives"
    (is 1 (conform-data ::int 1))
    (is 1 (conform-data ::int "1"))
    (is (thrown-with-msg? Throwable #"Value cannot be coerced to match spec"
                          (conform-data ::int "a"))))

  (testing "maps"
    (is {:int 1} (conform-data ::map-of-int {:int 1}))
    (is {:int 1} (conform-data ::map-of-int {:int "1"}))
    (is {:int 1} (conform-data ::map-of-int {:int 1 :foo "bar"}))
    (is {:int 1} (conform-data ::map-of-int {:my-int 1} {:my-int :int}))
    (is (thrown-with-msg? Throwable #"Value cannot be coerced to match spec"
                          (conform-data ::map-of-int 1)))
    (is (thrown-with-msg? Throwable #"Value cannot be coerced to match spec"
                          (conform-data ::map-of-int {:bar "bax"}))))

  (testing "arrays"
    (testing "of primitives"
      (is [1] (conform-data ::coll-of-int [1]))
      (is [1] (conform-data ::coll-of-int ["1"]))
      (is (thrown-with-msg? Throwable #"Value cannot be coerced to match spec"
                            (conform-data ::coll-of-int ["a"])))
      (is (thrown-with-msg? Throwable #"Value cannot be coerced to match spec"
                            (conform-data ::coll-of-int 1))))

    (testing "of maps"
      (is [{:int 1} {:int 2}] (conform-data ::coll-of-map-of-int [{:int 1} {:int "2"}]))
      (is [{:int 1} {:int 2}] (conform-data ::coll-of-map-of-int [{:my-int 1} {:my-int "2"}] {:my-int :int}))
      (is [{:int 1} {:int 2}] (conform-data ::coll-of-map-of-int [{:int 1 :foo "bar"}
                                                                  {:int "2" :baz "quu"}]))
      (is (thrown-with-msg? Throwable #"Value cannot be coerced to match spec"
                            (conform-data ::coll-of-map-of-int 1))))))
