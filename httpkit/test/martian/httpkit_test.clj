(ns martian.httpkit-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.httpkit :as martian-http]
            [martian.server-stub :refer [swagger-url
                                         openapi-url
                                         openapi-yaml-url
                                         openapi-test-url
                                         openapi-test-yaml-url
                                         openapi-multipart-url
                                         test-multipart-file-url
                                         openapi-coercions-url
                                         with-server]]
            [martian.test-utils :refer [create-temp-file
                                        extend-io-factory-for-path
                                        input-stream?
                                        without-content-type?
                                        multipart+boundary?]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test])
  (:import (java.io PrintWriter)
           (java.net Socket URI)
           (java.nio ByteBuffer)))

(use-fixtures :once with-server)

(deftest swagger-http-test
  (let [m (martian-http/bootstrap-swagger swagger-url)]
    (testing "default encoders"
      (is (match?
            {:body (m/via #(encoders/transit-decode % :msgpack)
                          {:name "Doggy McDogFace", :type "Dog", :age 3})
             :headers {"Accept" "application/transit+msgpack"
                       "Content-Type" "application/transit+msgpack"}
             :as :byte-array}
            (martian/request-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                      :type "Dog"
                                                      :age 3}}))))
    (testing "server responses"
      (is (match?
            {:status 201
             :body {:id 123}}
            @(martian/response-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                        :type "Dog"
                                                        :age 3}})))
      (is (match?
            {:body {:name "Doggy McDogFace"
                    :type "Dog"
                    :age 3}}
            @(martian/response-for m :get-pet {:id 123}))))))

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
               :headers without-content-type?}
              (martian/request-for m :upload-data {:string "Howdy!"})))
        (is (match?
              {:status 200
               :body {:content-type multipart+boundary?
                      :content-map {:string "Howdy!"}}}
              @(martian/response-for m :upload-data {:string "Howdy!"}))))
      (testing "File"
        (let [tmp-file (create-temp-file)]
          (is (match?
                {:multipart [{:name "binary" :content tmp-file}]
                 :headers without-content-type?}
                (martian/request-for m :upload-data {:binary tmp-file})))
          (is (match?
                {:status 200
                 :body {:content-type multipart+boundary?
                        :content-map {:binary (slurp tmp-file)}}}
                @(martian/response-for m :upload-data {:binary tmp-file})))))
      (testing "InputStream"
        (let [tmp-file (create-temp-file)
              tmp-file-is (io/input-stream tmp-file)]
          (is (match?
                {:multipart [{:name "binary" :content tmp-file-is}]
                 :headers without-content-type?}
                (martian/request-for m :upload-data {:binary tmp-file-is})))
          (is (match?
                {:status 200
                 :body {:content-type multipart+boundary?
                        :content-map {:binary (slurp tmp-file)}}}
                @(martian/response-for m :upload-data {:binary tmp-file-is})))))
      (testing "byte array"
        (let [byte-arr (String/.getBytes "Clojure!")]
          (is (match?
                {:multipart [{:name "binary" :content byte-arr}]
                 :headers without-content-type?}
                (martian/request-for m :upload-data {:binary byte-arr})))
          (is (match?
                {:status 200
                 :body {:content-type multipart+boundary?
                        :content-map {:binary "Clojure!"}}}
                @(martian/response-for m :upload-data {:binary byte-arr}))))))

    (testing "extra types:"
      (testing "URL"
        (let [url (.toURL (URI. test-multipart-file-url))]
          (is (match?
                {:multipart [{:name "binary" :content input-stream?}]
                 :headers without-content-type?}
                (martian/request-for m :upload-data {:binary url})))
          (is (match?
                {:status 200
                 :body {:content-type multipart+boundary?
                        :content-map {:binary "Content retrieved via URL/URI"}}}
                @(martian/response-for m :upload-data {:binary url})))))
      (testing "URI"
        (let [uri (URI. test-multipart-file-url)]
          (is (match?
                {:multipart [{:name "binary" :content input-stream?}]
                 :headers without-content-type?}
                (martian/request-for m :upload-data {:binary uri})))
          (is (match?
                {:status 200
                 :body {:content-type multipart+boundary?
                        :content-map {:binary "Content retrieved via URL/URI"}}}
                @(martian/response-for m :upload-data {:binary uri})))))
      (testing "Socket"
        (with-open [socket (Socket. "localhost" 8888)
                    writer (PrintWriter. (.getOutputStream socket) true)]
          (binding [*out* writer]
            (println "Hello, server! This is an invalid HTTP message."))
          (is (match?
                {:multipart [{:name "binary" :content input-stream?}]
                 :headers without-content-type?}
                (martian/request-for m :upload-data {:binary socket})))
          (is (match?
                {:status 200
                 :body {:content-type multipart+boundary?
                        :content-map {:binary #(str/starts-with? % "HTTP/1.1")}}}
                @(martian/response-for m :upload-data {:binary socket})))))
      (testing "Path"
        (let [tmp-file (create-temp-file)
              path (.toPath tmp-file)]
          ;; NB: This test case requires IOFactory extension for Path.
          (extend-io-factory-for-path)
          (is (match?
                {:multipart [{:name "binary" :content input-stream?}]
                 :headers without-content-type?}
                (martian/request-for m :upload-data {:binary path})))
          (is (match?
                {:status 200
                 :body {:content-type multipart+boundary?
                        :content-map {:binary (slurp tmp-file)}}}
                @(martian/response-for m :upload-data {:binary path}))))))

    (testing "custom types:"
      (testing "ByteBuffer"
        (let [byte-arr (String/.getBytes "Clojure!")
              byte-buf (ByteBuffer/wrap byte-arr)]
          (is (match?
                {:multipart [{:name "custom" :content byte-buf}]
                 :headers without-content-type?}
                (martian/request-for m :upload-data {:custom byte-buf})))
          (is (match?
                {:status 200
                 :body {:content-type multipart+boundary?
                        :content-map {:custom "Clojure!"}}}
                @(martian/response-for m :upload-data {:custom byte-buf})))))
      ;; NB: Although 'http-kit' has built-in support for numbers, we omit it.
      (testing "Number"
        (let [int-num 1234567890]
          (is (match?
                {:multipart [{:name "custom" :content (str int-num)}]
                 :headers without-content-type?}
                (martian/request-for m :upload-data {:custom int-num})))
          (is (match?
                {:status 200
                 :body {:content-type multipart+boundary?
                        :content-map {:custom (str int-num)}}}
                @(martian/response-for m :upload-data {:custom int-num}))))))))

(deftest response-coercion-test
  (let [m (martian-http/bootstrap-openapi openapi-coercions-url)]
    (is (= "http://localhost:8888" (:api-root m)))

    (testing "application/edn"
      (is (match?
            {:headers {"Accept" "application/edn"}
             :as :text}
            (martian/request-for m :get-edn)))
      (is (match?
            {:status 200
             :headers {:content-type "application/edn;charset=UTF-8"}
             :body {:message "Here's some text content"}}
            @(martian/response-for m :get-edn))))
    (testing "application/json"
      (is (match?
            {:headers {"Accept" "application/json"}
             :as :text}
            (martian/request-for m :get-json)))
      (is (match?
            {:status 200
             :headers {:content-type "application/json;charset=utf-8"}
             :body {:message "Here's some text content"}}
            @(martian/response-for m :get-json))))
    (testing "application/transit+json"
      (is (match?
            {:headers {"Accept" "application/transit+json"}
             :as :text}
            (martian/request-for m :get-transit+json)))
      (is (match?
            {:status 200
             :headers {:content-type "application/transit+json;charset=UTF-8"}
             :body {:message "Here's some text content"}}
            @(martian/response-for m :get-transit+json))))
    (testing "application/transit+msgpack"
      (is (match?
            {:headers {"Accept" "application/transit+msgpack"}
             :as :byte-array}
            (martian/request-for m :get-transit+msgpack))
          "The 'application/transit+msgpack' has a custom `:as` value set")
      (is (match?
            {:status 200
             :headers {:content-type "application/transit+msgpack;charset=UTF-8"}
             :body {:message "Here's some text content"}}
            @(martian/response-for m :get-transit+msgpack))))
    (testing "application/x-www-form-urlencoded"
      (is (match?
            {:headers {"Accept" "application/x-www-form-urlencoded"}
             :as :text}
            (martian/request-for m :get-form-data)))
      (is (match?
            {:status 200
             :headers {:content-type "application/x-www-form-urlencoded"}
             :body {:message "Here's some text content"}}
            @(martian/response-for m :get-form-data))))

    (testing "multiple response content types (default encoders order)"
      (is (match?
            {:produces ["application/transit+json"]}
            (martian/handler-for m :get-something)))
      (is (match?
            {:headers {"Accept" "application/transit+json"}
             :as :text}
            (martian/request-for m :get-something)))
      (is (match?
            {:status 200
             :headers {:content-type "application/transit+json;charset=UTF-8"}
             :body {:message "Here's some text content"}}
            @(martian/response-for m :get-something))))

    (testing "any response content type (operation with '*/*' content)"
      (is (match?
            {:produces []}
            (martian/handler-for m :get-anything)))
      (let [request (martian/request-for m :get-anything)]
        (is (= :auto (:as request))
            "The response auto-coercion is set")
        (is (not (contains? (:headers request) "Accept"))
            "The 'Accept' request header is absent"))
      (is (match?
            {:status 200
             :headers {:content-type "application/json;charset=utf-8"}
             :body {:message "Here's some text content"}}
            @(martian/response-for m :get-anything)))))

  ;; FIXME: No matching clause: :magic.
  (testing "custom encoding"
    (testing "application/magical+json"
      (let [magical-encoder {:encode (comp str/reverse encoders/json-encode)
                             :decode (comp encoders/json-decode str/reverse)
                             :as :magic}
            request-encoders (assoc martian-http/default-request-encoders
                               "application/magical+json" magical-encoder)
            response-encoders (assoc martian-http/default-response-encoders
                                "application/magical+json" magical-encoder)
            m (martian-http/bootstrap-openapi
                openapi-coercions-url {:request-encoders request-encoders
                                       :response-encoders response-encoders})]
        (is (match?
              {:headers {"Accept" "application/magical+json"}
               :as :magic}
              (martian/request-for m :get-magical)))
        (is (match?
              {:status 200
               :headers {:content-type "application/magical+json"}
               :body {:message "Here's some text content"}}
              @(martian/response-for m :get-magical)))))))
