(ns martian.schema-tools-test
  (:require [martian.schema-tools :as schema-tools]
            [schema.core :as s]
            [schema-tools.core :as st]
            #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

;; NB: A tree is a string, a vector of trees, or one of the nodes that wrap
;;     a tree in turn. A node tree is made of the nodes alone.

(defn node [child-schema]
  (s/conditional
    #(= "foo" (:type %))
    {:type (s/eq "foo")
     :child child-schema}

    #(= "bar" (:type %))
    {:type (s/eq "bar")
     :child child-schema}

    :else
    {:type (s/eq "baz")
     :child child-schema
     :quu_quux s/Int
     :fizz_buzz s/Str}))

(def node-tree
  (node (s/recursive #'node-tree)))

(def tree
  (s/cond-pre
    s/Str
    [(s/recursive #'tree)]
    (node (s/recursive #'tree))))

(deftest compute-aliases-at-test
  (testing "basic usage"
    (let [schema {:fooBar s/Str
                  (s/optional-key :BAR) s/Str
                  :Baz {:QUU s/Str
                        :Quux [{:Fizz s/Str}]}}]
      (is (= {:foo-bar :fooBar
              :bar :BAR
              :baz :Baz}
             (schema-tools/compute-aliases-at
               schema
               [])))
      (is (nil? (schema-tools/compute-aliases-at
                  schema
                  [:foo-bar])))
      (is (nil? (schema-tools/compute-aliases-at
                  schema
                  [:bar])))
      (is (= {:quu :QUU
              :quux :Quux}
             (schema-tools/compute-aliases-at
               schema
               [:baz])))
      (is (nil? (schema-tools/compute-aliases-at
                  schema
                  [:baz :quu])))
      (is (= {:fizz :Fizz}
             (schema-tools/compute-aliases-at
               schema
               [:baz :quux])))
      (is (nil? (schema-tools/compute-aliases-at
                  schema
                  [:baz :quux :fizz])))))

  (testing "non-keyword keys"
    (is (= {"foo-bar" "fooBar"
            :bar-baz :Bar-Baz}
           (schema-tools/compute-aliases-at
             {"fooBar" s/Str
              :Bar-Baz s/Str
              'bazQuux s/Str}
             []))
        "Symbols are excluded for performance purposes, could work as well"))

  (testing "qualified keys"
    (is (nil? (schema-tools/compute-aliases-at
                {:foo/Bar s/Str
                 :Baz/DOO s/Str}
                []))))

  (testing "generic keys"
    (is (nil? (schema-tools/compute-aliases-at
                {s/Str {:foo s/Str}}
                [])))
    (is (nil? (schema-tools/compute-aliases-at
                {s/Keyword {:foo s/Str}}
                [])))
    (is (nil? (schema-tools/compute-aliases-at
                (st/any-keys)
                []))))

  (testing "recursive union schemas"
    (let [calls (atom 0)
          map-entry-aliases schema-tools/map-entry-aliases
          aliases (with-redefs [schema-tools/map-entry-aliases
                                (fn [ms]
                                  (swap! calls inc)
                                  (map-entry-aliases ms))]
                    (schema-tools/compute-aliases-at
                      node-tree
                      [:child :child :child :child]))]
      (is (= {:quu-quux :quu_quux
              :fizz-buzz :fizz_buzz}
             aliases))
      (is (= 3 @calls)
          "Must take each map schema once, not once per route through the union")))

  (testing "recursive union schemas with an array of themselves"
    (is (= {:quu-quux :quu_quux
            :fizz-buzz :fizz_buzz}
           (schema-tools/compute-aliases-at tree [])))
    (is (= {:quu-quux :quu_quux
            :fizz-buzz :fizz_buzz}
           (schema-tools/compute-aliases-at
             tree [:child :child :child :child :child :child])))
    (is (nil? (schema-tools/compute-aliases-at tree [:no-such-key])))))

(deftest prewalk-with-path-test
  (testing "map schemas (with all sorts of keys)"
    (let [paths+forms (atom [])]
      (schema-tools/prewalk-with-path
        (fn [path form]
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
      (schema-tools/prewalk-with-path
        (fn [path form]
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
      (schema-tools/prewalk-with-path
        (fn [path form]
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
      (schema-tools/prewalk-with-path
        (fn [path form]
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
