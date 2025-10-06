(ns martian.parameter-aliases-test
  (:require [clojure.string :as str]
            [martian.parameter-aliases :refer [parameter-aliases unalias-data alias-schema]]
            [schema-tools.core :as st]
            [schema.core :as s]
            #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(defn not-blank? [s]
  (not (str/blank? s)))

(deftest parameter-aliases-test
  (testing "produces idiomatic aliases for all keys in a schema"
    (testing "map schemas (with all sorts of keys)"
      (is (= {[] {:foo-bar :fooBar
                  :bar :BAR
                  :baz :Baz}}
             (parameter-aliases {:fooBar s/Str
                                 (s/optional-key :BAR) s/Str
                                 (s/required-key :Baz) s/Str}))))

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
                                                   :Quux []})}))
          "Must contain aliases for both the schema and a data described by it")
      (is (= {[] {:quu :QUU, :quux :Quux}
              [:quux] {:fizz :Fizz}
              [:schema] {:quu :QUU, :quux :Quux}
              [:schema :quux] {:fizz :Fizz}
              [:value] {:quu :QUU, :quux :Quux}}
             (parameter-aliases (st/default {:QUU s/Str
                                             :Quux [{:Fizz s/Str}]}
                                            {:QUU "hi"
                                             :Quux []})))
          "Must contain aliases for both the schema and a data described by it"))

    (testing "named schemas"
      (is (= {[] {:foo-bar :fooBar}
              [:schema] {:foo-bar :fooBar}}
             (parameter-aliases (s/named {:fooBar s/Str} "FooBar")))
          "Must contain aliases for both the schema and a data described by it"))

    (testing "maybe schemas"
      (is (= {[] {:foo-bar :fooBar}
              [:foo-bar] {:baz :Baz}
              [:foo-bar :schema] {:baz :Baz}}
             (parameter-aliases {:fooBar (s/maybe {:Baz s/Str})}))
          "Must contain aliases for both the schema and a data described by it")
      (is (= {[] {:foo-bar :fooBar}
              [:schema] {:foo-bar :fooBar}}
             (parameter-aliases (s/maybe {:fooBar s/Str})))
          "Must contain aliases for both the schema and a data described by it"))

    (testing "constrained schemas"
      (is (= {[] {:foo-bar :fooBar}}
             (parameter-aliases {:fooBar (s/constrained s/Str not-blank?)})))
      (is (= {[] {:foo-bar :fooBar}
              [:foo-bar :schema] {:baz :Baz}
              [:foo-bar] {:baz :Baz}}
             (parameter-aliases {:fooBar (s/constrained {:Baz s/Str} some?)}))
          "Must contain aliases for both the schema and a data described by it"))

    (testing "qualified keys are not aliased"
      (is (= {} (parameter-aliases {:foo/Bar s/Str
                                    :Baz/DOO s/Str}))))))

(deftest unalias-data-test
  (testing "renames idiomatic keys back to original"
    (testing "map schemas (with all sorts of keys)"
      (is (= {:fooBar "a"
              :BAR "b"
              :Baz "c"}
             (let [schema {:fooBar s/Str
                           (s/optional-key :BAR) s/Str
                           (s/required-key :Baz) s/Str}]
               (unalias-data (parameter-aliases schema) {:foo-bar "a"
                                                         :bar "b"
                                                         :baz "c"})))))

    (testing "nested map and vector schemas"
      (is (= {:FOO {:fooBar "b"
                    :Bar [{:BAZ "c"}]}}
             (let [schema {:FOO {:fooBar s/Str
                                 (s/optional-key :Bar) [{:BAZ s/Str}]}}]
               (unalias-data (parameter-aliases schema) {:foo {:foo-bar "b"
                                                               :bar [{:baz "c"}]}})))))

    (testing "deeply nested vector schemas"
      (is (= {:FOO {:Bar [[{:barDoo "a"
                            :barDee "b"}]]}}
             (let [schema {(s/optional-key :FOO)
                           {:Bar [[{:barDoo s/Str
                                    (s/optional-key :barDee) s/Str}]]}}]
               (unalias-data (parameter-aliases schema) {:foo {:bar [[{:bar-doo "a"
                                                                       :bar-dee "b"}]]}})))))

    (testing "default schemas"
      (is (= {:fooBar "a"
              :BAR "b"
              :Baz {:QUU "c"
                    :Quux [{:Fizz "d"}]}}
             (let [schema {:fooBar s/Str
                           (s/optional-key :BAR) s/Str
                           :Baz (st/default {:QUU s/Str
                                             :Quux [{:Fizz s/Str}]}
                                            {:QUU "hi"
                                             :Quux []})}]
               (unalias-data (parameter-aliases schema) {:foo-bar "a"
                                                         :bar "b"
                                                         :baz {:quu "c"
                                                               :quux [{:fizz "d"}]}}))))
      (is (= {:QUU "c"
              :Quux [{:Fizz "d"}]}
             (let [schema (st/default {:QUU s/Str
                                       :Quux [{:Fizz s/Str}]}
                                      {:QUU "hi"
                                       :Quux []})]
               (unalias-data (parameter-aliases schema) {:quu "c"
                                                         :quux [{:fizz "d"}]})))))

    (testing "named schemas"
      (is (= {:fooBar "a"}
             (let [schema (s/named {:fooBar s/Str} "FooBar")]
               (unalias-data (parameter-aliases schema) {:foo-bar "a"})))))

    (testing "maybe schemas"
      (is (= {:fooBar {:Baz "a"}}
             (let [schema {:fooBar (s/maybe {:Baz s/Str})}]
               (unalias-data (parameter-aliases schema) {:foo-bar {:baz "a"}}))))
      (is (= {:fooBar "a"}
             (let [schema (s/maybe {:fooBar s/Str})]
               (unalias-data (parameter-aliases schema) {:foo-bar "a"})))))

    (testing "constrained schemas"
      (is (= {:fooBar "a"}
             (let [schema {:fooBar (s/constrained s/Str not-blank?)}]
               (unalias-data (parameter-aliases schema) {:foo-bar "a"}))))
      (is (= {:fooBar {:Baz "b"}}
             (let [schema {:fooBar (s/constrained {:Baz s/Str} some?)}]
               (unalias-data (parameter-aliases schema) {:foo-bar {:baz "b"}})))))

    (testing "qualified keys are not aliased"
      (is (= {:foo/Bar "a"
              :Baz/DOO "b"}
             (let [schema {:foo/Bar s/Str
                           :Baz/DOO s/Str}]
               (unalias-data (parameter-aliases schema) {:foo/Bar "a"
                                                         :Baz/DOO "b"})))))))

(deftest alias-schema-test
  (testing "renames schema keys into idiomatic keys"
    (testing "map schemas (with all sorts of keys)"
      (is (= {:foo-bar s/Str
              (s/optional-key :bar) s/Str
              (s/required-key :baz) s/Str}
             (let [schema {:fooBar s/Str
                           (s/optional-key :BAR) s/Str
                           (s/required-key :Baz) s/Str}]
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
               (alias-schema (parameter-aliases schema) schema))))
      (is (= (st/default {:quu s/Str
                          :quux [{:fizz s/Str}]}
                         {:quu "hi"
                          :quux []})
             (let [schema (st/default {:QUU s/Str
                                       :Quux [{:Fizz s/Str}]}
                                      {:QUU "hi"
                                       :Quux []})]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "named schemas"
      (is (= (s/named {:foo-bar s/Str} "FooBar")
             (let [schema (s/named {:fooBar s/Str} "FooBar")]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "maybe schemas"
      (is (= {:foo-bar (s/maybe {:baz s/Str})}
             (let [schema {:fooBar (s/maybe {:Baz s/Str})}]
               (alias-schema (parameter-aliases schema) schema))))
      (is (= (s/maybe {:foo-bar s/Str})
             (let [schema (s/maybe {:fooBar s/Str})]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "constrained schemas"
      (is (= {:foo-bar (s/constrained s/Str not-blank?)}
             (let [schema {:fooBar (s/constrained s/Str not-blank?)}]
               (alias-schema (parameter-aliases schema) schema))))
      (is (= {:foo-bar (s/constrained {:baz s/Str} some?)}
             (let [schema {:fooBar (s/constrained {:Baz s/Str} some?)}]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "qualified keys are not aliased"
      (is (= {:foo/Bar s/Str
              :Baz/DOO s/Str}
             (let [schema {:foo/Bar s/Str
                           :Baz/DOO s/Str}]
               (alias-schema (parameter-aliases schema) schema)))))))
