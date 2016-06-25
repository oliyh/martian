(ns martian.test-test
  (:require [martian.core :as martian]
            [martian.protocols :refer [url-for request-for]]
            [martian.test :as martian-test]
            [schema.core :as s]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest testing is run-tests]])))

(def swagger-definition
  {:paths {(keyword "/pets/{id}") {:get {:operationId "load-pet"
                                         :parameters [{:in "path"
                                                       :name "id"
                                                       :type "integer"}]
                                         :responses {200 {:description "A pet"
                                                          :schema {:$ref "#/definitions/Pet"}}}}}}
   :definitions {:Pet {:type "object"
                       :properties {:id {:type "integer"
                                         :required true}
                                    :name {:type "string"
                                           :required true}}}}})

(deftest generate-response-test
  (let [m (martian/bootstrap-swagger "https://api.com" swagger-definition
                                     {:interceptors [martian-test/generate-response]})]

    (is (nil? (s/check {:status (s/eq 200)
                        :body {:id s/Int
                               :name s/Str}}
                       (request-for m :load-pet {:id 123}))))))
