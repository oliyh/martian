(ns martian.clj-http-lite-test
  (:require [martian.clj-http-lite :as martian-http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [tripod.context :as tc]
            [martian.interceptors :as i]
            [martian.server-stub :refer [with-server swagger-url]]
            [martian.test-utils :refer [input-stream->byte-array]]
            [clojure.test :refer :all]))

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
                 (update :body #(encoders/transit-decode (input-stream->byte-array %) :msgpack))))))

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