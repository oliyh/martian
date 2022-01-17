(ns martian.cljs-http-test
  (:require [martian.cljs-http :as martian-http]
            [martian.core :as martian]
            [cljs.test :refer-macros [deftest testing is run-tests async]]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(deftest swagger-http-test
  (async done
         (go (let [m (<! (martian-http/bootstrap-swagger "http://localhost:8888/swagger.json"))]

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
         (go (let [m (<! (martian-http/bootstrap-openapi "http://localhost:8888/openapi.json"))]

               (is (= "http://localhost:8888/openapi/v3"
                      (:api-root m)))

               (is (contains? (set (map first (martian/explore m)))
                              :get-order-by-id)))
             (done))))
