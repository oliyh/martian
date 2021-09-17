(ns martian.cljs-http-promise-test
  (:require [martian.cljs-http-promise :as martian-http]
            [martian.core :as martian]
            [cljs.test :refer-macros [deftest is async]]
            [promesa.core :as prom]))

(deftest swagger-http-test
  (async done
         (-> (prom/let [m (martian-http/bootstrap-swagger "http://localhost:8888/swagger.json")
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
                             (println "done now")
                             (done))))))

(deftest openapi-bootstrap-test
  (async done
         (-> (martian-http/bootstrap-openapi "http://localhost:8888/openapi.json")
             (prom/then (fn [m]
                          (is (= "http://localhost:8888/openapi/v3"
                                 (:api-root m)))

                          (is (contains? (set (map first (martian/explore m)))
                                         :get-order-by-id))))
             (prom/finally (fn []
                             (done))))))
