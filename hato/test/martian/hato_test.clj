(ns martian.hato-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.hato :as martian-http]
            [martian.server-stub :refer [swagger-url
                                         openapi-url
                                         openapi-yaml-url
                                         openapi-test-url
                                         openapi-test-yaml-url
                                         openapi-multipart-url
                                         with-server]]
            [martian.test-utils :refer [create-temp-file
                                        extend-io-factory-for-path
                                        input-stream?
                                        input-stream->byte-array
                                        multipart+boundary?]]
            [matcher-combinators.test])
  (:import (java.io PrintWriter)
           (java.net Socket)))

(use-fixtures :once with-server)

(deftest swagger-http-test
  (let [m (martian-http/bootstrap-swagger swagger-url)]

    (testing "default encoders"
      (is (= {:version :http-1.1
              :method :post
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

(deftest async-test
  (let [m (martian-http/bootstrap-swagger swagger-url {:interceptors martian-http/default-interceptors-async})]

    (testing "default encoders"
      (is (= {:version :http-1.1
              :method :post
              :url "http://localhost:8888/pets/"
              :body {:name "Doggy McDogFace", :type "Dog", :age 3}
              :headers {"Accept" "application/transit+msgpack"
                        "Content-Type" "application/transit+msgpack"}
              :as :byte-array}
             (-> (martian/request-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                           :type "Dog"
                                                           :age 3}})
                 (update :body #(encoders/transit-decode (input-stream->byte-array %) :msgpack))))))


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

(deftest error-handling-test
  (testing "remote exceptions"

    (testing "can be thrown"
      (let [m (martian-http/bootstrap-swagger swagger-url {:interceptors martian-http/default-interceptors-async})]
        (is (thrown? Exception @(martian/response-for m :get-pet {:id -1})))))

    (testing "can be handled"
      (let [turn-off-exception-throwing {:name ::turn-off-exception-throwing
                                         :enter (fn [ctx] (assoc-in ctx [:request :throw-exceptions?] false))}
            m (martian-http/bootstrap-swagger swagger-url {:interceptors (cons turn-off-exception-throwing
                                                                               martian-http/default-interceptors-async)})]
        (is (= 404 (:status @(martian/response-for m :get-pet {:id -1}))))))

    (testing "can be caught in the interceptor chain"
      (let [m (martian-http/bootstrap-swagger swagger-url {:interceptors (cons {:name ::i-catch-errors
                                                                                :error (fn [ctx _error]
                                                                                         (assoc ctx :response {:custom-error-response true}))}
                                                                               martian-http/default-interceptors-async)})]
        (is (= {:custom-error-response true}
               @(martian/response-for m :get-pet {:id -1})))))))

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
           (:api-root myaml))
        "check yaml description")

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
  (let [m (martian-http/bootstrap-openapi openapi-multipart-url)]

    (testing "common types:"
      (testing "String"
        (is (= {:version :http-1.1
                :method :post
                :url "http://localhost:8888/upload"
                :multipart [{:name "string" :content "String"}]
                :headers {"Accept" "application/json"}
                :as :text}
               (martian/request-for m :upload-data {:string "String"})))
        (is (match?
              {:version :http-1.1
               :status 200
               :headers {:content-type "application/json;charset=utf-8"}
               :body {:payload ["string"]
                      :message "Upload was successful"}
               :request {:headers {"content-type" multipart+boundary?}}}
              (martian/response-for m :upload-data {:string "String"}))))
      (testing "File"
        (let [tmp-file (create-temp-file)]
          (is (= {:version :http-1.1
                  :method :post
                  :url "http://localhost:8888/upload"
                  :multipart [{:name "binary" :content tmp-file}]
                  :headers {"Accept" "application/json"}
                  :as :text}
                 (martian/request-for m :upload-data {:binary tmp-file})))
          (is (match?
                {:version :http-1.1
                 :status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:payload ["binary"]
                        :message "Upload was successful"}
                 :request {:headers {"content-type" multipart+boundary?}}}
                (martian/response-for m :upload-data {:binary tmp-file})))))
      (testing "InputStream"
        (let [tmp-file-is (io/input-stream (create-temp-file))]
          (is (= {:version :http-1.1
                  :method :post
                  :url "http://localhost:8888/upload"
                  :multipart [{:name "binary" :content tmp-file-is}]
                  :headers {"Accept" "application/json"}
                  :as :text}
                 (martian/request-for m :upload-data {:binary tmp-file-is})))
          (is (match?
                {:version :http-1.1
                 :status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:payload ["binary"]
                        :message "Upload was successful"}
                 :request {:headers {"content-type" multipart+boundary?}}}
                (martian/response-for m :upload-data {:binary tmp-file-is})))))
      (testing "byte array"
        (let [byte-arr (byte-array [67 108 111 106 117 114 101 33])]
          (is (= {:version :http-1.1
                  :method :post
                  :url "http://localhost:8888/upload"
                  :multipart [{:name "binary" :content byte-arr}]
                  :headers {"Accept" "application/json"}
                  :as :text}
                 (martian/request-for m :upload-data {:binary byte-arr})))
          (is (match?
                {:version :http-1.1
                 :status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:payload ["binary"]
                        :message "Upload was successful"}
                 :request {:headers {"content-type" multipart+boundary?}}}
                (martian/response-for m :upload-data {:binary byte-arr}))))))

    (testing "extra types:"
      (testing "URL"
        (let [url (io/as-url (create-temp-file))]
          (is (match?
                {:version :http-1.1
                 :method :post
                 :url "http://localhost:8888/upload"
                 :multipart [{:name "binary" :content input-stream?}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary url})))
          (is (match?
                {:version :http-1.1
                 :status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:payload ["binary"]
                        :message "Upload was successful"}
                 :request {:headers {"content-type" multipart+boundary?}}}
                (martian/response-for m :upload-data {:binary url})))))
      (testing "URI"
        (let [uri (.toURI (io/as-url (create-temp-file)))]
          (is (match?
                {:version :http-1.1
                 :method :post
                 :url "http://localhost:8888/upload"
                 :multipart [{:name "binary" :content input-stream?}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary uri})))
          (is (match?
                {:version :http-1.1
                 :status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:payload ["binary"]
                        :message "Upload was successful"}
                 :request {:headers {"content-type" multipart+boundary?}}}
                (martian/response-for m :upload-data {:binary uri})))))
      (testing "Socket"
        (with-open [socket (Socket. "localhost" 8888)
                    writer (PrintWriter. (.getOutputStream socket) true)]
          (binding [*out* writer]
            (println "Hello, server! This is a raw text message."))
          (is (match?
                {:version :http-1.1
                 :method :post
                 :url "http://localhost:8888/upload"
                 :multipart [{:name "binary" :content input-stream?}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary socket})))
          (is (match?
                {:version :http-1.1
                 :status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:payload ["binary"]
                        :message "Upload was successful"}
                 :request {:headers {"content-type" multipart+boundary?}}}
                (martian/response-for m :upload-data {:binary socket})))))
      (testing "Path"
        (let [path (.toPath (create-temp-file))]
          ;; NB: This test case requires IOFactory extension for Path.
          (extend-io-factory-for-path)
          (is (match?
                {:version :http-1.1
                 :method :post
                 :url "http://localhost:8888/upload"
                 :multipart [{:name "binary" :content input-stream?}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary path})))
          (is (match?
                {:version :http-1.1
                 :status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:payload ["binary"]
                        :message "Upload was successful"}
                 :request {:headers {"content-type" multipart+boundary?}}}
                (martian/response-for m :upload-data {:binary path}))))))))
