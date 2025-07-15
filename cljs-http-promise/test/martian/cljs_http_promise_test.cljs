(ns martian.cljs-http-promise-test
  (:require [cljs.test :refer-macros [async deftest is testing]]
            [clojure.string :as str]
            [martian.cljs-http-promise :as martian-http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.interceptors :as i]
            [matcher-combinators.test]
            [promesa.core :as prom])
  (:require-macros [martian.file :refer [load-local-resource]]))

(def swagger-url "http://localhost:8888/swagger.json")
(def openapi-url "http://localhost:8888/openapi.json")
(def openapi-test-url "http://localhost:8888/openapi-test.json")
(def openapi-coercions-url "http://localhost:8888/openapi-coercions.json")

(defn report-error-and-throw [err]
  (cljs.test/report
    {:type :error
     :message (.-message err)
     :actual err
     :expected :none})
  (throw err))

(deftest swagger-http-test
  (async done
    (-> (martian-http/bootstrap-swagger swagger-url)
        (prom/then (fn [m]
                     (testing "server responses"
                       (prom/let [create-response (martian/response-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                                                             :type "Dog"
                                                                                             :age 3}})
                                  get-response (martian/response-for m :get-pet {:id 123})]
                         (is (match?
                               {:status 201
                                :body {:id 123}}
                               create-response))
                         (is (match?
                               {:body {:name "Doggy McDogFace"
                                       :type "Dog"
                                       :age 3}}
                               get-response))))))
        (prom/catch report-error-and-throw)
        (prom/finally (fn []
                        (done))))))

(deftest openapi-bootstrap-test
  (async done
    (-> (martian-http/bootstrap-openapi openapi-test-url)
        (prom/then (fn [m]
                     (is (= "https://sandbox.example.com"
                            (:api-root m)) "check absolute server url")))
        (prom/catch report-error-and-throw))

    (-> (martian-http/bootstrap-openapi openapi-test-url {:server-url "https://sandbox.com"})
        (prom/then (fn [m]
                     (is (= "https://sandbox.com"
                            (:api-root m)) "check absolute server url via opts")))
        (prom/catch report-error-and-throw))

    (-> (martian-http/bootstrap-openapi openapi-test-url {:server-url "/v3.1"})
        (prom/then (fn [m]
                     (is (= "http://localhost:8888/v3.1"
                            (:api-root m)) "check relative server url via opts")))
        (prom/catch report-error-and-throw))

    (-> (prom/let [m (martian-http/bootstrap-openapi openapi-url)]
          (is (= "http://localhost:8888/openapi/v3" (:api-root m)))
          (is (contains? (set (map first (martian/explore m))) :get-order-by-id)))
        (prom/catch report-error-and-throw)
        (prom/finally (fn []
                        (done))))))

(deftest local-file-test
  (let [m (martian/bootstrap-openapi "https://sandbox.example.com" (load-local-resource "public/openapi-test.json") martian-http/default-opts)]
    (is (= "https://sandbox.example.com" (:api-root m)))
    (is (= [[:list-items "Gets a list of items."]]
           (martian/explore m)))))

(deftest supported-content-types-test
  (async done
    (-> (prom/let [m (martian-http/bootstrap-openapi openapi-url)]
          (is (= {:encodes ["application/transit+json"
                            "application/edn"
                            "application/json"
                            "application/x-www-form-urlencoded"]
                  :decodes ["application/transit+json"
                            "application/edn"
                            "application/json"
                            "application/x-www-form-urlencoded"]}
                 (i/supported-content-types (:interceptors m)))))
        (prom/catch report-error-and-throw)
        (prom/finally (fn []
                        (done))))))

(deftest response-coercion-edn-test
  (async done
    (testing "application/edn"
      (-> (prom/let [m (martian-http/bootstrap-openapi openapi-coercions-url)]
            (is (match?
                  {:headers {"Accept" "application/edn"}
                   :response-type :default}
                  (martian/request-for m :get-edn)))
            (-> (martian/response-for m :get-edn)
                (prom/then (fn [response]
                             (is (match?
                                   {:status 200
                                    :headers {"content-type" "application/edn;charset=UTF-8"}
                                    :body {:message "Here's some text content"}}
                                   response))))))
          (prom/catch report-error-and-throw)
          (prom/finally (fn []
                          (done)))))))

(deftest response-coercion-json-test
  (async done
    (testing "application/json"
      (-> (prom/let [m (martian-http/bootstrap-openapi openapi-coercions-url)]
            (is (match?
                  {:headers {"Accept" "application/json"}
                   :response-type :default}
                  (martian/request-for m :get-json)))
            (-> (martian/response-for m :get-json)
                (prom/then (fn [response]
                             (is (match?
                                   {:status 200
                                    :headers {"content-type" "application/json;charset=utf-8"}
                                    :body {:message "Here's some text content"}}
                                   response))))))
          (prom/catch report-error-and-throw)
          (prom/finally (fn []
                          (done)))))))

(deftest response-coercion-transit+json-test
  (async done
    (testing "application/transit+json"
      (-> (prom/let [m (martian-http/bootstrap-openapi openapi-coercions-url)]
            (is (match?
                  {:headers {"Accept" "application/transit+json"}
                   :response-type :default}
                  (martian/request-for m :get-transit+json)))
            (-> (martian/response-for m :get-transit+json)
                (prom/then (fn [response]
                             (is (match?
                                   {:status 200
                                    :headers {"content-type" "application/transit+json;charset=UTF-8"}
                                    :body {:message "Here's some text content"}}
                                   response))))))
          (prom/catch report-error-and-throw)
          (prom/finally (fn []
                          (done)))))))

(deftest response-coercion-form-data-test
  (async done
    (testing "application/x-www-form-urlencoded"
      (-> (prom/let [m (martian-http/bootstrap-openapi openapi-coercions-url)]
            (is (match?
                  {:headers {"Accept" "application/x-www-form-urlencoded"}
                   :response-type :text}
                  (martian/request-for m :get-form-data)))
            (-> (martian/response-for m :get-form-data)
                (prom/then (fn [response]
                             (is (match?
                                   {:status 200
                                    :headers {"content-type" "application/x-www-form-urlencoded"}
                                    :body {:message "Here's some text content"}}
                                   response))))))
          (prom/catch report-error-and-throw)
          (prom/finally (fn []
                          (done)))))))

(deftest response-coercion-something-test
  (async done
    (testing "multiple response content types (default encoders order)"
      (-> (prom/let [m (martian-http/bootstrap-openapi openapi-coercions-url)]
            (is (match?
                  {:produces ["application/transit+json"]}
                  (martian/handler-for m :get-something)))
            (is (match?
                  {:headers {"Accept" "application/transit+json"}
                   :response-type :default}
                  (martian/request-for m :get-something)))
            (-> (martian/response-for m :get-something)
                (prom/then (fn [response]
                             (is (match?
                                   {:status 200
                                    :headers {"content-type" "application/transit+json;charset=UTF-8"}
                                    :body {:message "Here's some text content"}}
                                   response))))))
          (prom/catch report-error-and-throw)
          (prom/finally (fn []
                          (done)))))))

(deftest response-coercion-anything-test
  (async done
    (testing "any response content type (operation with '*/*' content)"
      (-> (prom/let [m (martian-http/bootstrap-openapi openapi-coercions-url)]
            (is (match?
                  {:produces []}
                  (martian/handler-for m :get-anything)))
            (let [request (martian/request-for m :get-anything)]
              (is (= :default (:response-type request))
                  "The response auto-coercion is set")
              (is (not (contains? (:headers request) "Accept"))
                  "The 'Accept' request header is absent"))
            (-> (martian/response-for m :get-anything)
                (prom/then (fn [response]
                             (is (match?
                                   {:status 200
                                    :headers {"content-type" "application/json;charset=utf-8"}
                                    :body {:message "Here's some text content"}}
                                   response))))))
          (prom/catch report-error-and-throw)
          (prom/finally (fn []
                          (done)))))))

;; FIXME: No matching clause: :magic.
(deftest response-coercion-custom-encoding-test
  (async done
    (testing "custom encoding (application/magical+json)"
      (let [magical-encoder {:encode (comp str/reverse encoders/json-encode)
                             :decode (comp encoders/json-decode str/reverse)
                             :as :magic}
            request-encoders (assoc (encoders/default-encoders)
                               "application/magical+json" magical-encoder)
            response-encoders (assoc martian-http/default-response-encoders
                                "application/magical+json" magical-encoder)]
        (-> (prom/let [m (martian-http/bootstrap-openapi
                           openapi-coercions-url {:request-encoders request-encoders
                                                  :response-encoders response-encoders})]
              (is (match?
                    {:headers {"Accept" "application/magical+json"}
                     :response-type :magic}
                    (martian/request-for m :get-magical)))
              (-> (martian/response-for m :get-magical)
                  (prom/then (fn [response]
                               (is (match?
                                     {:status 200
                                      :headers {"content-type" "application/magical+json"}
                                      :body {:message "Here's some text content"}}
                                     response))))))
            (prom/catch report-error-and-throw)
            (prom/finally (fn []
                            (done))))))))
