(ns martian.encoding-test
  (:require [martian.encoders :as encoders]
            [martian.encoding :as encoding]
            [matcher-combinators.test]
            #?(:clj  [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(def encoders (encoders/default-encoders))

(deftest find-encoder-test
  (testing "no encoders"
    (is (= encoding/auto-encoder
           (encoding/find-encoder nil nil)))
    (is (= encoding/auto-encoder
           (encoding/find-encoder nil "*/*")))
    (is (= encoding/auto-encoder
           (encoding/find-encoder nil "text/plain")))
    (is (= encoding/auto-encoder
           (encoding/find-encoder nil "application/edn")))
    (is (= encoding/auto-encoder
           (encoding/find-encoder nil "application/json"))))
  (testing "default encoders"
    (is (= encoding/auto-encoder
           (encoding/find-encoder encoders nil)))
    (is (= encoding/auto-encoder
           (encoding/find-encoder encoders "*/*")))
    (is (= encoding/auto-encoder
           (encoding/find-encoder encoders "text/plain")))
    (is (= (get encoders "application/edn")
           (encoding/find-encoder encoders "application/edn")))
    (is (= (get encoders "application/json")
           (encoding/find-encoder encoders "application/json")))))
