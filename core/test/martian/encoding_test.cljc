(ns martian.encoding-test
  (:require [martian.encoders :as encoders]
            [martian.encoding :as encoding]
            [matcher-combinators.test]
            #?(:clj  [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(def encoders (encoders/default-encoders))

(deftest get-type-subtype-test
  (testing "normalized (no whitespaces)"
    (is (= "*/*"
           (encoding/get-type-subtype "*/*")))
    (is (= "text/*"
           (encoding/get-type-subtype "text/*")))
    (is (= "text/plain"
           (encoding/get-type-subtype "text/plain")))
    (is (= "text/plain"
           (encoding/get-type-subtype "text/plain; charset=iso-8859-1")))
    (is (= "application/json"
           (encoding/get-type-subtype "application/json")))
    (is (= "application/json"
           (encoding/get-type-subtype "application/json; charset=utf-8")))
    (is (= "application/edn"
           (encoding/get-type-subtype "application/edn")))
    (is (= "image/svg+xml"
           (encoding/get-type-subtype "image/svg+xml")))
    (is (= "audio/*"
           (encoding/get-type-subtype "audio/*; q=0.8")))
    (is (= "multipart/form-data"
           (encoding/get-type-subtype "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW"))))
  (testing "with optional whitespace (OWS)"
    (is (= "text/plain"
           (encoding/get-type-subtype "text/plain  ; charset=iso-8859-1")))
    (is (= "text/plain"
           (encoding/get-type-subtype "text/plain\t; charset=iso-8859-1")))
    (is (= "application/json"
           (encoding/get-type-subtype "application/json ; charset=utf-8")))
    (is (= "application/json"
           (encoding/get-type-subtype "application/json\t; charset=utf-8")))))

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
