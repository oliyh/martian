(ns martian.parameter-aliases-test
  (:require [martian.parameter-aliases :refer [parameter-aliases unalias-data alias-schema]]
            [schema-tools.core :as st]
            [schema.core :as s]
            #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(deftest parameter-aliases-test
  (testing "produces idiomatic aliases for all keys in a schema"
    (testing "map schemas with optional keys"
      (is (= {[] {:foo-bar :fooBar
                  :bar :BAR
                  :baz :Baz}}
             (parameter-aliases {:fooBar s/Str
                                 (s/optional-key :BAR) s/Str
                                 :Baz s/Str}))))

    (testing "nested map and vector schemas"
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

    (testing "deeply nested vector schemas"
      (is (= {[] {:foo :FOO}
              [:foo] {:bar :Bar}
              [:foo :bar] {:bar-doo :barDoo
                           :bar-dee :barDee}}
             (parameter-aliases {(s/optional-key :FOO)
                                 {:Bar [[{:barDoo s/Str
                                          (s/optional-key :barDee) s/Str}]]}}))))

    (testing "default schemas"
      (is (= {[] {:foo-bar :fooBar
                  :bar :BAR
                  :baz :Baz}
              [:baz] {:quu :QUU
                      :quux :Quux}
              [:baz :quux] {:fizz :Fizz}
              [:baz :schema] {:quu :QUU, :quux :Quux}
              [:baz :schema :quux] {:fizz :Fizz}
              [:baz :value] {:quu :QUU, :quux :Quux}}
             (parameter-aliases {:fooBar s/Str
                                 (s/optional-key :BAR) s/Str
                                 :Baz (st/default {:QUU s/Str
                                                   :Quux [{:Fizz s/Str}]}
                                                  {:QUU "hi"
                                                   :Quux []})}))))))

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
  (testing "renames schema keys into idiomatic keys"
    (testing "map schemas with optional keys"
      (is (= {:foo-bar s/Str
              (s/optional-key :bar) s/Str
              :baz s/Str}
             (let [schema {:fooBar s/Str
                           (s/optional-key :BAR) s/Str
                           :Baz s/Str}]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "nested map and vector schemas"
      (is (= {:foo {:foo-bar s/Str
                    (s/optional-key :bar) [{:baz s/Str}]}}
             (let [schema {:FOO {:fooBar s/Str
                                 (s/optional-key :Bar) [{:BAZ s/Str}]}}]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "deeply nested vector schemas"
      (is (= {(s/optional-key :foo)
              {:bar [[{:bar-doo s/Str
                       (s/optional-key :bar-dee) s/Str}]]}}
             (let [schema {(s/optional-key :FOO)
                           {:Bar [[{:barDoo s/Str
                                    (s/optional-key :barDee) s/Str}]]}}]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "default schemas"
      (is (= {:foo-bar s/Str
              (s/optional-key :bar) s/Str
              :baz (st/default {:quu s/Str
                                :quux [{:fizz s/Str}]}
                               {:quu "hi"
                                :quux []})}
             (let [schema {:fooBar s/Str
                           (s/optional-key :BAR) s/Str
                           :Baz (st/default {:QUU s/Str
                                             :Quux [{:Fizz s/Str}]}
                                            {:QUU "hi"
                                             :Quux []})}]
               (alias-schema (parameter-aliases schema) schema)))))))
