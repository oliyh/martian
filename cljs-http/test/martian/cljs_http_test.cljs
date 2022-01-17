(ns martian.cljs-http-test
  (:require [martian.cljs-http :as martian-http]
            [martian.core :as martian]
            [cljs.test :refer-macros [deftest testing is run-tests async]]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def swagger-url "http://localhost:8888/swagger.json")
(def openapi-url "http://localhost:8888/openapi.json")
(def openapi-test-url "http://localhost:8888/openapi-test.json")


(deftest swagger-http-test
  (async done
         (go (let [m (<! (martian-http/bootstrap-swagger swagger-url))]

               (let [response (<! (martian/response-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                                             :type "Dog"
                                                                             :age 3}}))]
                 (is (= {:status 201
                         :body {:id 123}}
                        (select-keys response [:status :body]))))

               (let [response (<! (martian/response-for m :get-pet {:id 123}))]
                 (is (= {:name "Doggy McDogFace"
                         :type "Dog"
                         :age 3}
                        (:body response)))))
             (done))))

(deftest openapi-bootstrap-test
  (async done
         (go (let [m (<! (martian-http/bootstrap-openapi openapi-url))
                   mt (<! (martian-http/bootstrap-openapi openapi-test-url))
                   mt1 (<! (martian-http/bootstrap-openapi openapi-test-url {:server-url "https://sandbox.com"}))
                   mt2 (<! (martian-http/bootstrap-openapi openapi-test-url {:server-url "/v3.1"}))]

               (is (= "https://sandbox.example.com"
                      (:api-root mt)) "check absolute server url")

               (is (= "https://sandbox.com"
                      (:api-root mt1)) "check absolute server url via opts")

               (is (= "http://localhost:8888/v3.1"
                      (:api-root mt2)) "check relative server url via opts")

               (is (= "http://localhost:8888/openapi/v3"
                      (:api-root m)))

               (is (contains? (set (map first (martian/explore m)))
                              :get-order-by-id)))
             (done))))
