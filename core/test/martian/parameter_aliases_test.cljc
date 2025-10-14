(ns martian.parameter-aliases-test
  (:require [clojure.string :as str]
            [martian.parameter-aliases :refer [parameter-aliases unalias-data alias-schema]]
            [schema-tools.core :as st]
            [schema.core :as s]
            #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(defn select-aliases-from-registry
  "Given a lazy registry and an expected map whose keys are paths,
   pull exactly those paths and return a plain {path -> alias-map}."
  [lazy-reg expected]
  (into {}
        (map (fn [path] [path (get lazy-reg path)]))
        (keys expected)))

(defmacro =aliases
  [expected schema]
  `(let [lazy-reg# (parameter-aliases ~schema)]
     (= ~expected (select-aliases-from-registry lazy-reg# ~expected))))

(defn not-blank? [s]
  (not (str/blank? s)))

(defn foo-map? [x]
  (and (map? x)
       (let [str-keys (map (comp str/lower-case name) (keys x))]
         (boolean (some #(str/starts-with? % "foo") str-keys)))))

(def not-foo-map? (complement foo-map?))

(declare schema-b)
(def schema-a {:FOO s/Str
               :Bar (s/recursive #'schema-b)})
(def schema-b {:BAZ s/Str
               :Quu (s/recursive #'schema-a)})

(deftest parameter-aliases-test
  (testing "produces idiomatic aliases for all keys in a schema"
    (testing "map schemas (with all sorts of keys)"
      (is (=aliases
            {[] {:foo-bar :fooBar
                 :bar :BAR
                 :baz :Baz}}
            {:fooBar s/Str
             (s/optional-key :BAR) s/Str
             (s/required-key :Baz) s/Str})))

    (testing "nested map and vector schemas"
      (is (=aliases
            {[] {:foo-bar :fooBar
                 :bar :BAR
                 :baz :Baz}
             [:baz] {:quu :QUU
                     :quux :Quux}
             [:baz :quux] {:fizz :Fizz}}
            {:fooBar s/Str
             (s/optional-key :BAR) s/Str
             :Baz {:QUU s/Str
                   :Quux [{:Fizz s/Str}]}})))

    (testing "deeply nested vector schemas"
      (is (=aliases
            {[] {:foo :FOO}
             [:foo] {:bar :Bar}
             [:foo :bar] {:bar-doo :barDoo
                          :bar-dee :barDee}}
            {(s/optional-key :FOO)
             {:Bar [[{:barDoo s/Str
                      (s/optional-key :barDee) s/Str}]]}})))

    (testing "default schemas"
      (is (=aliases
            {[] {:foo-bar :fooBar
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
            {:fooBar s/Str
             (s/optional-key :BAR) s/Str
             :Baz (st/default {:QUU s/Str
                               :Quux [{:Fizz s/Str}]}
                              {:QUU "hi"
                               :Quux []})})
          "Must contain aliases for both the schema and a data described by it")
      (is (=aliases
            {[] {:quu :QUU
                 :quux :Quux}
             [:quux] {:fizz :Fizz}
             [:schema] {:quu :QUU
                        :quux :Quux}
             [:schema :quux] {:fizz :Fizz}
             [:value] {:quu :QUU
                       :quux :Quux}
             [:value :quux] {:fizz :Fizz}}
            (st/default {:QUU s/Str
                         :Quux [{:Fizz s/Str}]}
                        {:QUU "hi"
                         :Quux []}))
          "Must contain aliases for both the schema and a data described by it"))

    (testing "named schemas"
      (is (=aliases
            {[] {:foo-bar :fooBar}
             [:schema] {:foo-bar :fooBar}}
            (s/named {:fooBar s/Str} "FooBar"))
          "Must contain aliases for both the schema and a data described by it"))

    (testing "maybe schemas"
      (is (=aliases
            {[] {:foo-bar :fooBar}
             [:foo-bar] {:baz :Baz}
             [:foo-bar :schema] {:baz :Baz}}
            {:fooBar (s/maybe {:Baz s/Str})})
          "Must contain aliases for both the schema and a data described by it")
      (is (=aliases
            {[] {:foo-bar :fooBar}
             [:schema] {:foo-bar :fooBar}}
            (s/maybe {:fooBar s/Str}))
          "Must contain aliases for both the schema and a data described by it"))

    (testing "constrained schemas"
      (is (=aliases
            {[] {:foo-bar :fooBar}}
            {:fooBar (s/constrained s/Str not-blank?)}))
      (is (=aliases
            {[] {:foo-bar :fooBar}
             [:foo-bar :schema] {:baz :Baz}
             [:foo-bar] {:baz :Baz}}
            {:fooBar (s/constrained {:Baz s/Str} some?)})
          "Must contain aliases for both the schema and a data described by it"))

    (testing "both schemas"
      (is (=aliases
            {[] {:foo-bar :fooBar
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
            (s/both {:fooBar s/Str
                     (s/optional-key :BAR) s/Str
                     (s/required-key :Baz) s/Str}
                    {:QUU s/Str
                     :Quux [{:Fizz s/Str}]}))
          "Must contain aliases for both the schema and a data described by it")
      (is (=aliases
            {[] {:foo :FOO}
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
            {:FOO (s/both {:fooBar s/Str
                           (s/optional-key :BAR) s/Str
                           (s/required-key :Baz) s/Str}
                          {:QUU s/Str
                           :Quux [{:Fizz s/Str}]})})
          "Must contain aliases for both the schema and a data described by it"))

    (testing "either schemas"
      (is (=aliases
            {[] {:foo-bar :fooBar
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
            (s/either {:fooBar s/Str
                       (s/optional-key :BAR) s/Str
                       (s/required-key :Baz) s/Str}
                      {:QUU s/Str
                       :Quux [{:Fizz s/Str}]}))
          "Must contain aliases for both the schema and a data described by it")
      (is (=aliases
            {[] {:foo :FOO}
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
            {:FOO (s/either {:fooBar s/Str
                             (s/optional-key :BAR) s/Str
                             (s/required-key :Baz) s/Str}
                            {:QUU s/Str
                             :Quux [{:Fizz s/Str}]})})
          "Must contain aliases for both the schema and a data described by it"))

    ;; TODO: An SCI issue happens for this test case. Unwrap when fixed.
    #?(:bb nil
       :default
       (testing "cond-pre schemas"
         (is (=aliases
               {[] {:foo-bar :fooBar}
                [:schemas] {:foo-bar :fooBar}}
               (s/cond-pre {:fooBar s/Str} s/Str))
             "Must contain paths for both the schema and a data described by it")
         (is (=aliases
               {[] {:foo :FOO}
                [:foo] {:foo-bar :fooBar}
                [:foo :schemas] {:foo-bar :fooBar}}
               {:FOO (s/cond-pre {:fooBar s/Str} s/Str)})
             "Must contain paths for both the schema and a data described by it")))

    (testing "conditional schemas"
      (is (=aliases
            {[] {:foo-bar :fooBar
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
            (s/conditional
              foo-map?
              {:fooBar s/Str
               (s/optional-key :BAR) s/Str
               (s/required-key :Baz) s/Str}
              :else
              {:QUU s/Str
               :Quux [{:Fizz s/Str}]}))
          "Must contain paths for both the schema and a data described by it")
      (is (=aliases
            {[] {:foo :FOO}
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
            {:FOO (s/conditional
                    foo-map?
                    {:fooBar s/Str
                     (s/optional-key :BAR) s/Str
                     (s/required-key :Baz) s/Str}
                    :else
                    {:QUU s/Str
                     :Quux [{:Fizz s/Str}]})})
          "Must contain paths for both the schema and a data described by it"))

    (testing "recursive schemas"
      (is (=aliases
            {[] {:foo :FOO, :bar :Bar}
             [:bar] {:baz :BAZ, :quu :Quu}
             [:bar :derefable] {:baz :BAZ, :quu :Quu}
             [:bar :quu] {:foo :FOO, :bar :Bar}
             [:bar :quu :derefable] {:foo :FOO, :bar :Bar}
             [:bar :quu :bar] {:baz :BAZ, :quu :Quu}
             [:bar :quu :bar :derefable] {:baz :BAZ, :quu :Quu}
             #_"..."}
            schema-a)
          "Must contain paths for both the schema and a data described by it")))

  (testing "non-keyword keys"
    (is (=aliases
          {[] {"foo-bar" "fooBar"}}
          {"fooBar" s/Str
           'bazQuux s/Str})
        "Symbols are excluded for performance purposes, could work as well"))

  (testing "qualified keys are not aliased"
    (is (=aliases
          {}
          {:foo/Bar s/Str
           :Baz/DOO s/Str})))

  (testing "generic keys are not aliased"
    (is (=aliases
          {}
          {s/Str {:fooBar s/Str}}))
    (is (=aliases
          {}
          {s/Keyword {:fooBar s/Str}}))
    (is (=aliases
          {}
          (st/any-keys)))))

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

    ;; TODO: An SCI issue happens for this test case. Unwrap when fixed.
    #?(:bb nil
       :default
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
                  (unalias-data (parameter-aliases schema) {:foo "b"}))))))

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

    (testing "recursive schemas"
      (is (= {:FOO "a"
              :Bar nil}
             (unalias-data (parameter-aliases schema-a) {:foo "a"
                                                         :bar nil})))
      (is (= {:FOO "a"
              :Bar {:BAZ "b"
                    :Quu nil}}
             (unalias-data (parameter-aliases schema-a) {:foo "a"
                                                         :bar {:baz "b"
                                                               :quu nil}})))
      (is (= {:FOO "a1"
              :Bar {:BAZ "b1"
                    :Quu {:FOO "a2"
                          :Bar nil}}}
             (unalias-data (parameter-aliases schema-a) {:foo "a1"
                                                         :bar {:baz "b1"
                                                               :quu {:foo "a2"
                                                                     :bar nil}}})))
      (is (= {:FOO "a1"
              :Bar {:BAZ "b1"
                    :Quu {:FOO "a2"
                          :Bar {:BAZ "b2"
                                :Quu nil}}}}
             (unalias-data (parameter-aliases schema-a) {:foo "a1"
                                                         :bar {:baz "b1"
                                                               :quu {:foo "a2"
                                                                     :bar {:baz "b2"
                                                                           :quu nil}}}})))))

  (testing "non-keyword keys"
    (is (= {"fooBar" "a"
            'baz-quux "b"}
           (let [schema {"fooBar" s/Str
                         'bazQuux s/Str}]
             (unalias-data (parameter-aliases schema) {"foo-bar" "a"
                                                       'baz-quux "b"})))
        "Symbols are excluded for performance purposes, could work as well"))

  (testing "qualified keys are not renamed"
    (is (= {:foo/Bar "a"
            :Baz/DOO "b"}
           (let [schema {:foo/Bar s/Str
                         :Baz/DOO s/Str}]
             (unalias-data (parameter-aliases schema) {:foo/Bar "a"
                                                       :Baz/DOO "b"})))))

  (testing "generic keys are not renamed"
    (is (= {"a" {:foo-bar "b"}}
           (let [schema {s/Str {:fooBar s/Str}}]
             (unalias-data (parameter-aliases schema) {"a" {:foo-bar "b"}}))))
    (is (= {:a {:foo-bar "b"}}
           (let [schema {s/Keyword {:fooBar s/Str}}]
             (unalias-data (parameter-aliases schema) {:a {:foo-bar "b"}}))))
    (is (= {:foo-bar "a"}
           (let [schema (st/any-keys)]
             (unalias-data (parameter-aliases schema) {:foo-bar "a"}))))))

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

    ;; TODO: An SCI issue happens for this test case. Unwrap when fixed.
    #?(:bb nil
       :default
       (testing "cond-pre schemas"
         (is (= (s/cond-pre {:foo-bar s/Str} s/Str)
                (let [schema (s/cond-pre {:fooBar s/Str} s/Str)]
                  (alias-schema (parameter-aliases schema) schema))))
         (is (= {:foo (s/cond-pre {:foo-bar s/Str} s/Str)}
                (let [schema {:FOO (s/cond-pre {:fooBar s/Str} s/Str)}]
                  (alias-schema (parameter-aliases schema) schema))))))

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

    (testing "recursive schemas"
      (is (= {:foo s/Str
              :bar (s/recursive #'schema-b)}
             (alias-schema (parameter-aliases schema-a) schema-a)))
      (is (= {:baz s/Str
              :quu (s/recursive #'schema-a)}
             (alias-schema (parameter-aliases schema-b) schema-b)))))

  (testing "non-keyword keys"
    (is (= {"foo-bar" s/Str
            'bazQuux s/Str}
           (let [schema {"fooBar" s/Str
                         'bazQuux s/Str}]
             (alias-schema (parameter-aliases schema) schema)))
        "Symbols are excluded for performance purposes, could work as well"))

  (testing "qualified keys are not renamed"
    (is (= {:foo/Bar s/Str
            :Baz/DOO s/Str}
           (let [schema {:foo/Bar s/Str
                         :Baz/DOO s/Str}]
             (alias-schema (parameter-aliases schema) schema)))))

  (testing "generic keys are not renamed"
    (is (= {s/Str {:fooBar s/Str}}
           (let [schema {s/Str {:fooBar s/Str}}]
             (alias-schema (parameter-aliases schema) schema))))
    (is (= {s/Keyword {:fooBar s/Str}}
           (let [schema {s/Keyword {:fooBar s/Str}}]
             (alias-schema (parameter-aliases schema) schema))))
    (is (= (st/any-keys)
           (let [schema (st/any-keys)]
             (alias-schema (parameter-aliases schema) schema))))))
