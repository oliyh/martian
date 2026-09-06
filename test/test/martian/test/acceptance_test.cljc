(ns martian.test.acceptance-test
  "Runs the same spec through martian-test's generative response stubbing with
   each schema backend, asserting identical observable behaviour.

   Assertions use only plain predicates and the backend's own check-schema, so
   every backend registered in `backends` must pass them unchanged."
  (:require [martian.core :as martian]
            [martian.test :as martian-test]
            [martian.backends.malli :as malli]
            [martian.backends.plumatic :as plumatic]
            [martian.schema-backend :as sb]
            [clojure.test.check.generators :as tcg]
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(def backends
  {"plumatic" plumatic/backend
   "malli"    malli/backend})

(def swagger-definition
  {:paths {(keyword "/pets/{id}") {:get {:operationId "load-pet"
                                         :parameters [{:in "path"
                                                       :name "id"
                                                       :type "integer"
                                                       :required true}]
                                         :responses {:200 {:description "A pet"
                                                           :schema {:$ref "#/definitions/Pet"}}
                                                     :404 {:schema {:type "string"}}}}}}
   :definitions {:Pet {:type "object"
                       :properties {:id {:type "integer"
                                         :required true}
                                    :name {:type "string"
                                           :required true}}}}})

(defn- bootstrap [backend]
  (martian/bootstrap-swagger "https://api.com" swagger-definition
                             {:schema-backend backend}))

(defn- pet-response? [{:keys [status body]}]
  (and (= 200 status)
       (integer? (:id body))
       (string? (:name body))))

(defn- not-found-response? [{:keys [status body]}]
  (and (= 404 status)
       (string? body)))

(deftest generated-success-response-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (-> (bootstrap backend)
                  (martian-test/respond-with-generated {:load-pet :success}))]
        (is (pet-response? (martian/response-for m :load-pet {:id 123})))))))

(deftest generated-error-response-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (-> (bootstrap backend)
                  (martian-test/respond-with-generated {:load-pet :error}))]
        (is (not-found-response? (martian/response-for m :load-pet {:id 123})))))))

(deftest generated-random-response-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (-> (bootstrap backend)
                  (martian-test/respond-with-generated {:load-pet :random}))
            response (martian/response-for m :load-pet {:id 123})]
        (is (or (pet-response? response)
                (not-found-response? response)))

        (testing "generated bodies match the handler's response schemas"
          (let [{:keys [response-schemas]} (martian/handler-for m :load-pet)]
            (is (some #(and (nil? (sb/check-schema backend (:status %) (:status response)))
                            (nil? (sb/check-schema backend (:body %) (:body response))))
                      response-schemas))))))))

(deftest response-generator-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (bootstrap backend)
            generator (martian-test/response-generator m :load-pet)]
        (is (every? #(or (pet-response? %) (not-found-response? %))
                    (tcg/sample generator 50)))))))
