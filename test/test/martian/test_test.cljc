(ns martian.test-test
  (:require [martian.core :as martian]
            [martian.test :as martian-test]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen #?@(:cljs [:include-macros true])]
            [clojure.test.check.properties :as prop #?@(:cljs [:include-macros true])]
            [clojure.test.check.clojure-test :as tct]
            [schema.core :as s]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest testing is run-tests async]])
            #?(:cljs [cljs.core.async :as a]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

#?(:cljs
   (def Throwable js/Error))

(def swagger-definition
  {:paths {(keyword "/pets/{id}") {:get {:operationId "load-pet"
                                         :parameters [{:in "path"
                                                       :name "id"
                                                       :type "integer"
                                                       :required true}]
                                         :responses {200 {:description "A pet"
                                                          :schema {:$ref "#/definitions/Pet"}}
                                                     404 {:schema {:type "string"}}}}}}
   :definitions {:Pet {:type "object"
                       :properties {:id {:type "integer"
                                         :required true}
                                    :name {:type "string"
                                           :required true}}}}})

(deftest generate-response-test
  (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
              (martian-test/respond-with :random))]

    (is (thrown-with-msg? Throwable #"Value cannot be coerced to match schema"
                          (martian/response-for m :load-pet {:id "abc"})))

    (is (nil? (s/check (s/conditional
                        #(= 200 (:status %)) {:status (s/eq 200)
                                              :body {:id s/Int
                                                     :name s/Str}}
                        #(= 404 (:status %)) {:status (s/eq 404)
                                              :body s/Str})
                       (martian/response-for m :load-pet {:id 123}))))))

(deftest generate-successful-response-test
  (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
              (martian-test/respond-with :success))]

    (is (nil? (s/check {:status (s/eq 200)
                        :body {:id s/Int
                               :name s/Str}}
                       (martian/response-for m :load-pet {:id 123}))))))

(deftest generate-error-response-test
  (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
              (martian-test/respond-with :error))]

    (is (nil? (s/check {:status (s/eq 404)
                        :body s/Str}
                       (martian/response-for m :load-pet {:id 123}))))))

(defn fn-to-test
  "This is a production function which uses Martian and makes some decisions based on the response.
   This would normally be hard to test, requiring a stub server or mocking"
  [m]
  (let [{:keys [status body]} (martian/response-for m :load-pet {:id 123})]
    (when (= 200 status)
      body)))

(deftest test-check-test
  (let [m (martian/bootstrap-swagger "https://api.com" swagger-definition)
        p (prop/for-all [response (martian-test/response-generator m :load-pet)]
                        (let [output (fn-to-test (martian-test/constantly-respond m response))]
                          (if (not= 200 (:status response))
                            (nil? output)
                            (not (nil? output)))))]

    (tct/assert-check (tc/quick-check 100 p))))

(deftest simulate-implementation-responses-test
  #?(:clj
     (testing "clj-http"
       (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                   (martian-test/respond-as :clj-http)
                   (martian-test/respond-with :success))]

         (is (= 200 (:status (martian/response-for m :load-pet {:id 123})))))))

  #?(:clj
     (testing "httpkit"
       (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                   (martian-test/respond-as :httpkit)
                   (martian-test/respond-with :success))]

         (is (= 200 (:status @(martian/response-for m :load-pet {:id 123})))))))

  #?(:cljs
     (testing "cljs-http"
       (async done
              (go
                (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                            (martian-test/respond-as :cljs-http)
                            (martian-test/respond-with :success))]

                  (is (= 200 (:status (a/<! (martian/response-for m :load-pet {:id 123})))))
                  (done)))))))
