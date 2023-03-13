(ns martian.babashka.http-client-test
  (:require [babashka.process :as process]
            [babashka.fs :as fs]
            [martian.babashka.http-client :as martian-http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.test-utils :refer [input-stream->byte-array]]
            [clojure.test :refer [deftest testing is use-fixtures]]))

(defmacro if-bb [then & [else]]
  (if (System/getProperty "babashka.version")
    then else))

(if-bb
    (do
      (def swagger-url (format "http://localhost:%s/swagger.json" 8888))
      (def openapi-url (format "http://localhost:%s/openapi.json" 8888))
      (def openapi-yaml-url (format "http://localhost:%s/openapi.yaml" 8888))
      (def openapi-test-url (format "http://localhost:%s/openapi-test.json" 8888))
      (def openapi-test-yaml-url (format "http://localhost:%s/openapi-test.yaml" 8888)))
  (do
    (require '[martian.server-stub :refer [with-server swagger-url openapi-url openapi-test-url openapi-yaml-url openapi-test-yaml-url]])
    (use-fixtures :once with-server)))

(deftest swagger-http-test
  (let [m (martian-http/bootstrap-swagger swagger-url)]

    (testing "default encoders"
      (is (= {:method :post
              :url "http://localhost:8888/pets/"
              :body {:name "Doggy McDogFace", :type "Dog", :age 3}
              :headers {"Accept" "application/transit+json"
                        "Content-Type" "application/transit+json"}
              :as :text
              :version :http-1.1}
             (-> (martian/request-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                           :type "Dog"
                                                           :age 3}})
                 (update :body #(encoders/transit-decode (input-stream->byte-array %) :json))))))


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
      (is (= {:method :post
              :url "http://localhost:8888/pets/"
              :body {:name "Doggy McDogFace", :type "Dog", :age 3}
              :headers {"Accept" "application/transit+json"
                        "Content-Type" "application/transit+json"}
              :as :text
              :version :http-1.1}
             (-> (martian/request-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                           :type "Dog"
                                                           :age 3}})
                 (update :body #(encoders/transit-decode (input-stream->byte-array %) :json))))))


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

(if-bb
    nil
  (deftest babashka-test
    (if (not (fs/which "bb"))
      (println "babashka not installed, skipping test")
      (process/shell "bb" "-e" "(require '[babashka.classpath :as cp] '[babashka.process :as process])
      (let [classpath (str/trim (:out (process/sh \"lein classpath\")))
            split (cp/split-classpath classpath)
            without-spec (remove #(str/includes? % \"spec.alpha\") split)
            with-test (cons \"test\" without-spec)]
        (doseq [cp with-test]
          (cp/add-classpath cp)))
      (require '[clojure.test] '[martian.babashka.http-client-test])
      (let [{:keys [error fail]} (clojure.test/run-tests 'martian.babashka.http-client-test)]
        (when (or (pos? error) (pos? fail)) (System/exit 1)))"))))
