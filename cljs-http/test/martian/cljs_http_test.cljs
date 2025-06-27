(ns martian.cljs-http-test
  (:require [cljs.core.async :refer [<!]]
            [cljs.test :refer-macros [async deftest is]]
            [martian.cljs-http :as martian-http]
            [martian.core :as martian]
            [martian.interceptors :as i])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [martian.file :refer [load-local-resource]]))

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
         (go (let [m (<! (martian-http/bootstrap-openapi openapi-url))]

               (is (= "https://sandbox.example.com"
                      (:api-root (<! (martian-http/bootstrap-openapi openapi-test-url))))
                   "check absolute server url")

               (is (= "https://sandbox.com"
                      (:api-root (<! (martian-http/bootstrap-openapi openapi-test-url {:server-url "https://sandbox.com"}))))
                   "check absolute server url via opts")

               (is (= "http://localhost:8888/v3.1"
                      (:api-root (<! (martian-http/bootstrap-openapi openapi-test-url {:server-url "/v3.1"}))))
                   "check relative server url via opts")

               (is (= "http://localhost:8888/openapi/v3"
                      (:api-root m)))

               (is (contains? (set (map first (martian/explore m)))
                              :get-order-by-id)))
             (done))))

(deftest local-file-test
  (let [m (martian/bootstrap-openapi "https://sandbox.example.com" (load-local-resource "public/openapi-test.json") martian-http/default-opts)]
    (is (= "https://sandbox.example.com" (:api-root m)))
    (is (= [[:list-items "Gets a list of items."]]
           (martian/explore m)))))

(deftest supported-content-types-test
  (async done
    (go (let [m (<! (martian-http/bootstrap-openapi openapi-url))]
          (is (= {:encodes #{"application/transit+json"
                             "application/json"
                             "application/edn"
                             "application/x-www-form-urlencoded"}
                  :decodes #{"application/transit+json"
                             "application/json"
                             "application/edn"
                             "application/x-www-form-urlencoded"}}
                 (i/supported-content-types (:interceptors m)))))
        (done))))
