(ns martian.test.generators-test
  (:require [martian.test.generators :as generators]
            [martian.backends.malli :as malli]
            [martian.backends.plumatic :as plumatic]
            [clojure.test.check.generators :as tcg]
            [schema.core :as s]
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(deftest generate-test
  (testing "plumatic schemas"
    (is (= 200 (generators/generate plumatic/backend (s/eq 200))))
    (is (integer? (generators/generate plumatic/backend s/Int))))

  (testing "malli schemas"
    (is (= 200 (generators/generate malli/backend [:= 200])))
    (is (integer? (generators/generate malli/backend :int)))))

(deftest response-generator-test
  (testing "generates responses matching the response schema"
    (doseq [[backend response-schema]
            [[plumatic/backend {:status (s/eq 200) :body {:id s/Int}}]
             [malli/backend {:status [:= 200] :body [:map [:id :int]]}]]]
      (is (every? (fn [{:keys [status body]}]
                    (and (= 200 status) (integer? (:id body))))
                  (tcg/sample (generators/response-generator backend response-schema) 20)))))

  (testing "responses without a body schema get a nil body"
    (doseq [[backend response-schema]
            [[plumatic/backend {:status (s/eq 204) :body nil}]
             [malli/backend {:status [:= 204] :body nil}]]]
      (is (= {:status 204 :body nil}
             (tcg/generate (generators/response-generator backend response-schema)))))))
