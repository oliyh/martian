(ns martian.cljs-http-test
  (:require [cljs.core.async :refer [<!]]
            [cljs.test :refer-macros [async deftest is testing]]
            [martian.cljs-http :as martian-http]
            [martian.core :as martian]
            [martian.interceptors :as i]
            [matcher-combinators.test])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [martian.file :refer [load-local-resource]]))

(def swagger-url "http://localhost:8888/swagger.json")
(def openapi-url "http://localhost:8888/openapi.json")
(def openapi-test-url "http://localhost:8888/openapi-test.json")
(def openapi-coercions-url "http://localhost:8888/openapi-coercions.json")

(deftest swagger-http-test
  (async done
    (go (let [m (<! (martian-http/bootstrap-swagger swagger-url))]

          (let [response (<! (martian/response-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                                        :type "Dog"
                                                                        :age 3}}))]
            (is (= {:status 201
                    :body {:id 123}}
                   (select-keys response [:status :body]))))

          (let [response (<! (martian/response-for m :get-pet {:id 123}))]
            (is (= {:name "Doggy McDogFace"
                    :type "Dog"
                    :age 3}
                   (:body response)))))
        (done))))

(deftest openapi-bootstrap-test
  (async done
    (go (let [m (<! (martian-http/bootstrap-openapi openapi-url))]

          (is (= "https://sandbox.example.com"
                 (:api-root (<! (martian-http/bootstrap-openapi openapi-test-url))))
              "check absolute server url")

          (is (= "https://sandbox.com"
                 (:api-root (<! (martian-http/bootstrap-openapi openapi-test-url {:server-url "https://sandbox.com"}))))
              "check absolute server url via opts")

          (is (= "http://localhost:8888/v3.1"
                 (:api-root (<! (martian-http/bootstrap-openapi openapi-test-url {:server-url "/v3.1"}))))
              "check relative server url via opts")

          (is (= "http://localhost:8888/openapi/v3"
                 (:api-root m)))

          (is (contains? (set (map first (martian/explore m)))
                         :get-order-by-id)))
        (done))))

(deftest local-file-test
  (let [m (martian/bootstrap-openapi "https://sandbox.example.com" (load-local-resource "public/openapi-test.json") martian-http/default-opts)]
    (is (= "https://sandbox.example.com" (:api-root m)))
    (is (= [[:list-items "Gets a list of items."]]
           (martian/explore m)))))

(deftest supported-content-types-test
  (async done
    (go (let [m (<! (martian-http/bootstrap-openapi openapi-url))]
          (is (= {:encodes #{"application/transit+json"
                             "application/json"
                             "application/edn"
                             "application/x-www-form-urlencoded"}
                  :decodes #{"application/transit+json"
                             "application/json"
                             "application/edn"
                             "application/x-www-form-urlencoded"}}
                 (i/supported-content-types (:interceptors m)))))
        (done))))

;; TODO: The `cljs-http` uses `:response-type` request key for output coercion.

(deftest response-coercion-edn-test
  (async done
    (go (testing "application/edn"
          (let [m (<! (martian-http/bootstrap-openapi openapi-coercions-url))]
            (is (match?
                  {:headers {"Accept" "application/edn"}
                   #_#_:response-type :default}
                  (martian/request-for m :get-edn)))
            (is (match?
                  {:status 200
                   :headers {"content-type" "application/edn;charset=UTF-8"}
                   :body {:message "Here's some text content"}}
                  (<! (martian/response-for m :get-edn))))))
        (done))))

(deftest response-coercion-json-test
  (async done
    (go (testing "application/json"
          (let [m (<! (martian-http/bootstrap-openapi openapi-coercions-url))]
            (is (match?
                  {:headers {"Accept" "application/json"}
                   #_#_:response-type :default}
                  (martian/request-for m :get-json)))
            (is (match?
                  {:status 200
                   :headers {"content-type" "application/json;charset=utf-8"}
                   :body {:message "Here's some text content"}}
                  (<! (martian/response-for m :get-json))))))
        (done))))

(deftest response-coercion-transit+json-test
  (async done
    (go (testing "application/transit+json"
          (let [m (<! (martian-http/bootstrap-openapi openapi-coercions-url))]
            (is (match?
                  {:headers {"Accept" "application/transit+json"}
                   #_#_:response-type :default}
                  (martian/request-for m :get-transit+json)))
            (is (match?
                  {:status 200
                   :headers {"content-type" "application/transit+json;charset=UTF-8"}
                   :body {:message "Here's some text content"}}
                  (<! (martian/response-for m :get-transit+json))))))
        (done))))

(deftest response-coercion-form-data-test
  (async done
    (go (testing "application/x-www-form-urlencoded"
          (let [m (<! (martian-http/bootstrap-openapi openapi-coercions-url))]
            (is (match?
                  {:headers {"Accept" "application/x-www-form-urlencoded"}
                   #_#_:response-type :default}
                  (martian/request-for m :get-form-data)))
            ;; TODO: Fails due to a raw type mismatch (does not apply encoder).
            (is (match?
                  {:status 200
                   :headers {"content-type" "application/x-www-form-urlencoded"}
                   :body {:message "Here's some text content"}
                   #_"message=Here%27s+some+text+content"}
                  (<! (martian/response-for m :get-form-data))))))
        (done))))

(deftest response-coercion-something-test
  (async done
    (go (testing "multiple response content types (default encoders order)"
          (let [m (<! (martian-http/bootstrap-openapi openapi-coercions-url))]
            (is (match?
                  {:produces ["application/transit+json"]}
                  (martian/handler-for m :get-something)))
            (is (match?
                  {:headers {"Accept" "application/transit+json"}
                   #_#_:response-type :default}
                  (martian/request-for m :get-something)))
            (is (match?
                  {:status 200
                   :headers {"content-type" "application/transit+json;charset=UTF-8"}
                   :body {:message "Here's some text content"}}
                  (<! (martian/response-for m :get-something))))))
        (done))))

(deftest response-coercion-anything-test
  (async done
    (go (testing "any response content type (operation with '*/*' content)"
          (let [m (<! (martian-http/bootstrap-openapi openapi-coercions-url))]
            (is (match?
                  {:produces []}
                  (martian/handler-for m :get-anything)))
            (let [request (martian/request-for m :get-anything)]
              #_(is (= :default (:response-type request))
                    "The response auto-coercion is set")
              (is (not (contains? (:headers request) "Accept"))
                  "The 'Accept' request header is absent"))
            (is (match?
                  {:status 200
                   :headers {"content-type" "application/json;charset=utf-8"}
                   :body {:message "Here's some text content"}}
                  (<! (martian/response-for m :get-anything))))))
        (done))))
