(ns martian.test-test
  (:require [martian.core :as martian]
            [martian.test :as martian-test]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop #?@(:cljs [:include-macros true])]
            [clojure.test.check.clojure-test :as tct]
            [schema.core :as s]
            #?(:clj [martian.httpkit :as martian-http])
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is async]])
            #?(:cljs [cljs.core.async :as a]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

#?(:cljs
   (def Throwable js/Error))

(def swagger-definition
  {:paths {(keyword "/pets/{id}") {:get {:operationId "load-pet"
                                         :parameters [{:in "path"
                                                       :name "id"
                                                       :type "integer"
                                                       :required true}]
                                         :responses {:200 {:description "A pet"
                                                           :schema {:$ref "#/definitions/Pet"}}
                                                     :404 {:schema {:type "string"}}}}}}
   :definitions {:Pet {:type "object"
                       :properties {:id {:type "integer"
                                         :required true}
                                    :name {:type "string"
                                           :required true}
                                    :timestamp {:type "date-time"
                                                :required true}}}}})

(deftest generate-response-test
  (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
              (martian-test/respond-with-generated {:load-pet :random}))]

    (is (thrown-with-msg? Throwable #"Could not coerce value to schema"
                          (martian/response-for m :load-pet {:id "abc"})))

    (is (nil? (s/check (s/conditional
                        #(= 200 (:status %)) {:status (s/eq 200)
                                              :body {:id s/Int
                                                     :name s/Str
                                                     :timestamp s/Inst}}
                        #(= 404 (:status %)) {:status (s/eq 404)
                                              :body s/Str})
                       (martian/response-for m :load-pet {:id 123}))))))

(deftest generate-successful-response-test
  (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
              (martian-test/respond-with-generated {:load-pet :success}))]

    (is (nil? (s/check {:status (s/eq 200)
                        :body {:id s/Int
                               :name s/Str
                               :timestamp s/Inst}}
                       (martian/response-for m :load-pet {:id 123}))))))

(deftest generate-error-response-test
  (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
              (martian-test/respond-with-generated {:load-pet :error}))]

    (is (nil? (s/check {:status (s/eq 404)
                        :body s/Str}
                       (martian/response-for m :load-pet {:id 123}))))))

(defn fn-to-test
  "This is a production function which uses Martian and makes some decisions based on the response.
   This would normally be hard to test, requiring a stub server or mocking"
  [m]
  (let [{:keys [status body]} (martian/response-for m :load-pet {:id 123})]
    (when (= 200 status)
      body)))

(deftest test-check-test
  (let [m (martian/bootstrap-swagger "https://api.com" swagger-definition)
        p (prop/for-all [response (martian-test/response-generator m :load-pet)]
            (let [output (fn-to-test (martian-test/respond-with-constant m {:load-pet response}))]
              (if (not= 200 (:status response))
                (nil? output)
                (not (nil? output)))))]

    (tct/assert-check (tc/quick-check 100 p))))

(deftest respond-with-constant-test
  (testing "responding with value"
    (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                (martian-test/respond-with {:load-pet {:status 200
                                                       :body "constant"}}))]

      (is (= {:status 200
              :body "constant"}
             (martian/response-for m :load-pet {:id 123})))))

  (testing "responding via function"
    (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                (martian-test/respond-with {:load-pet (fn [request]
                                                        {:status 200
                                                         :body (select-keys request [:method])})}))]

      (is (= {:status 200
              :body {:method :get}}
             (martian/response-for m :load-pet {:id 123}))))))

(deftest respond-with-contextual-test
  (testing "responding via context"
    (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                (martian-test/respond-with (fn [ctx]
                                             {:status 200
                                              :body ctx})))
          response (martian/response-for m :load-pet {:id 123})]

      (is (= 200 (:status response)))
      (is (= {:path "/pets/:id"
              :route-name :load-pet}
             (-> response :body :handler (select-keys [:path :route-name]))))
      (is (= {:id 123}
             (-> response :body :params)))
      (is (= {:method :get
              :url "https://api.com/pets/123"}
             (-> response :body :request))))))

(deftest simulate-implementation-responses-test
  #?(:clj
     (testing "clj-http"
       (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                   (martian-test/respond-as :clj-http)
                   (martian-test/respond-with-generated {:load-pet :success}))]

         (is (= 200 (:status (martian/response-for m :load-pet {:id 123})))))))

  #?(:clj
     (testing "httpkit"
       (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                   (martian-test/respond-as :httpkit)
                   (martian-test/respond-with-generated {:load-pet :success}))]

         (is (= 200 (:status @(martian/response-for m :load-pet {:id 123})))))))

  #?(:clj
     (testing "hato"
       (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                   (martian-test/respond-as :hato)
                   (martian-test/respond-with-generated {:load-pet :success}))]

         (is (= 200 (:status (martian/response-for m :load-pet {:id 123})))))))

  #?(:clj
     (testing "babashka.http-client"
       (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                   (martian-test/respond-as :babashka.http-client)
                   (martian-test/respond-with-generated {:load-pet :success}))]

         (is (= 200 (:status (martian/response-for m :load-pet {:id 123})))))))

  #?(:cljs
     (testing "cljs-http"
       (async done
              (go
                (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                            (martian-test/respond-as :cljs-http)
                            (martian-test/respond-with-generated {:load-pet :success}))]

                  (is (= 200 (:status (a/<! (martian/response-for m :load-pet {:id 123})))))
                  (done))))))

  #?(:cljs
     (testing "cljs-http-promise"
       (async done
              (let [m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition)
                          (martian-test/respond-as :cljs-http-promise)
                          (martian-test/respond-with-generated {:load-pet :success}))]

                (.then (martian/response-for m :load-pet {:id 123})
                       (fn [response]
                         (is (= 200 (:status response)))
                         (done))))))))

#?(:clj
   (deftest test-pedestal-api
     ;; this is an example of how to take a real martian, bootstrapped from a real source, and use it in testing
     ;; the respond-with fn removes http interceptors and replaces them with the test interceptors

     (let [real-martian (martian-http/bootstrap-swagger "https://petstore.swagger.io/v2/swagger.json")]

       (let [test-martian (martian-test/respond-with-generated real-martian {:get-pet-by-id :success})]

         (is (every? #{"martian.interceptors" "martian.test"} (map (comp namespace :name) (:interceptors test-martian))))
         (is (contains? (set (:interceptors test-martian)) martian-test/httpkit-responder))
         (is (= 200 (:status @(martian/response-for test-martian :get-pet-by-id {:pet-id 123})))))

       (let [test-martian (martian-test/respond-with-generated real-martian {:get-pet-by-id :error})]
         (is (contains? #{400 404 500} (:status @(martian/response-for test-martian :get-pet-by-id {:pet-id 123}))))))))

(deftest interceptors-order-test
  (let [int-chain (atom nil)
        chain-spy {:name ::chain-spy
                   :enter (fn [{:keys [tripod.context/queue] :as ctx}]
                            (reset! int-chain (mapv :name queue))
                            ctx)}
        m (-> (martian/bootstrap-swagger "https://api.com" swagger-definition
                                         {:interceptors (cons chain-spy martian/default-interceptors)})
              (martian-test/respond-with-generated {:load-pet :success}))]
    (is (= {:method :get
            :url "https://api.com/pets/123"}
           (martian/request-for m :load-pet {:id 123})))
    (is (= [:martian.interceptors/keywordize-params
            :martian.interceptors/method
            :martian.interceptors/url
            :martian.interceptors/query-params
            :martian.interceptors/body-params
            :martian.interceptors/form-params
            :martian.interceptors/header-params
            :martian.interceptors/enqueue-route-specific-interceptors
            :martian.test/generate-responses
            ;; this one is added by `request-for` and should go last!
            :martian.interceptors/request-only-handler]
           @int-chain))))
