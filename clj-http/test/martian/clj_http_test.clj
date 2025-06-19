(ns martian.clj-http-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [martian.clj-http :as martian-http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.server-stub :refer [swagger-url
                                         openapi-url
                                         openapi-test-url
                                         openapi-test-yaml-url
                                         openapi-yaml-url
                                         with-server]]
            [martian.test-utils :refer [create-temp-file
                                        extend-io-factory-for-path
                                        input-stream?
                                        input-stream->byte-array]]
            [matcher-combinators.test])
  (:import (java.net Socket)
           (org.apache.http Consts)
           (org.apache.http.entity ContentType)
           (org.apache.http.entity.mime.content ByteArrayBody FileBody InputStreamBody StringBody)))

(use-fixtures :once with-server)

(deftest swagger-http-test
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

(deftest openapi-bootstrap-test
  (let [m (martian-http/bootstrap-openapi openapi-url)
        myaml (martian-http/bootstrap-openapi openapi-yaml-url)]

    (is (= "https://sandbox.example.com"
           (:api-root (martian-http/bootstrap-openapi openapi-test-yaml-url)))
        "check yaml description")

    (is (= "https://sandbox.example.com"
           (:api-root (martian-http/bootstrap-openapi openapi-test-url)))
        "check absolute server url")

    (is (= "https://sandbox.com"
           (:api-root (martian-http/bootstrap-openapi openapi-test-url {:server-url "https://sandbox.com"})))
        "check absolute server url via opts")

    (is (= "http://localhost:8888/v3.1"
           (:api-root (martian-http/bootstrap-openapi openapi-test-url {:server-url "/v3.1"})))
        "check relative server url via opts")

    (is (= "http://localhost:8888/openapi/v3"
           (:api-root myaml)))

    (is (contains? (set (map first (martian/explore myaml)))
                   :get-order-by-id))

    (is (= "http://localhost:8888/openapi/v3"
           (:api-root m)))

    (is (contains? (set (map first (martian/explore m)))
                   :get-order-by-id))))

(deftest local-file-test
  (let [m (martian-http/bootstrap-openapi "public/openapi-test.json")]
    (is (= "https://sandbox.example.com" (:api-root m)))
    (is (= [[:list-items "Gets a list of items."]]
           (martian/explore m)))))

(deftest multipart-request-test
  (let [m (martian-http/bootstrap-openapi openapi-url)

        tmp-file (create-temp-file)
        tmp-file-is (io/input-stream tmp-file)
        byte-arr (byte-array [67 108 111 106 117 114 101 33])]

    (testing "common"
      (is (= {:method :post
              :url "http://localhost:8888/openapi/v3/upload"
              :multipart [{:name "string" :content "String"}]
              :headers {"Content-Type" "multipart/form-data"}
              :as :auto}
             (martian/request-for m :upload-data {:string "String"})))
      (is (= {:method :post
              :url "http://localhost:8888/openapi/v3/upload"
              :multipart [{:name "binary" :content tmp-file}]
              :headers {"Content-Type" "multipart/form-data"}
              :as :auto}
             (martian/request-for m :upload-data {:binary tmp-file})))
      (is (= {:method :post
              :url "http://localhost:8888/openapi/v3/upload"
              :multipart [{:name "binary" :content tmp-file-is}]
              :headers {"Content-Type" "multipart/form-data"}
              :as :auto}
             (martian/request-for m :upload-data {:binary tmp-file-is})))
      (is (= {:method :post
              :url "http://localhost:8888/openapi/v3/upload"
              :multipart [{:name "binary" :content byte-arr}]
              :headers {"Content-Type" "multipart/form-data"}
              :as :auto}
             (martian/request-for m :upload-data {:binary byte-arr}))))

    (testing "extras"
      (let [url (io/as-url tmp-file)]
        (is (match?
              {:method :post
               :url "http://localhost:8888/openapi/v3/upload"
               :multipart [{:name "binary" :content input-stream?}]
               :headers {"Content-Type" "multipart/form-data"}
               :as :auto}
              (martian/request-for m :upload-data {:binary url}))))
      (let [uri (.toURI (io/as-url tmp-file))]
        (is (match?
              {:method :post
               :url "http://localhost:8888/openapi/v3/upload"
               :multipart [{:name "binary" :content input-stream?}]
               :headers {"Content-Type" "multipart/form-data"}
               :as :auto}
              (martian/request-for m :upload-data {:binary uri}))))
      (let [sock (Socket. "localhost" 8888)]
        (is (match?
              {:method :post
               :url "http://localhost:8888/openapi/v3/upload"
               :multipart [{:name "binary" :content input-stream?}]
               :headers {"Content-Type" "multipart/form-data"}
               :as :auto}
              (martian/request-for m :upload-data {:binary sock}))))
      (let [path (.toPath tmp-file)]
        ;; NB: This test case requires IOFactory extension for Path.
        (extend-io-factory-for-path)
        (is (match?
              {:method :post
               :url "http://localhost:8888/openapi/v3/upload"
               :multipart [{:name "binary" :content input-stream?}]
               :headers {"Content-Type" "multipart/form-data"}
               :as :auto}
              (martian/request-for m :upload-data {:binary path})))))

    (testing "custom > ContentBody"
      (let [file-body (FileBody. tmp-file)
            is-body (InputStreamBody. tmp-file-is "filename")
            byte-arr-body (ByteArrayBody. byte-arr "filename")
            str-body (StringBody. "String" (ContentType/create "text/plain" Consts/UTF_8))]
        (is (= {:method :post
                :url "http://localhost:8888/openapi/v3/upload"
                :multipart [{:name "binary" :content file-body}]
                :headers {"Content-Type" "multipart/form-data"}
                :as :auto}
               (martian/request-for m :upload-data {:binary file-body})))
        (is (= {:method :post
                :url "http://localhost:8888/openapi/v3/upload"
                :multipart [{:name "binary" :content is-body}]
                :headers {"Content-Type" "multipart/form-data"}
                :as :auto}
               (martian/request-for m :upload-data {:binary is-body})))
        (is (= {:method :post
                :url "http://localhost:8888/openapi/v3/upload"
                :multipart [{:name "binary" :content byte-arr-body}]
                :headers {"Content-Type" "multipart/form-data"}
                :as :auto}
               (martian/request-for m :upload-data {:binary byte-arr-body})))
        (is (= {:method :post
                :url "http://localhost:8888/openapi/v3/upload"
                :multipart [{:name "binary" :content str-body}]
                :headers {"Content-Type" "multipart/form-data"}
                :as :auto}
               (martian/request-for m :upload-data {:binary str-body})))))))
