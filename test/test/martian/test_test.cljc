(ns martian.test-test
  (:require [martian.core :as martian]
            [martian.test :as martian-test]
            [schema.core :as s]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest testing is run-tests]])))

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
  (let [m (martian/bootstrap-swagger "https://api.com" swagger-definition
                                     {:interceptors (concat martian/default-interceptors
                                                            [martian-test/generate-response])})]

    (is (thrown-with-msg? Exception #"Value cannot be coerced to match schema"
                          (martian/response-for m :load-pet {:id "abc"})))

    (is (nil? (s/check (s/conditional
                        #(= 200 (:status %)) {:status (s/eq 200)
                                              :body {:id s/Int
                                                     :name s/Str}}
                        #(= 404 (:status %)) {:status (s/eq 404)
                                              :body s/Str})
                       (martian/response-for m :load-pet {:id 123}))))))

(deftest generate-successful-response-test
  (let [m (martian/bootstrap-swagger "https://api.com" swagger-definition
                                     {:interceptors (concat martian/default-interceptors
                                                            [martian-test/generate-success-response])})]

    (is (nil? (s/check {:status (s/eq 200)
                        :body {:id s/Int
                               :name s/Str}}
                       (martian/response-for m :load-pet {:id 123}))))))

(deftest generate-error-response-test
  (let [m (martian/bootstrap-swagger "https://api.com" swagger-definition
                                     {:interceptors (concat martian/default-interceptors
                                                            [martian-test/generate-error-response])})]

    (is (nil? (s/check {:status (s/eq 404)
                        :body s/Str}
                       (martian/response-for m :load-pet {:id 123}))))))
