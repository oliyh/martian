(ns martian.core-test
  (:require [martian.core :as martian]
            [martian.protocols :refer [url-for request-for]]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest testing is run-tests]]))
  #?(:clj (:import [martian Martian])))

#?(:cljs
   (def Throwable js/Error))

(def swagger-definition
  {:paths {(keyword "/pets/{id}")                         {:get {:operationId "load-pet"
                                                                 :parameters [{:in "path"
                                                                               :name "id"
                                                                               :type "integer"}]}}
           (keyword "/pets/")                             {:get {:operationId "all-pets"
                                                                 :parameters [{:in "query"
                                                                               :name "sort"
                                                                               :enum ["desc","asc"]
                                                                               :required false}]}
                                                           :post {:operationId "create-pet"
                                                                  :parameters [{:in "body"
                                                                                :name "Pet"
                                                                                :required true
                                                                                :schema {:$ref "#/definitions/Pet"}}]}}
           (keyword "/users/{user-id}/orders/{order-id}") {:get {:operationId "order"
                                                                 :parameters [{:in "path"
                                                                               :name "user-id"}
                                                                              {:in "path"
                                                                               :name "order-id"}]}}}
   :definitions {:Pet {:type "object"
                       :properties {:id {:type "integer"}
                                    :name {:type "string"}}}}})

(deftest url-for-test
  (let [m (martian/bootstrap "https://api.org" swagger-definition)
        url-for (partial url-for m)]

    (is (= "https://api.org/pets/123" (url-for :load-pet {:id 123})))
    (is (= "https://api.org/pets/" (url-for :all-pets)))
    (is (= "https://api.org/pets/" (url-for :create-pet)))
    (is (= "https://api.org/users/123/orders/456" (url-for :order {:user-id 123 :order-id 456})))))

(deftest string-keys-test
  (let [swagger-definition
        {"paths" {"/pets/{id}"                         {"get" {"operationId" "load-pet"
                                                               "parameters" [{"in" "path"
                                                                              "name" "id"}]}}
                  "/pets/"                             {"get" {"operationId" "all-pets"}
                                                        "post" {"operationId" "create-pet"}}
                  "/users/{user-id}/orders/{order-id}" {"get" {"operationId" "order"}}}}
        m (martian/bootstrap "https://api.org" swagger-definition)
        url-for (partial url-for m)]

    (is (= "https://api.org/pets/123" (url-for :load-pet {:id 123})))
    (is (= "https://api.org/pets/" (url-for :all-pets)))
    (is (= "https://api.org/pets/" (url-for :create-pet)))
    (is (= "https://api.org/users/123/orders/456" (url-for :order {:user-id 123 :order-id 456})))))

#?(:clj
   (deftest java-api-test
     (let [swagger-definition
           {"paths" {"/pets/{id}"                         {"get" {"operationId" "load-pet"}}
                     "/pets/"                             {"get" {"operationId" "all-pets"}
                                                           "post" {"operationId" "create-pet"}}
                     "/users/{user-id}/orders/{order-id}" {"get" {"operationId" "order"}}}}
           m (Martian. "https://api.org" swagger-definition)]

       (is (= "https://api.org/pets/123" (.urlFor m "load-pet" {"id" 123})))
       (is (= "https://api.org/pets/" (.urlFor m "all-pets")))
       (is (= "https://api.org/pets/" (.urlFor m "create-pet")))
       (is (= "https://api.org/users/123/orders/456" (.urlFor m "order" {"user-id" 123 "order-id" 456}))))))

(deftest request-for-test
  (let [m (martian/bootstrap "https://api.org" swagger-definition)
        request-for (partial request-for m)]

    (is (= {:method :get
            :uri "https://api.org/pets/123"}
           (request-for :load-pet {:id 123})
           (request-for :load-pet {:id "123"})))

    (is (= {:method :get
            :uri "https://api.org/pets/"}
           (request-for :all-pets {})))

    (is (= {:method :get
            :uri "https://api.org/pets/"
            :query-params {:sort "asc"}}
           (request-for :all-pets {:sort "asc"})))

    (is (= {:method :get
            :uri "https://api.org/users/123/orders/234"}
           (request-for :order {:user-id 123 :order-id 234})))

    (is (= {:method :post
            :uri "https://api.org/pets/"
            :body {:id 123 :name "charlie"}}
           (request-for :create-pet {:pet {:id 123 :name "charlie"}})))

    (testing "exceptions"
      (is (thrown-with-msg? Throwable #"Value cannot be coerced to match schema"
                            (request-for :all-pets {:sort "baa"})))

      (is (thrown-with-msg? Throwable #"Value cannot be coerced to match schema"
                            (request-for :load-pet {:id "one"})))

      #_(is (thrown-with-msg? Throwable #"Value does not match schema"
                            (request-for :create-pet {:pet {:id "one"
                                                            :name 1}}))))))
