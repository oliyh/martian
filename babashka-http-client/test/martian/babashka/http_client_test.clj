(ns martian.babashka.http-client-test
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [martian.babashka.http-client :as martian-http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.interceptors :as i]
            [martian.test-utils :refer [if-bb
                                        binary-content
                                        create-temp-file
                                        input-stream?
                                        input-stream->byte-array
                                        without-content-type?
                                        multipart+boundary?]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test])
  (:import (java.io PrintWriter)
           (java.net Socket URI)))

(if-bb
  (let [port 8888]
    (def swagger-url (format "http://localhost:%s/swagger.json" port))
    (def openapi-url (format "http://localhost:%s/openapi.json" port))
    (def openapi-yaml-url (format "http://localhost:%s/openapi.yaml" port))
    (def openapi-test-url (format "http://localhost:%s/openapi-test.json" port))
    (def openapi-test-yaml-url (format "http://localhost:%s/openapi-test.yaml" port))
    (def openapi-multipart-url (format "http://localhost:%s/openapi-multipart.json" port))
    (def test-multipart-file-url (format "http://localhost:%s/test-multipart.txt" port))
    (def openapi-coercions-url (format "http://localhost:%s/openapi-coercions.json" port)))
  (do
    (require '[martian.server-stub :refer [swagger-url
                                           openapi-url
                                           openapi-yaml-url
                                           openapi-test-url
                                           openapi-test-yaml-url
                                           openapi-multipart-url
                                           test-multipart-file-url
                                           openapi-coercions-url
                                           with-server]])
    (require '[martian.test-utils :refer [extend-io-factory-for-path]])
    (use-fixtures :once with-server)))

(defn- decode-body
  "The `transit+msgpack` is not available when running in BB, but is available
   when running on the JVM"
  [body]
  (if-bb
    (encoders/transit-decode (input-stream->byte-array body) :json)
    (encoders/transit-decode (input-stream->byte-array body) :msgpack)))

(deftest swagger-http-test
  (let [m (martian-http/bootstrap-swagger swagger-url)]

    (testing "default encoders"
      (is (= (if-bb
               {:method :post
                :url "http://localhost:8888/pets/"
                :body {:name "Doggy McDogFace", :type "Dog", :age 3}
                :headers {"Accept" "application/transit+json"
                          "Content-Type" "application/transit+json"}
                :version :http-1.1}

               {:method :post
                :url "http://localhost:8888/pets/"
                :body {:name "Doggy McDogFace", :type "Dog", :age 3}
                :headers {"Accept" "application/transit+msgpack"
                          "Content-Type" "application/transit+msgpack"}
                :as :bytes
                :version :http-1.1})

             (-> (martian/request-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                           :type "Dog"
                                                           :age 3}})
                 (update :body decode-body)))))

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
      (is (= (if-bb
               {:method :post
                :url "http://localhost:8888/pets/"
                :body {:name "Doggy McDogFace", :type "Dog", :age 3}
                :headers {"Accept" "application/transit+json"
                          "Content-Type" "application/transit+json"}
                :version :http-1.1}

               {:method :post
                :url "http://localhost:8888/pets/"
                :body {:name "Doggy McDogFace", :type "Dog", :age 3}
                :headers {"Accept" "application/transit+msgpack"
                          "Content-Type" "application/transit+msgpack"}
                :as :bytes
                :version :http-1.1})
             (-> (martian/request-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                           :type "Dog"
                                                           :age 3}})
                 (update :body decode-body)))))

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
                                         :enter (fn [ctx] (assoc-in ctx [:request :throw] false))}
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

(if-bb
  nil
  (deftest babashka-test
    (if (not (fs/which "bb"))
      (println "babashka not installed, skipping test")
      (process/shell
        {:inherit true}
        "bb" "-e" "(require '[babashka.classpath :as cp] '[babashka.process :as process])
      (let [classpath (str/trim (:out (process/sh \"lein classpath\")))
            split (cp/split-classpath classpath)
            without-spec (remove #(str/includes? % \"spec.alpha\") split)
            with-test (cons \"test\" without-spec)]
        (doseq [cp with-test]
          (cp/add-classpath cp)))
      (require '[clojure.test] '[martian.babashka.http-client-test])
      (let [{:keys [error fail]} (clojure.test/run-tests 'martian.babashka.http-client-test)]
        (when (or (pos? error) (pos? fail)) (System/exit 1)))"))))

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
              (martian/response-for m :upload-data {:string "Howdy!"}))))
      (testing "File"
        (let [tmp-file (create-temp-file)]
          (is (match?
                {:multipart [{:name "binary" :content tmp-file}]
                 :headers without-content-type?}
                (martian/request-for m :upload-data {:binary tmp-file})))
          (is (match?
                {:status 200
                 :body {:content-type multipart+boundary?
                        :content-map {:binary (binary-content tmp-file)}}}
                (martian/response-for m :upload-data {:binary tmp-file})))))
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
                (martian/response-for m :upload-data {:binary tmp-file-is})))))
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
                (martian/response-for m :upload-data {:binary byte-arr}))))))

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
                (martian/response-for m :upload-data {:binary url})))))
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
                (martian/response-for m :upload-data {:binary uri})))))
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
                (martian/response-for m :upload-data {:binary socket})))))
      (if-bb
        nil
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
                  (martian/response-for m :upload-data {:binary path})))))))

    (testing "custom types:"
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
                (martian/response-for m :upload-data {:custom int-num}))))))))

(deftest supported-content-types-test
  (let [m (martian-http/bootstrap-openapi openapi-url)]
    (if-bb
      (is (= {:encodes #{"application/transit+json"
                         "application/json"
                         "application/edn"
                         "multipart/form-data"}
              :decodes #{"application/transit+json"
                         "application/json"
                         "application/edn"}}
             (i/supported-content-types (:interceptors m))))
      (is (= {:encodes #{"application/transit+msgpack"
                         "application/transit+json"
                         "application/json"
                         "application/edn"
                         "application/x-www-form-urlencoded"
                         "multipart/form-data"}
              :decodes #{"application/transit+msgpack"
                         "application/transit+json"
                         "application/json"
                         "application/edn"
                         "application/x-www-form-urlencoded"}}
             (i/supported-content-types (:interceptors m)))))))

(deftest response-coercion-test
  (let [m (martian-http/bootstrap-openapi openapi-coercions-url)]
    (is (= "http://localhost:8888" (:api-root m)))

    (testing "application/edn"
      (is (match?
            {:headers {"Accept" "application/edn"}
             :as m/absent}
            (martian/request-for m :get-edn)))
      (is (match?
            {:status 200
             :headers {:content-type "application/edn;charset=UTF-8"}
             :body {:message "Here's some text content"}}
            (martian/response-for m :get-edn))))
    (testing "application/json"
      (is (match?
            {:headers {"Accept" "application/json"}
             :as m/absent}
            (martian/request-for m :get-json)))
      (is (match?
            {:status 200
             :headers {:content-type "application/json;charset=utf-8"}
             :body {:message "Here's some text content"}}
            (martian/response-for m :get-json))))
    (testing "application/transit+json"
      (is (match?
            {:headers {"Accept" "application/transit+json"}
             :as m/absent}
            (martian/request-for m :get-transit+json)))
      (is (match?
            {:status 200
             :headers {:content-type "application/transit+json;charset=UTF-8"}
             :body {:message "Here's some text content"}}
            (martian/response-for m :get-transit+json))))
    ;; The `transit+msgpack` is not available when running in BB, but is on the JVM
    (if-bb
      nil
      (testing "application/transit+msgpack"
        (is (match?
              {:headers {"Accept" "application/transit+msgpack"}
               :as :bytes}
              (martian/request-for m :get-transit+msgpack))
            "The 'application/transit+msgpack' has a custom `:as` value set")
        (is (match?
              {:status 200
               :headers {:content-type "application/transit+msgpack;charset=UTF-8"}
               :body {:message "Here's some text content"}}
              (martian/response-for m :get-transit+msgpack)))))
    ;; The `form-urlencoded` is not available when running in BB, but is on the JVM
    (if-bb
      nil
      (testing "application/x-www-form-urlencoded"
        (is (match?
              {:headers {"Accept" "application/x-www-form-urlencoded"}
               :as m/absent}
              (martian/request-for m :get-form-data)))
        (is (match?
              {:status 200
               :headers {:content-type "application/x-www-form-urlencoded"}
               :body {:message "Here's some text content"}}
              (martian/response-for m :get-form-data)))))

    (testing "multiple response content types (default encoders order)"
      (is (match?
            {:produces ["application/json"]}
            (martian/handler-for m :get-something)))
      (is (match?
            {:headers {"Accept" "application/json"}
             :as m/absent}
            (martian/request-for m :get-something)))
      (is (match?
            {:status 200
             :headers {:content-type "application/json;charset=utf-8"}
             :body {:message "Here's some text content"}}
            (martian/response-for m :get-something))))

    (testing "any response content type (operation with '*/*' content)"
      (is (match?
            {:produces []}
            (martian/handler-for m :get-anything)))
      (let [request (martian/request-for m :get-anything)]
        (is (not (contains? request :as))
            "The response auto-coercion is NOT set")
        (is (not (contains? (:headers request) "Accept"))
            "The 'Accept' request header is absent"))
      (is (match?
            {:status 200
             :headers {:content-type "application/json;charset=utf-8"}
             :body {:message "Here's some text content"}}
            (martian/response-for m :get-anything))))))
