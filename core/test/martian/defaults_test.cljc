(ns martian.defaults-test
  (:require [martian.defaults :refer [defaults]]
            [martian.schema :refer [schema-with-meta]]
            [schema.core :as s]
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(deftest map-defaults
  (testing "defaults top level maps"
    (is (= {:foo "bar"}
           (defaults {:foo (schema-with-meta s/Str {:default "bar"})}))))

  (testing "defaults nested maps"
    (is (= {:foo "bar"
            :baz {:quu 123}}
           (defaults {:foo (schema-with-meta s/Str {:default "bar"})
                      :baz {:quu (schema-with-meta s/Int {:default 123})}})))

    (testing "within nested maps"
      (is (= {:foo "bar"
              :baz {:quu {:quux "baffle"}}}
             (defaults {:foo (schema-with-meta s/Str {:default "bar"})
                        :baz {:quu {:quux (schema-with-meta s/Str {:default "baffle"})}}}))))))
