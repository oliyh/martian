(ns martian.clj-http-test
  (:require [martian.clj-http :as martian-http]
            [martian.core :as martian]
            [martian.server-stub :refer [with-server swagger-url]]
            [clojure.test :refer :all]))

(use-fixtures :once with-server)

(deftest http-test
  (let [m (martian-http/bootstrap-swagger swagger-url)]
    (let [response (martian/response-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                              :type "Dog"
                                                              :age 3}})]
      (is (= {:status 201
              :body {:id 123}}
             (select-keys response [:status :body]))))

    (let [response (martian/response-for m :get-pet {:id 123})]
      (is (= {:name "Doggy McDogFace"
              :type "Dog"
              :age 3}
             (:body response))))))
