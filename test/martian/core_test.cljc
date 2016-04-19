(ns martian.core-test
  (:require [martian.core :refer :all]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest testing is run-tests]])))

(deftest url-for-test
  (let [swagger-definition
        {:paths {(keyword "/pets/{id}")                         {:get {:operationId "load-pet"}}
                 (keyword "/pets/")                             {:get {:operationId "all-pets"}
                                                                 :post {:operationId "create-pet"}}
                 (keyword "/users/{user-id}/orders/{order-id}") {:get {:operationId "order"}}}}
        url-for (bootstrap "https://api.org" swagger-definition)]

    (is (= "https://api.org/pets/123" (url-for :load-pet {:id 123})))
    (is (= "https://api.org/pets/" (url-for :all-pets)))
    (is (= "https://api.org/pets/" (url-for :create-pet)))
    (is (= "https://api.org/users/123/orders/456" (url-for :order {:user-id 123 :order-id 456})))))

#_(deftest string-keys-test
  (let [swagger-definition
        {"paths" {"/pets/{id}"                         {"get" {"operationId" "load-pet"}}
                  "/pets/"                             {"get" {"operationId" "all-pets"}
                                                        "post" {"operationId" "create-pet"}}
                  "/users/{user-id}/orders/{order-id}" {"get" {"operationId" "order"}}}}
        url-for (bootstrap "https://api.org" swagger-definition)]

    (is (= "https://api.org/pets/123" (url-for :load-pet {:id 123})))
    (is (= "https://api.org/pets/" (url-for :all-pets)))
    (is (= "https://api.org/pets/" (url-for :create-pet)))
    (is (= "https://api.org/users/123/orders/456" (url-for :order {:user-id 123 :order-id 456})))))
