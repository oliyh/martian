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

(defn- openapi-responses
  "Returns a Martian with the OpenAPI route `:get-thing`, whose `responses` map
   status keys to the type of their body, or to nil for no body."
  [responses]
  (martian/bootstrap-openapi
   "https://api.com"
   {:openapi "3.0.0"
    :paths {(keyword "/thing")
            {:get {:operationId "get-thing"
                   :responses (into {}
                                    (map (fn [[k body-type]]
                                           [k (if body-type
                                                {:content {(keyword "*/*") {:schema {:type body-type}}}}
                                                {:description "No body"})]))
                                    responses)}}}}))

(defn- generate
  "Returns `n` responses generated for the route `:get-thing` of `m`."
  [m response-type n]
  (let [m (martian-test/respond-with-generated m {:get-thing response-type})]
    (repeatedly n #(martian/response-for m :get-thing {}))))

(defn- statuses [m response-type n]
  (map :status (generate m response-type n)))

(deftest generated-status-ranges-test
  (testing "a range generates statuses in it"
    (is (every? #(<= 200 % 299) (statuses (openapi-responses {:2XX "string"}) :success 50)))
    (is (every? #(<= 300 % 399) (statuses (openapi-responses {:3XX "string"}) :success 50)))
    (is (every? #(<= 400 % 499) (statuses (openapi-responses {:4XX "string"}) :error 50)))
    (is (every? #(<= 500 % 599) (statuses (openapi-responses {:5XX "string"}) :error 50)))
    (is (every? #(<= 100 % 199) (statuses (openapi-responses {:1XX "string"}) :random 50))))

  (testing "a response type that no response can have is an error"
    (is (thrown-with-msg? Throwable #"No response can be generated with a success status"
                          (doall (statuses (openapi-responses {:1XX "string"}) :success 1))))))

(deftest generated-exact-codes-and-ranges-test
  (testing "an exact code wins over the range that covers it"
    (doseq [[range-key exact-codes left response-type] [[:4XX (range 400 490) #(<= 490 % 499) :error]
                                                        [:2XX (range 200 290) #(<= 290 % 299) :success]]]
      (let [exact (into {} (map (fn [code] [(keyword (str code)) "integer"])) exact-codes)
            m (openapi-responses (assoc exact range-key "string"))]
        (is (every? (comp left :status)
                    (filter (comp string? :body) (generate m response-type 200)))
            (name range-key))))))

(deftest generated-default-test
  (testing "`default` generates codes that no exact code declares"
    (let [exact (into {} (map (fn [code] [(keyword (str code)) "integer"])) (range 400 450))
          m (openapi-responses (assoc exact :default "boolean"))]
      (is (every? #(<= 450 (:status %) 599)
                  (filter (comp boolean? :body) (generate m :error 200))))))

  (testing "`default` generates codes that no range covers"
    (is (every? #(<= 500 (:status %) 599)
                (filter (comp boolean? :body)
                        (generate (openapi-responses {:4XX "string" :default "boolean"}) :error 200)))))

  (testing "`default` gives success codes only when no other response does"
    (let [m (openapi-responses {:200 "integer" :default "boolean"})]
      (is (every? #(and (= 200 (:status %)) (integer? (:body %))) (generate m :success 50)))
      (is (every? #(<= 400 (:status %) 599)
                  (filter (comp boolean? :body) (generate m :random 100)))))
    (is (every? #(<= 400 (:status %) 599)
                (filter (comp boolean? :body)
                        (generate (openapi-responses {:2XX "string" :default "boolean"}) :random 100))))
    (is (every? #(<= 200 % 399) (statuses (openapi-responses {:404 "string" :default "boolean"}) :success 50))
        "With only error responses declared, `default` gives the success codes"))

  (testing "`default` alone stands for every response, success or error"
    (doseq [m [(openapi-responses {:default "string"})
               (martian/bootstrap-swagger "https://api.com"
                                          {:paths {(keyword "/thing")
                                                   {:get {:operationId "get-thing"
                                                          :responses {:default {:schema {:type "string"}}}}}}})]]
      (is (every? #(<= 200 % 399) (statuses m :success 50)))
      (is (every? #(<= 400 % 599) (statuses m :error 50)))
      (is (every? #(<= 200 % 599) (statuses m :random 50)))))

  (testing "Swagger's `default` generates codes, not the symbol `default`"
    (let [m (martian/bootstrap-swagger "https://api.com"
                                       {:paths {(keyword "/thing")
                                                {:get {:operationId "get-thing"
                                                       :responses {:200 {:schema {:type "string"}}
                                                                   :default {:schema {:type "string"}}}}}}})]
      (is (every? int? (statuses m :random 50)))
      (is (every? #(<= 400 % 599) (statuses m :error 50))))))

(deftest generated-hand-written-statuses-test
  (let [martian (fn [response-schemas]
                  (martian/bootstrap "https://api.com" [{:route-name :get-thing
                                                         :path-parts ["/thing"]
                                                         :method :get
                                                         :response-schemas response-schemas}]))]
    (testing "a status schema that spans success and error codes generates only the asked ones"
      (is (every? #(<= 200 % 399)
                  (statuses (martian [{:status (s/enum 200 404) :body s/Str}
                                      {:status (s/eq 201) :body s/Int}])
                            :success 100))))

    (testing "any other status schema generates the codes from 100 to 599 it allows"
      (is (every? #(and (int? %) (<= 100 % 599))
                  (statuses (martian [{:status s/Int :body s/Str}]) :random 100))))

    (testing "a response schema without a `:body` key generates a response without one"
      (is (= {:status 200} (first (generate (martian [{:status (s/eq 200)}]) :random 1)))))))

(deftest generated-bodies-test
  (testing "a response without a body gets a nil body"
    (is (= {:status 204 :body nil}
           (first (generate (openapi-responses {:204 nil}) :success 1))))))

(deftest generated-non-status-keys-test
  (testing "a response key that isn't a status, such as an extension, is never generated"
    (is (every? #{200} (statuses (openapi-responses {:200 "string" :x-foo nil}) :random 50)))))

(deftest response-generator-statuses-test
  (let [m (openapi-responses {:2XX "string" :404 "integer" :default "boolean"})
        p (prop/for-all [{:keys [status body]} (martian-test/response-generator m :get-thing)]
            (and (int? status)
                 (cond
                   (string? body)  (<= 200 status 299)
                   (integer? body) (= 404 status)
                   (boolean? body) (and (<= 400 status 599) (not= 404 status)))))]
    (tct/assert-check (tc/quick-check 100 p))))
