(ns martian.httpkit-test
  (:require [martian.httpkit :as martian-http]
            [martian.core :as martian]
            [martian.interceptors :as interceptors]
            [martian.server-stub :refer [with-server swagger-url]]
            [martian.encoding :as encoding]
            [martian.test-utils :refer [input-stream->byte-array]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [cheshire.core :as json]))

(use-fixtures :once with-server)

(deftest http-test
  (let [m (martian-http/bootstrap-swagger swagger-url)]

    (testing "default encoders"
      (is (= {:method :post
              :url "http://localhost:8888/pets/"
              :body {:name "Doggy McDogFace", :type "Dog", :age 3}
              :headers {"Accept" "application/transit+msgpack"
                        "Content-Type" "application/transit+msgpack"}
              :as :byte-array}
             (-> (martian/request-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                           :type "Dog"
                                                           :age 3}})
                 (update :body #(encoding/transit-decode (input-stream->byte-array %) :msgpack))))))

    (let [response @(martian/response-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                               :type "Dog"
                                                               :age 3}})]
      (is (= {:status 201
              :body {:id 123}}
             (select-keys response [:status :body]))))

    (let [response @(martian/response-for m :get-pet {:id 123})]
      (is (= {:name "Doggy McDogFace"
              :type "Dog"
              :age 3}
             (:body response))))))
