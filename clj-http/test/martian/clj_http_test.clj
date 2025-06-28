(ns martian.clj-http-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [martian.clj-http :as martian-http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.server-stub :refer [swagger-url
                                         openapi-url
                                         openapi-yaml-url
                                         openapi-test-url
                                         openapi-test-yaml-url
                                         openapi-multipart-url
                                         test-multipart-file-url
                                         with-server]]
            [martian.test-utils :refer [binary-content
                                        create-temp-file
                                        extend-io-factory-for-path
                                        input-stream?
                                        input-stream->byte-array
                                        multipart+boundary?]]
            [matcher-combinators.test])
  (:import (java.io PrintWriter)
           (java.net Socket URI)
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
  (let [m (martian-http/bootstrap-openapi openapi-multipart-url)]
    (is (= "http://localhost:8888" (:api-root m)))

    (testing "common types:"
      (testing "String"
        (is (match?
              {:multipart [{:name "string" :content "Howdy!"}]
               :headers {"Accept" "application/json"}
               :as :text}
              (martian/request-for m :upload-data {:string "Howdy!"})))
        (is (match?
              {:status 200
               :headers {:content-type "application/json;charset=utf-8"}
               :body {:content-type multipart+boundary?
                      :content-map {:string "Howdy!"}}}
              (martian/response-for m :upload-data {:string "Howdy!"}))))
      (testing "File"
        (let [tmp-file (create-temp-file)]
          (is (match?
                {:multipart [{:name "binary" :content tmp-file}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary tmp-file})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:binary (binary-content tmp-file)}}}
                (martian/response-for m :upload-data {:binary tmp-file})))))
      (testing "InputStream"
        (let [tmp-file (create-temp-file)
              tmp-file-is (io/input-stream tmp-file)]
          (is (match?
                {:multipart [{:name "binary" :content tmp-file-is}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary tmp-file-is})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:binary (binary-content tmp-file "binary")}}}
                (martian/response-for m :upload-data {:binary tmp-file-is})))))
      (testing "byte array"
        (let [byte-arr (String/.getBytes "Clojure!")]
          (is (match?
                {:multipart [{:name "binary" :content byte-arr}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary byte-arr})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:binary {:filename "binary"
                                               :content-type "application/octet-stream"
                                               :tempfile "Clojure!"
                                               :size 8}}}}
                (martian/response-for m :upload-data {:binary byte-arr}))))))

    (testing "extra types:"
      (testing "URL"
        (let [url (.toURL (URI. test-multipart-file-url))]
          (is (match?
                {:multipart [{:name "binary" :content input-stream?}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary url})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:binary {:filename "binary"
                                               :content-type "application/octet-stream"
                                               :tempfile "Content retrieved via URL/URI"
                                               :size 29}}}}
                (martian/response-for m :upload-data {:binary url})))))
      (testing "URI"
        (let [uri (URI. test-multipart-file-url)]
          (is (match?
                {:multipart [{:name "binary" :content input-stream?}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary uri})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:binary {:filename "binary"
                                               :content-type "application/octet-stream"
                                               :tempfile "Content retrieved via URL/URI"
                                               :size 29}}}}
                (martian/response-for m :upload-data {:binary uri})))))
      (testing "Socket"
        (with-open [socket (Socket. "localhost" 8888)
                    writer (PrintWriter. (.getOutputStream socket) true)]
          (binding [*out* writer]
            (println "Hello, server! This is an invalid HTTP message."))
          (is (match?
                {:multipart [{:name "binary" :content input-stream?}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary socket})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:binary {:filename "binary"
                                               :content-type "application/octet-stream"
                                               :tempfile #(str/starts-with? % "HTTP/1.1")}}}}
                (martian/response-for m :upload-data {:binary socket})))))
      (testing "Path"
        (let [tmp-file (create-temp-file)
              path (.toPath tmp-file)]
          ;; NB: This test case requires IOFactory extension for Path.
          (extend-io-factory-for-path)
          (is (match?
                {:multipart [{:name "binary" :content input-stream?}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:binary path})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:binary (binary-content tmp-file "binary")}}}
                (martian/response-for m :upload-data {:binary path}))))))

    (testing "custom types:"
      (testing "ContentBody > FileBody"
        (let [tmp-file (create-temp-file)
              file-body (FileBody. tmp-file)]
          (is (match?
                {:multipart [{:name "custom" :content file-body}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:custom file-body})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:custom (binary-content tmp-file)}}}
                (martian/response-for m :upload-data {:custom file-body})))))
      (testing "ContentBody > InputStreamBody"
        (let [tmp-file (create-temp-file)
              tmp-file-is (io/input-stream tmp-file)
              is-body (InputStreamBody. tmp-file-is "input-stream")]
          (is (match?
                {:multipart [{:name "custom" :content is-body}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:custom is-body})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:custom (binary-content tmp-file "input-stream")}}}
                (martian/response-for m :upload-data {:custom is-body})))))
      (testing "ContentBody > ByteArrayBody"
        (let [byte-arr (String/.getBytes "Clojure!")
              byte-arr-body (ByteArrayBody. byte-arr "byte-array")]
          (is (match?
                {:multipart [{:name "custom" :content byte-arr-body}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:custom byte-arr-body})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:custom {:filename "byte-array"
                                               :content-type "application/octet-stream"
                                               :tempfile "Clojure!"
                                               :size 8}}}}
                (martian/response-for m :upload-data {:custom byte-arr-body})))))
      (testing "ContentBody > StringBody"
        (let [content-type (ContentType/create "text/plain" Consts/UTF_8)
              str-body (StringBody. "Hello, server! This is text." content-type)]
          (is (match?
                {:multipart [{:name "custom" :content str-body}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:custom str-body})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:custom "Hello, server! This is text."}}}
                (martian/response-for m :upload-data {:custom str-body})))))

      (testing "Number"
        (let [int-num 1234567890]
          (is (match?
                {:multipart [{:name "custom" :content (str int-num)}]
                 :headers {"Accept" "application/json"}
                 :as :text}
                (martian/request-for m :upload-data {:custom int-num})))
          (is (match?
                {:status 200
                 :headers {:content-type "application/json;charset=utf-8"}
                 :body {:content-type multipart+boundary?
                        :content-map {:custom (str int-num)}}}
                (martian/response-for m :upload-data {:custom int-num}))))))))
