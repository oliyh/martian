(ns martian.httpkit-test
  (:require [martian.httpkit :as martian-http]
            [martian.core :as martian]
            [martian.server-stub :refer [with-server swagger-url]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [cheshire.core :as json])
  (:import [java.io ByteArrayOutputStream]))

(use-fixtures :once with-server)

(defn- input-stream->byte-array [input-stream]
  (with-open [os (ByteArrayOutputStream.)]
    (io/copy (io/input-stream input-stream) os)
    (.toByteArray os)))

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
                 (update :body #(martian-http/transit-decode (input-stream->byte-array %) :msgpack))))))

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

(deftest custom-encoders-test
  ;; todo
  ;; 1. move as much of the implementation code into core as possible
  ;; 2. make the server support xml so can test the user story of adding a content type that martian doesn't support
  ;; 3. write release notes confirming the breaking change
  ;; 4. Test the auto case
  (testing "proving that you can supply your own encoders and decoders for any content type supported by the api"
    (let [test-body-json (json/encode {:name "Bob"
                                       :type "Dog"
                                       :age 3})
          my-encoders {"application/json" {:encode (constantly test-body-json)
                                           :decode (constantly ::test-decode)}}
          m (martian-http/bootstrap-swagger swagger-url {:interceptors (concat martian/default-interceptors
                                                                               [(martian-http/encode-body my-encoders)
                                                                                (martian-http/coerce-response my-encoders)
                                                                                martian-http/perform-request])})]

      (is (= {:method :post
              :url "http://localhost:8888/pets/"
              :body test-body-json
              :headers {"Accept" "application/json"
                        "Content-Type" "application/json"}
              :as :text}
             (martian/request-for m :create-pet {:pet {:name "Catty McKittenFace"
                                                       :type "Cat"
                                                       :age 100}})))

      (let [response @(martian/response-for m :create-pet {:pet {:name "Catty McKittenFace"
                                                                 :type "Cat"
                                                                 :age 100}})]
        (is (= {:status 201
                :body ::test-decode}
               (select-keys response [:status :body])))))))
