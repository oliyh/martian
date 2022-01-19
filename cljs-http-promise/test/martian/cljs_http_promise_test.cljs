(ns martian.cljs-http-promise-test
  (:require [martian.cljs-http-promise :as martian-http]
            [martian.core :as martian]
            [cljs.test :refer-macros [deftest is async]]
            [promesa.core :as prom]))

(def swagger-url "http://localhost:8888/swagger.json")
(def openapi-url "http://localhost:8888/openapi.json")
(def openapi-test-url "http://localhost:8888/openapi-test.json")

(deftest swagger-http-test
  (async done
         (-> (prom/let [m (martian-http/bootstrap-swagger swagger-url)
                        create-response (martian/response-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                                            :type "Dog"
                                                                            :age 3}})
                        get-response (martian/response-for m :get-pet {:id 123})]

               (is (= {:status 201
                       :body {:id 123}}
                      (select-keys create-response [:status :body])))

               (is (= {:name "Doggy McDogFace"
                       :type "Dog"
                       :age 3}
                      (:body get-response))))
             (prom/finally (fn []
                             (done))))))

(deftest openapi-bootstrap-test
  (async done
         (-> (martian-http/bootstrap-openapi openapi-test-url)
             (prom/then (fn [m]
                          (is (= "https://sandbox.example.com"
                                 (:api-root m)) "check absolute server url"))))

         (-> (martian-http/bootstrap-openapi openapi-test-url {:server-url "https://sandbox.com"})
             (prom/then (fn [m]
                          (is (= "https://sandbox.com"
                                 (:api-root m)) "check absolute server url via opts"))))
         (-> (martian-http/bootstrap-openapi openapi-test-url {:server-url "/v3.1"})
             (prom/then (fn [m]
                          (is (= "http://localhost:8888/v3.1"
                                 (:api-root m)) "check relative server url via opts"))))

         (-> (prom/let [m (martian-http/bootstrap-openapi openapi-url)]

               (is (= "http://localhost:8888/openapi/v3"
                      (:api-root m)))

               (is (contains? (set (map first (martian/explore m)))
                              :get-order-by-id)))
             (prom/finally (fn []
                             (done))))))
