(ns martian.clj-http-lite-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [martian.clj-http-lite :as martian-http]
            [martian.core :as martian]
            [martian.encoders :as encoders]
            [martian.server-stub :refer [swagger-url
                                         openapi-url
                                         openapi-test-url
                                         openapi-yaml-url
                                         openapi-test-yaml-url
                                         openapi-coercions-url
                                         with-server]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test]))

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
            (martian/response-for m :create-pet {:pet {:name "Doggy McDogFace"
                                                       :type "Dog"
                                                       :age 3}})))
      (is (match?
            {:body {:name "Doggy McDogFace"
                    :type "Dog"
                    :age 3}}
            (martian/response-for m :get-pet {:id 123}))))))

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

(deftest response-coercion-test
  (let [m (martian-http/bootstrap-openapi openapi-coercions-url)]
    (is (= "http://localhost:8888" (:api-root m)))

    (testing "application/edn"
      (is (match?
            {:headers {"Accept" "application/edn"}
             :as :string}
            (martian/request-for m :get-edn)))
      (is (match?
            {:status 200
             :headers {:content-type "application/edn;charset=UTF-8"}
             :body {:message "Here's some text content"}}
            (martian/response-for m :get-edn))))
    (testing "application/json"
      (is (match?
            {:headers {"Accept" "application/json"}
             :as :string}
            (martian/request-for m :get-json)))
      (is (match?
            {:status 200
             :headers {:content-type "application/json;charset=utf-8"}
             :body {:message "Here's some text content"}}
            (martian/response-for m :get-json))))
    (testing "application/transit+json"
      (is (match?
            {:headers {"Accept" "application/transit+json"}
             :as :string}
            (martian/request-for m :get-transit+json)))
      (is (match?
            {:status 200
             :headers {:content-type "application/transit+json;charset=UTF-8"}
             :body {:message "Here's some text content"}}
            (martian/response-for m :get-transit+json))))
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
            (martian/response-for m :get-transit+msgpack))))
    (testing "application/x-www-form-urlencoded"
      (is (match?
            {:headers {"Accept" "application/x-www-form-urlencoded"}
             :as :string}
            (martian/request-for m :get-form-data)))
      (is (match?
            {:status 200
             :headers {:content-type "application/x-www-form-urlencoded"}
             :body {:message "Here's some text content"}}
            (martian/response-for m :get-form-data))))

    (testing "multiple response content types (default encoders order)"
      (is (match?
            {:produces ["application/transit+json"]}
            (martian/handler-for m :get-something)))
      (is (match?
            {:headers {"Accept" "application/transit+json"}
             :as :string}
            (martian/request-for m :get-something)))
      (is (match?
            {:status 200
             :headers {:content-type "application/transit+json;charset=UTF-8"}
             :body {:message "Here's some text content"}}
            (martian/response-for m :get-something))))

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
            (martian/response-for m :get-anything)))))

  (testing "custom encoding"
    (testing "application/magical+json"
      (let [magical-encoder {:encode (comp str/reverse encoders/json-encode)
                             :decode (comp encoders/json-decode str/reverse)
                             :as :magic}
            encoders (assoc (encoders/default-encoders)
                       "application/magical+json" magical-encoder)
            m (martian-http/bootstrap-openapi
                openapi-coercions-url {:request-encoders encoders
                                       :response-encoders encoders})]
        (is (match?
              {:headers {"Accept" "application/magical+json"}
               :as :magic}
              (martian/request-for m :get-magical)))
        (is (match?
              {:status 200
               :headers {:content-type "application/magical+json"}
               :body {:message "Here's some text content"}}
              (martian/response-for m :get-magical)))))))
