(ns martian.parameter-aliases-test
  (:require [clojure.string :as str]
            [martian.parameter-aliases :refer [parameter-aliases unalias-data alias-schema]]
            [schema-tools.core :as st]
            [schema.core :as s]
            #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(defn not-blank? [s]
  (not (str/blank? s)))

(defn foo-map? [x]
  (and (map? x)
       (let [str-keys (map (comp str/lower-case name) (keys x))]
         (boolean (some #(str/starts-with? % "foo") str-keys)))))

(def not-foo-map? (complement foo-map?))

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
              [:baz :schema] {:quu :QUU
                              :quux :Quux}
              [:baz :schema :quux] {:fizz :Fizz}
              [:baz :value] {:quu :QUU
                             :quux :Quux}
              [:baz :value :quux] {:fizz :Fizz}}
             (parameter-aliases {:fooBar s/Str
                                 (s/optional-key :BAR) s/Str
                                 :Baz (st/default {:QUU s/Str
                                                   :Quux [{:Fizz s/Str}]}
                                                  {:QUU "hi"
                                                   :Quux []})}))
          "Must contain aliases for both the schema and a data described by it")
      (is (= {[] {:quu :QUU
                  :quux :Quux}
              [:quux] {:fizz :Fizz}
              [:schema] {:quu :QUU
                         :quux :Quux}
              [:schema :quux] {:fizz :Fizz}
              [:value] {:quu :QUU
                        :quux :Quux}
              [:value :quux] {:fizz :Fizz}}
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

    (testing "both schemas"
      (is (= {[] {:foo-bar :fooBar
                  :bar :BAR
                  :baz :Baz
                  :quu :QUU
                  :quux :Quux}
              [:quux] {:fizz :Fizz}
              [:schemas] {:foo-bar :fooBar
                          :bar :BAR
                          :baz :Baz
                          :quu :QUU
                          :quux :Quux}
              [:schemas :quux] {:fizz :Fizz}}
             (parameter-aliases (s/both {:fooBar s/Str
                                         (s/optional-key :BAR) s/Str
                                         (s/required-key :Baz) s/Str}
                                        {:QUU s/Str
                                         :Quux [{:Fizz s/Str}]})))
          "Must contain aliases for both the schema and a data described by it")
      (is (= {[] {:foo :FOO}
              [:foo] {:foo-bar :fooBar
                      :bar :BAR
                      :baz :Baz
                      :quu :QUU
                      :quux :Quux}
              [:foo :quux] {:fizz :Fizz}
              [:foo :schemas] {:foo-bar :fooBar
                               :bar :BAR
                               :baz :Baz
                               :quu :QUU
                               :quux :Quux}
              [:foo :schemas :quux] {:fizz :Fizz}}
             (parameter-aliases {:FOO (s/both {:fooBar s/Str
                                               (s/optional-key :BAR) s/Str
                                               (s/required-key :Baz) s/Str}
                                              {:QUU s/Str
                                               :Quux [{:Fizz s/Str}]})}))
          "Must contain aliases for both the schema and a data described by it"))

    (testing "either schemas"
      (is (= {[] {:foo-bar :fooBar
                  :bar :BAR
                  :baz :Baz
                  :quu :QUU
                  :quux :Quux}
              [:quux] {:fizz :Fizz}
              [:schemas] {:foo-bar :fooBar
                          :bar :BAR
                          :baz :Baz
                          :quu :QUU
                          :quux :Quux}
              [:schemas :quux] {:fizz :Fizz}}
             (parameter-aliases (s/either {:fooBar s/Str
                                           (s/optional-key :BAR) s/Str
                                           (s/required-key :Baz) s/Str}
                                          {:QUU s/Str
                                           :Quux [{:Fizz s/Str}]})))
          "Must contain aliases for both the schema and a data described by it")
      (is (= {[] {:foo :FOO}
              [:foo] {:foo-bar :fooBar
                      :bar :BAR
                      :baz :Baz
                      :quu :QUU
                      :quux :Quux}
              [:foo :quux] {:fizz :Fizz}
              [:foo :schemas] {:foo-bar :fooBar
                               :bar :BAR
                               :baz :Baz
                               :quu :QUU
                               :quux :Quux}
              [:foo :schemas :quux] {:fizz :Fizz}}
             (parameter-aliases {:FOO (s/either {:fooBar s/Str
                                                 (s/optional-key :BAR) s/Str
                                                 (s/required-key :Baz) s/Str}
                                                {:QUU s/Str
                                                 :Quux [{:Fizz s/Str}]})}))
          "Must contain aliases for both the schema and a data described by it"))

    (testing "cond-pre schemas"
      (is (= {[] {:foo-bar :fooBar}
              [:schemas] {:foo-bar :fooBar}}
             (parameter-aliases (s/cond-pre {:fooBar s/Str} s/Str)))
          "Must contain paths for both the schema and a data described by it")
      (is (= {[] {:foo :FOO}
              [:foo] {:foo-bar :fooBar}
              [:foo :schemas] {:foo-bar :fooBar}}
             (parameter-aliases {:FOO (s/cond-pre {:fooBar s/Str} s/Str)}))
          "Must contain paths for both the schema and a data described by it"))

    (testing "conditional schemas"
      (is (= {[] {:foo-bar :fooBar
                  :bar :BAR
                  :baz :Baz
                  :quu :QUU
                  :quux :Quux}
              [:quux] {:fizz :Fizz}
              [:preds-and-schemas] {:foo-bar :fooBar
                                    :bar :BAR
                                    :baz :Baz
                                    :quu :QUU
                                    :quux :Quux}
              [:preds-and-schemas :quux] {:fizz :Fizz}}
             (parameter-aliases (s/conditional
                                  foo-map?
                                  {:fooBar s/Str
                                   (s/optional-key :BAR) s/Str
                                   (s/required-key :Baz) s/Str}
                                  :else
                                  {:QUU s/Str
                                   :Quux [{:Fizz s/Str}]})))
          "Must contain paths for both the schema and a data described by it")
      (is (= {[] {:foo :FOO}
              [:foo] {:foo-bar :fooBar
                      :bar :BAR
                      :baz :Baz
                      :quu :QUU
                      :quux :Quux}
              [:foo :quux] {:fizz :Fizz}
              [:foo :preds-and-schemas] {:foo-bar :fooBar
                                         :bar :BAR
                                         :baz :Baz
                                         :quu :QUU
                                         :quux :Quux}
              [:foo :preds-and-schemas :quux] {:fizz :Fizz}}
             (parameter-aliases {:FOO (s/conditional
                                        foo-map?
                                        {:fooBar s/Str
                                         (s/optional-key :BAR) s/Str
                                         (s/required-key :Baz) s/Str}
                                        :else
                                        {:QUU s/Str
                                         :Quux [{:Fizz s/Str}]})}))
          "Must contain paths for both the schema and a data described by it"))

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
              :Baz {:QUU "x"
                    :Quux [{:Fizz "y"}]}}
             (let [schema {:fooBar s/Str
                           (s/optional-key :BAR) s/Str
                           :Baz (st/default {:QUU s/Str
                                             :Quux [{:Fizz s/Str}]}
                                            {:QUU "hi"
                                             :Quux []})}]
               (unalias-data (parameter-aliases schema) {:foo-bar "a"
                                                         :bar "b"
                                                         :baz {:quu "x"
                                                               :quux [{:fizz "y"}]}}))))
      (is (= {:QUU "x"
              :Quux [{:Fizz "y"}]}
             (let [schema (st/default {:QUU s/Str
                                       :Quux [{:Fizz s/Str}]}
                                      {:QUU "hi"
                                       :Quux []})]
               (unalias-data (parameter-aliases schema) {:quu "x"
                                                         :quux [{:fizz "y"}]})))))

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

    (testing "both schemas"
      (is (= {:fooBar "a"
              :BAR "b"
              :Baz "c"
              :QUU "x"
              :Quux [{:Fizz "y"}]}
             (let [schema (s/both {:fooBar s/Str
                                   (s/optional-key :BAR) s/Str
                                   (s/required-key :Baz) s/Str}
                                  {:QUU s/Str
                                   :Quux [{:Fizz s/Str}]})]
               (unalias-data (parameter-aliases schema) {:foo-bar "a"
                                                         :bar "b"
                                                         :baz "c"
                                                         :quu "x"
                                                         :quux [{:fizz "y"}]}))))
      (is (= {:FOO {:fooBar "a"
                    :BAR "b"
                    :Baz "c"
                    :QUU "x"
                    :Quux [{:Fizz "y"}]}}
             (let [schema {:FOO (s/both {:fooBar s/Str
                                         (s/optional-key :BAR) s/Str
                                         (s/required-key :Baz) s/Str}
                                        {:QUU s/Str
                                         :Quux [{:Fizz s/Str}]})}]
               (unalias-data (parameter-aliases schema) {:foo {:foo-bar "a"
                                                               :bar "b"
                                                               :baz "c"
                                                               :quu "x"
                                                               :quux [{:fizz "y"}]}})))))

    (testing "either schemas"
      (let [schema (s/either {:fooBar s/Str
                              (s/optional-key :BAR) s/Str
                              (s/required-key :Baz) s/Str}
                             {:QUU s/Str
                              :Quux [{:Fizz s/Str}]})]
        (is (= {:fooBar "a"
                :BAR "b"
                :Baz "c"}
               (unalias-data (parameter-aliases schema) {:foo-bar "a"
                                                         :bar "b"
                                                         :baz "c"})))
        (is (= {:QUU "x"
                :Quux [{:Fizz "y"}]}
               (unalias-data (parameter-aliases schema) {:quu "x"
                                                         :quux [{:fizz "y"}]}))))
      (let [schema {:FOO (s/either {:fooBar s/Str
                                    (s/optional-key :BAR) s/Str
                                    (s/required-key :Baz) s/Str}
                                   {:QUU s/Str
                                    :Quux [{:Fizz s/Str}]})}]
        (is (= {:FOO {:fooBar "a"
                      :BAR "b"
                      :Baz "c"}}
               (unalias-data (parameter-aliases schema) {:foo {:foo-bar "a"
                                                               :bar "b"
                                                               :baz "c"}})))
        (is (= {:FOO {:QUU "x"
                      :Quux [{:Fizz "y"}]}}
               (unalias-data (parameter-aliases schema) {:foo {:quu "x"
                                                               :quux [{:fizz "y"}]}})))))

    (testing "cond-pre schemas"
      (let [schema (s/cond-pre {:fooBar s/Str} s/Str)]
        (is (= {:fooBar "a"}
               (unalias-data (parameter-aliases schema) {:foo-bar "a"})))
        (is (= "b"
               (unalias-data (parameter-aliases schema) "b"))))
      (let [schema {:FOO (s/cond-pre {:fooBar s/Str} s/Str)}]
        (is (= {:FOO {:fooBar "a"}}
               (unalias-data (parameter-aliases schema) {:foo {:foo-bar "a"}})))
        (is (= {:FOO "b"}
               (unalias-data (parameter-aliases schema) {:foo "b"})))))

    (testing "conditional schemas"
      (let [schema (s/conditional
                     foo-map?
                     {:fooBar s/Str
                      (s/optional-key :BAR) s/Str
                      (s/required-key :Baz) s/Str}
                     not-foo-map?
                     {:QUU s/Str
                      :Quux [{:Fizz s/Str}]})]
        (is (= {:fooBar "a"
                :BAR "b"
                :Baz "c"}
               (unalias-data (parameter-aliases schema) {:foo-bar "a"
                                                         :bar "b"
                                                         :baz "c"})))
        (is (= {:QUU "x"
                :Quux [{:Fizz "y"}]}
               (unalias-data (parameter-aliases schema) {:quu "x"
                                                         :quux [{:fizz "y"}]}))))
      (let [schema {:FOO (s/conditional
                           foo-map?
                           {:fooBar s/Str
                            (s/optional-key :BAR) s/Str
                            (s/required-key :Baz) s/Str}
                           not-foo-map?
                           {:QUU s/Str
                            :Quux [{:Fizz s/Str}]})}]
        (is (= {:FOO {:fooBar "a"
                      :BAR "b"
                      :Baz "c"}}
               (unalias-data (parameter-aliases schema) {:foo {:foo-bar "a"
                                                               :bar "b"
                                                               :baz "c"}})))
        (is (= {:FOO {:QUU "x"
                      :Quux [{:Fizz "y"}]}}
               (unalias-data (parameter-aliases schema) {:foo {:quu "x"
                                                               :quux [{:fizz "y"}]}})))))

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

    (testing "both schemas"
      (is (= (s/both {:foo-bar s/Str
                      (s/optional-key :bar) s/Str
                      (s/required-key :baz) s/Str}
                     {:quu s/Str
                      :quux [{:fizz s/Str}]})
             (let [schema (s/both {:fooBar s/Str
                                   (s/optional-key :BAR) s/Str
                                   (s/required-key :Baz) s/Str}
                                  {:QUU s/Str
                                   :Quux [{:Fizz s/Str}]})]
               (alias-schema (parameter-aliases schema) schema))))
      (is (= {:foo (s/both {:foo-bar s/Str
                            (s/optional-key :bar) s/Str
                            (s/required-key :baz) s/Str}
                           {:quu s/Str
                            :quux [{:fizz s/Str}]})}
             (let [schema {:FOO (s/both {:fooBar s/Str
                                         (s/optional-key :BAR) s/Str
                                         (s/required-key :Baz) s/Str}
                                        {:QUU s/Str
                                         :Quux [{:Fizz s/Str}]})}]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "either schemas"
      (is (= (s/either {:foo-bar s/Str
                        (s/optional-key :bar) s/Str
                        (s/required-key :baz) s/Str}
                       {:quu s/Str
                        :quux [{:fizz s/Str}]})
             (let [schema (s/either {:fooBar s/Str
                                     (s/optional-key :BAR) s/Str
                                     (s/required-key :Baz) s/Str}
                                    {:QUU s/Str
                                     :Quux [{:Fizz s/Str}]})]
               (alias-schema (parameter-aliases schema) schema))))
      (is (= {:foo (s/either {:foo-bar s/Str
                              (s/optional-key :bar) s/Str
                              (s/required-key :baz) s/Str}
                             {:quu s/Str
                              :quux [{:fizz s/Str}]})}
             (let [schema {:FOO (s/either {:fooBar s/Str
                                           (s/optional-key :BAR) s/Str
                                           (s/required-key :Baz) s/Str}
                                          {:QUU s/Str
                                           :Quux [{:Fizz s/Str}]})}]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "cond-pre schemas"
      (is (= (s/cond-pre {:foo-bar s/Str} s/Str)
             (let [schema (s/cond-pre {:fooBar s/Str} s/Str)]
               (alias-schema (parameter-aliases schema) schema))))
      (is (= {:foo (s/cond-pre {:foo-bar s/Str} s/Str)}
             (let [schema {:FOO (s/cond-pre {:fooBar s/Str} s/Str)}]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "conditional schemas"
      (is (= (s/conditional
               foo-map?
               {:foo-bar s/Str
                (s/optional-key :bar) s/Str
                (s/required-key :baz) s/Str}
               not-foo-map?
               {:quu s/Str
                :quux [{:fizz s/Str}]})
             (let [schema (s/conditional
                            foo-map?
                            {:fooBar s/Str
                             (s/optional-key :BAR) s/Str
                             (s/required-key :Baz) s/Str}
                            not-foo-map?
                            {:QUU s/Str
                             :Quux [{:Fizz s/Str}]})]
               (alias-schema (parameter-aliases schema) schema))))
      (is (= {:foo (s/conditional
                     foo-map?
                     {:foo-bar s/Str
                      (s/optional-key :bar) s/Str
                      (s/required-key :baz) s/Str}
                     not-foo-map?
                     {:quu s/Str
                      :quux [{:fizz s/Str}]})}
             (let [schema {:FOO (s/conditional
                                  foo-map?
                                  {:fooBar s/Str
                                   (s/optional-key :BAR) s/Str
                                   (s/required-key :Baz) s/Str}
                                  not-foo-map?
                                  {:QUU s/Str
                                   :Quux [{:Fizz s/Str}]})}]
               (alias-schema (parameter-aliases schema) schema)))))

    (testing "qualified keys are not aliased"
      (is (= {:foo/Bar s/Str
              :Baz/DOO s/Str}
             (let [schema {:foo/Bar s/Str
                           :Baz/DOO s/Str}]
               (alias-schema (parameter-aliases schema) schema)))))))
