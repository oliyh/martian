(ns martian.schema-tools-test
  (:require [clojure.string :as str]
            [martian.schema-tools :refer [key-seqs prewalk-with-path]]
            [schema.core :as s]
            [schema-tools.core :as st]
            #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(deftest key-seqs-test
  (testing "map schemas (with all sorts of keys)"
    (is (= [[]
            [:fooBar]
            [:BAR]
            [:Baz]]
           (key-seqs {:fooBar s/Str
                      (s/optional-key :BAR) s/Str
                      (s/required-key :Baz) s/Str}))))

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
            [:FOO :Bar :barDoo]
            [:FOO :Bar :barDee]]
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
            [:Baz :schema :QUU]
            [:Baz :schema :Quux]
            [:Baz :schema :Quux :Fizz]
            [:Baz :value]
            [:Baz :value :QUU]
            [:Baz :value :Quux]
            [:Baz :QUU]
            [:Baz :Quux]
            [:Baz :Quux :Fizz]]
           (key-seqs {:fooBar s/Str
                      (s/optional-key :BAR) s/Str
                      :Baz (st/default {:QUU s/Str
                                        :Quux [{:Fizz s/Str}]}
                                       {:QUU "hi"
                                        :Quux []})}))
        "Must contain paths for both the schema and a data described by it")
    (is (= [[]
            [:schema]
            [:schema :QUU]
            [:schema :Quux]
            [:schema :Quux :Fizz]
            [:value]
            [:value :QUU]
            [:value :Quux]
            [:QUU]
            [:Quux]
            [:Quux :Fizz]]
           (key-seqs (st/default {:QUU s/Str
                                  :Quux [{:Fizz s/Str}]}
                                 {:QUU "hi"
                                  :Quux []})))
        "Must contain paths for both the schema and a data described by it"))

  (testing "named schemas"
    (is (= [[]
            [:schema]
            [:schema :fooBar]
            [:name]
            [:fooBar]]
           (key-seqs (s/named {:fooBar s/Str} "FooBar")))
        "Must contain paths for both the schema and a data described by it"))

  (testing "maybe schemas"
    (is (= [[]
            [:schema]
            [:schema :fooBar]
            [:fooBar]]
           (key-seqs (s/maybe {:fooBar s/Str})))
        "Must contain paths for both the schema and a data described by it")
    (is (= [[]
            [:fooBar]
            [:fooBar :Baz]
            [:fooBar :Baz :schema]]
           (key-seqs {:fooBar {:Baz (s/maybe s/Str)}}))
        "Must contain paths for both the schema and a data described by it"))

  (testing "constrained schemas"
    (is (= [[]
            [:fooBar]
            [:fooBar :schema]
            [:fooBar :postcondition]
            [:fooBar :post-name]]
           (key-seqs {:fooBar (s/constrained s/Str (complement str/blank?))}))
        "Must contain paths for both the schema and a data described by it")
    (is (= [[]
            [:fooBar]
            [:fooBar :schema]
            [:fooBar :schema :Baz]
            [:fooBar :postcondition]
            [:fooBar :post-name]
            [:fooBar :Baz]]
           (key-seqs {:fooBar (s/constrained {:Baz s/Str} some?)})))))

(deftest prewalk-with-path-test
  (testing "map schemas (with all sorts of keys)"
    (let [paths+forms (atom [])]
      (prewalk-with-path (fn [path form]
                           (swap! paths+forms conj [path form])
                           form)
                         []
                         {:fooBar s/Str
                          (s/optional-key :BAR) s/Str
                          (s/required-key :Baz) s/Str})
      (is (= [[[] {:fooBar s/Str, (s/optional-key :BAR) s/Str, :Baz s/Str}]
              [[] [:fooBar s/Str]]
              [[] :fooBar]
              [[:fooBar] s/Str]
              [[] [(s/optional-key :BAR) s/Str]]
              [[] (s/optional-key :BAR)]
              [[] [:k :BAR]]
              [[] :k]
              [[:k] :BAR]
              [[(s/optional-key :BAR)] s/Str]
              [[] [:Baz s/Str]]
              [[] :Baz]
              [[:Baz] s/Str]]
             @paths+forms))))

  (testing "nested map and vector schemas"
    (let [paths+forms (atom [])]
      (prewalk-with-path (fn [path form]
                           (swap! paths+forms conj [path form])
                           form)
                         []
                         {:fooBar s/Str
                          (s/optional-key :BAR) s/Str
                          :Baz {:QUU s/Str
                                :Quux [{:Fizz s/Str}]}})
      (is (= [[[] {:fooBar s/Str,
                   (s/optional-key :BAR) s/Str,
                   :Baz {:QUU s/Str, :Quux [{:Fizz s/Str}]}}]
              [[] [:fooBar s/Str]]
              [[] :fooBar]
              [[:fooBar] s/Str]
              [[] [(s/optional-key :BAR) s/Str]]
              [[] (s/optional-key :BAR)]
              [[] [:k :BAR]]
              [[] :k]
              [[:k] :BAR]
              [[(s/optional-key :BAR)] s/Str]
              [[] [:Baz {:QUU s/Str, :Quux [{:Fizz s/Str}]}]]
              [[] :Baz]
              [[:Baz] {:QUU s/Str, :Quux [{:Fizz s/Str}]}]
              [[:Baz] [:QUU s/Str]]
              [[:Baz] :QUU]
              [[:Baz :QUU] s/Str]
              [[:Baz] [:Quux [{:Fizz s/Str}]]]
              [[:Baz] :Quux]
              [[:Baz :Quux] [{:Fizz s/Str}]]
              [[:Baz :Quux] {:Fizz s/Str}]
              [[:Baz :Quux] [:Fizz s/Str]]
              [[:Baz :Quux] :Fizz]
              [[:Baz :Quux :Fizz] s/Str]]
             @paths+forms))))

  (testing "deeply nested vector schemas"
    (let [paths+forms (atom [])]
      (prewalk-with-path (fn [path form]
                           (swap! paths+forms conj [path form])
                           form)
                         []
                         {(s/optional-key :FOO)
                          {:Bar [[{:barDoo s/Str
                                   (s/optional-key :barDee) s/Str}]]}})
      (is (= [[[] {(s/optional-key :FOO) {:Bar [[{:barDoo s/Str,
                                                  (s/optional-key :barDee) s/Str}]]}}]
              [[] [(s/optional-key :FOO)
                   {:Bar [[{:barDoo s/Str, (s/optional-key :barDee) s/Str}]]}]]
              [[] (s/optional-key :FOO)]
              [[] [:k :FOO]]
              [[] :k]
              [[:k] :FOO]
              [[(s/optional-key :FOO)]
               {:Bar [[{:barDoo s/Str, (s/optional-key :barDee) s/Str}]]}]
              [[(s/optional-key :FOO)]
               [:Bar [[{:barDoo s/Str, (s/optional-key :barDee) s/Str}]]]]
              [[(s/optional-key :FOO)] :Bar]
              [[(s/optional-key :FOO) :Bar]
               [[{:barDoo s/Str, (s/optional-key :barDee) s/Str}]]]
              [[(s/optional-key :FOO) :Bar]
               [{:barDoo s/Str, (s/optional-key :barDee) s/Str}]]
              [[(s/optional-key :FOO) :Bar]
               {:barDoo s/Str, (s/optional-key :barDee) s/Str}]
              [[(s/optional-key :FOO) :Bar] [:barDoo s/Str]]
              [[(s/optional-key :FOO) :Bar] :barDoo]
              [[(s/optional-key :FOO) :Bar :barDoo] s/Str]
              [[(s/optional-key :FOO) :Bar] [(s/optional-key :barDee) s/Str]]
              [[(s/optional-key :FOO) :Bar] (s/optional-key :barDee)]
              [[(s/optional-key :FOO) :Bar] [:k :barDee]]
              [[(s/optional-key :FOO) :Bar] :k]
              [[(s/optional-key :FOO) :Bar :k] :barDee]
              [[(s/optional-key :FOO) :Bar (s/optional-key :barDee)] s/Str]]
             @paths+forms))))

  (testing "default schemas"
    (let [paths+forms (atom [])]
      (prewalk-with-path (fn [path form]
                           (swap! paths+forms conj [path form])
                           form)
                         []
                         {:fooBar s/Str
                          (s/optional-key :BAR) s/Str
                          :Baz (st/default {:QUU s/Str
                                            :Quux [{:Fizz s/Str}]}
                                           {:QUU "hi"
                                            :Quux []})})
      (is (= [[[] {:fooBar s/Str,
                   (s/optional-key :BAR) s/Str,
                   :Baz (st/default {:QUU s/Str, :Quux [{:Fizz s/Str}]} {:QUU "hi", :Quux []})}]
              [[] [:fooBar s/Str]]
              [[] :fooBar]
              [[:fooBar] s/Str]
              [[] [(s/optional-key :BAR) s/Str]]
              [[] (s/optional-key :BAR)]
              [[] [:k :BAR]]
              [[] :k]
              [[:k] :BAR]
              [[(s/optional-key :BAR)] s/Str]
              [[] [:Baz (st/default {:QUU s/Str, :Quux [{:Fizz s/Str}]} {:QUU "hi", :Quux []})]]
              [[] :Baz]
              [[:Baz] (st/default {:QUU s/Str, :Quux [{:Fizz s/Str}]} {:QUU "hi", :Quux []})]
              [[:Baz] [:schema {:QUU s/Str, :Quux [{:Fizz s/Str}]}]]
              [[:Baz] :schema]
              [[:Baz :schema] {:QUU s/Str, :Quux [{:Fizz s/Str}]}]
              [[:Baz :schema] [:QUU s/Str]]
              [[:Baz :schema] :QUU]
              [[:Baz :schema :QUU] s/Str]
              [[:Baz :schema] [:Quux [{:Fizz s/Str}]]]
              [[:Baz :schema] :Quux]
              [[:Baz :schema :Quux] [{:Fizz s/Str}]]
              [[:Baz :schema :Quux] {:Fizz s/Str}]
              [[:Baz :schema :Quux] [:Fizz s/Str]]
              [[:Baz :schema :Quux] :Fizz]
              [[:Baz :schema :Quux :Fizz] s/Str]
              [[:Baz] [:value {:QUU "hi", :Quux []}]]
              [[:Baz] :value]
              [[:Baz :value] {:QUU "hi", :Quux []}]
              [[:Baz :value] [:QUU "hi"]]
              [[:Baz :value] :QUU]
              [[:Baz :value :QUU] "hi"]
              [[:Baz :value] [:Quux []]]
              [[:Baz :value] :Quux]
              [[:Baz :value :Quux] []]]
             @paths+forms)))))
