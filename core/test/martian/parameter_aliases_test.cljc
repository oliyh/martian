(ns martian.parameter-aliases-test
  (:require [martian.parameter-aliases :refer [parameter-aliases unalias-data alias-schema]]
            [martian.schema :refer [schema-with-meta]]
            [schema.core :as s]
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(deftest parameter-aliases-test
  (testing "produces idiomatic aliases for all keys in a schema"
    (is (= {[] {:foo-bar :fooBar
                :bar :BAR
                :baz :Baz}}
           (parameter-aliases {:fooBar s/Str
                               (s/optional-key :BAR) s/Str
                               :Baz s/Str}))))

  (testing "works on nested maps and sequences"
    (is (= {[] {:foo-bar :fooBar
                :bar :BAR
                :baz :Baz}
            [:baz] {:quu :QUU
                    :quux :Quux}
            [:baz :quux] {:fizz :Fizz}}
           (parameter-aliases {:fooBar s/Str
                               (s/optional-key :BAR) s/Str
                               :Baz {:QUU s/Str
                                     :Quux [{:Fizz s/Str}]}}))))

  (testing "works on nested maps and sequences"
    (is (= {[] {:foo-bar :fooBar
                :bar :BAR
                :baz :Baz}
            [:baz] {:quu :QUU
                    :quux :Quux}
            [:baz :quux] {:fizz :Fizz}}
           (parameter-aliases {:fooBar s/Str
                               (s/optional-key :BAR) s/Str
                               :Baz (schema-with-meta {:QUU s/Str
                                                       :Quux [{:Fizz s/Str}]}
                                                      {:default {:QUU "hi"}})})))))

(deftest unalias-data-test
  (testing "renames idiomatic keys back to original"
    (let [schema {:FOO s/Str
                  :fooBar s/Str
                  (s/optional-key :Bar) s/Str}]
      (is (= {:FOO "a"
              :fooBar "b"
              :Bar "c"}
             (unalias-data (parameter-aliases schema) {:foo "a" :foo-bar "b" :bar "c"})))))

  (testing "works on nested maps and sequences"
    (let [schema {:FOO {:fooBar s/Str
                        (s/optional-key :Bar) [{:BAZ s/Str}]}}]
      (is (= {:FOO {:fooBar "b"
                    :Bar [{:BAZ "c"}]}}
             (unalias-data (parameter-aliases schema) {:foo {:foo-bar "b" :bar [{:baz "c"}]}}))))))

(deftest alias-schema-test
  (testing "renames the keys in the schema to give an idiomatic input schema"
    (let [schema {:FOO {:fooBar s/Str
                        (s/optional-key :Bar) [{:BAZ s/Str}]}}]
      (is (= {:foo {:foo-bar s/Str
                    (s/optional-key :bar) [{:baz s/Str}]}}
             (alias-schema (parameter-aliases schema) schema))))))
