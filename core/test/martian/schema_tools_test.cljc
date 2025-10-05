(ns martian.schema-tools-test
  (:require [martian.schema-tools :refer [key-seqs]]
            [schema.core :as s]
            [schema-tools.core :as st]
            #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(deftest key-seqs-test
  (testing "map schemas with optional keys"
    (is (= [[]
            [:fooBar]
            [:BAR]
            [:Baz]]
           (key-seqs {:fooBar s/Str
                      (s/optional-key :BAR) s/Str
                      :Baz s/Str}))))

  (testing "nested map and vector schemas"
    (is (= [[]
            [:fooBar]
            [:BAR]
            [:Baz]
            [:Baz :QUU]
            [:Baz :Quux]
            [:Baz :Quux :Fizz]]
           (key-seqs {:fooBar s/Str
                      (s/optional-key :BAR) s/Str
                      :Baz {:QUU s/Str
                            :Quux [{:Fizz s/Str}]}}))))

  (testing "deeply nested vector schemas"
    (is (= [[]
            [:FOO]
            [:FOO :Bar]
            [:FOO :Bar :martian.schema-tools/idx]
            [:FOO :Bar :martian.schema-tools/idx :barDoo]
            [:FOO :Bar :martian.schema-tools/idx :barDee]]
           (key-seqs {(s/optional-key :FOO)
                      {:Bar [[{:barDoo s/Str
                               (s/optional-key :barDee) s/Str}]]}}))
        "Must contain paths with qualified indexes inside the nested vector"))

  (testing "default schemas"
    (is (= [[]
            [:fooBar]
            [:BAR]
            [:Baz]
            [:Baz :schema]
            [:Baz :value]
            [:Baz :schema :QUU]
            [:Baz :schema :Quux]
            [:Baz :value :QUU]
            [:Baz :value :Quux]
            [:Baz :QUU]
            [:Baz :Quux]
            [:Baz :schema :Quux :Fizz]
            [:Baz :Quux :Fizz]]
           (key-seqs {:fooBar s/Str
                      (s/optional-key :BAR) s/Str
                      :Baz (st/default {:QUU s/Str
                                        :Quux [{:Fizz s/Str}]}
                                       {:QUU "hi"
                                        :Quux []})}))
        "Must contain paths for both the schema and a data described by it")))
