(ns martian.cljs-http-test
  (:require [cljs.core.async :refer [<!]]
            [cljs.test :refer-macros [async deftest is testing]]
            [clojure.string :as str]
            [martian.cljs-http :as martian-http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
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
          (testing "server responses"
            (is (match?
                  {:status 201
                   :body {:id 123}}
                  (<! (martian/response-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                                 :type "Dog"
                                                                 :age 3}}))))
            (is (match?
                  {:body {:name "Doggy McDogFace"
                          :type "Dog"
                          :age 3}}
                  (<! (martian/response-for m :get-pet {:id 123}))))))
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
          (is (= {:encodes ["application/transit+json"
                            "application/edn"
                            "application/json"
                            "application/x-www-form-urlencoded"
                            "text/plain"
                            "application/octet-stream"]
                  :decodes ["application/transit+json"
                            "application/edn"
                            "application/json"
                            "application/x-www-form-urlencoded"
                            "text/plain"
                            "application/octet-stream"]}
                 (i/supported-content-types (:interceptors m)))))
        (done))))

(deftest response-coercion-edn-test
  (async done
    (go (testing "application/edn"
          (let [m (<! (martian-http/bootstrap-openapi openapi-coercions-url))]
            (is (match?
                  {:headers {"Accept" "application/edn"}
                   :response-type :default}
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
                   :response-type :default}
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
                   :response-type :default}
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
                   :response-type :text}
                  (martian/request-for m :get-form-data)))
            (is (match?
                  {:status 200
                   :headers {"content-type" "application/x-www-form-urlencoded"}
                   :body {:message "Here's some text content"}}
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
                   :response-type :default}
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
              (is (= :default (:response-type request))
                  "The response auto-coercion is set")
              (is (not (contains? (:headers request) "Accept"))
                  "The 'Accept' request header is absent"))
            (is (match?
                  {:status 200
                   :headers {"content-type" "application/json;charset=utf-8"}
                   :body {:message "Here's some text content"}}
                  (<! (martian/response-for m :get-anything))))))
        (done))))

(deftest response-coercion-custom-encoding-test
  (async done
    (go (testing "custom encoding (application/magical+json)"
          (let [magical-encoder {:encode (comp str/reverse encoders/json-encode)
                                 :decode (comp encoders/json-decode str/reverse)
                                 :as :string}
                request-encoders (assoc (encoders/default-encoders)
                                   "application/magical+json" magical-encoder)
                response-encoders (assoc martian-http/default-response-encoders
                                    "application/magical+json" magical-encoder)
                m (<! (martian-http/bootstrap-openapi
                        openapi-coercions-url {:request-encoders request-encoders
                                               :response-encoders response-encoders}))]
            (is (match?
                  {:headers {"Accept" "application/magical+json"}
                   :response-type :text}
                  (martian/request-for m :get-magical)))
            (is (match?
                  {:status 200
                   :headers {"content-type" "application/magical+json"}
                   :body {:message "Here's some text content"}}
                  (<! (martian/response-for m :get-magical))))))
        (done))))
