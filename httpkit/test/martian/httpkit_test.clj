(ns martian.httpkit-test
  (:require [martian.httpkit :as martian-http]
            [martian.core :as martian]
            [martian.server-stub :refer [with-server swagger-url]]
            [clojure.test :refer :all]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream]))

(use-fixtures :once with-server)

(defn- input-stream->byte-array [input-stream]
  (with-open [os (ByteArrayOutputStream.)]
    (io/copy (io/input-stream input-stream) os)
    (.toByteArray os)))

(deftest http-test
  (let [m (martian-http/bootstrap-swagger swagger-url)]

    (is (= {:method :post
            :url "http://localhost:8888/pets/"
            :body {:name "Doggy McDogFace", :type "Dog", :age 3}
            :headers {"Accept" "application/transit+msgpack"
                      "Content-Type" "application/transit+msgpack"}
            :as :byte-array}
           (-> (martian/request-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                         :type "Dog"
                                                         :age 3}})
               (update :body #(martian-http/transit-decode (input-stream->byte-array %) :msgpack)))))

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
