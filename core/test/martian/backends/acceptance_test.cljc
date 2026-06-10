(ns martian.backends.acceptance-test
  "Runs the same OpenAPI/Swagger specs through Martian with each schema
   backend, asserting identical observable behaviour: URLs, request maps,
   parameter coercion, key aliasing, defaults and response validation.

   Every backend registered in `backends` must pass every assertion with
   the same expected values — this is the contract that lets users switch
   schema backends without changing how they use Martian."
  (:require [martian.core :as martian]
            [martian.interceptors :as interceptors]
            [martian.backends.malli :as malli]
            [martian.backends.plumatic :as plumatic]
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

#?(:cljs
   (def Throwable js/Error))

(def backends
  {"plumatic" plumatic/backend
   "malli"    malli/backend})

;; ---------------------------------------------------------------------------
;; Swagger (2.x) spec
;; ---------------------------------------------------------------------------

(def swagger-definition
  {:paths {(keyword "/pets/{id}") {:get {:operationId "load-pet"
                                         :summary "Loads a pet by id"
                                         :parameters [{:name "id"
                                                       :in "path"
                                                       :required true
                                                       :type "integer"}]
                                         :responses {:200 {:description "The pet"
                                                           :schema {:$ref "#/definitions/Pet"}}}}
                                   :put {:operationId "update-pet"
                                         :parameters [{:name "id"
                                                       :in "path"
                                                       :required true
                                                       :type "integer"}
                                                      {:name "name"
                                                       :in "formData"
                                                       :type "string"
                                                       :required true}
                                                      {:name "X-AuthToken"
                                                       :in "header"
                                                       :type "string"
                                                       :required true}]}}
           (keyword "/pets/")     {:get {:operationId "all-pets"
                                         :parameters [{:name "sort"
                                                       :in "query"
                                                       :enum ["desc" "asc"]
                                                       :required false}
                                                      {:name "tags"
                                                       :in "query"
                                                       :type "array"
                                                       :collectionFormat "csv"
                                                       :items {:type "string"}
                                                       :required false}]
                                         :responses {:200 {:description "All pets"
                                                           :schema {:type "array"
                                                                    :items {:$ref "#/definitions/Pet"}}}}}
                                   :post {:operationId "create-pet"
                                          :parameters [{:name "Pet"
                                                        :in "body"
                                                        :required true
                                                        :schema {:$ref "#/definitions/Pet"}}]}}
           (keyword "/orders/")   {:post {:operationId "create-orders"
                                          :parameters [{:name "order-ids"
                                                        :in "body"
                                                        :required true
                                                        :type "array"
                                                        :items {:type "string"}}]}}}

   :definitions {:Pet {:type "object"
                       :required ["id" "name" "emailAddress" "address"]
                       :properties {:id {:type "integer"}
                                    :name {:type "string"}
                                    :emailAddress {:type "string"}
                                    :nickName {:type "string"}
                                    :tags {:type "array"
                                           :items {:type "string"}}
                                    :address {:$ref "#/definitions/Address"}}}
                 :Address {:type "object"
                           :required ["city"]
                           :properties {:city {:type "string"
                                               :default "trondheim"}
                                        :zipCode {:type "string"}}}}})

(defn- swagger-martian [backend & [opts]]
  (martian/bootstrap-swagger "https://api.org" swagger-definition
                             (merge {:schema-backend backend} opts)))

(deftest swagger-url-for-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (swagger-martian backend)]
        (is (= "https://api.org/pets/123"
               (martian/url-for m :load-pet {:id 123})
               (martian/url-for m :load-pet {:id "123"})))))))

(deftest swagger-path-params-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (swagger-martian backend)]
        (testing "coerces path params"
          (is (= {:method :get
                  :url "https://api.org/pets/123"}
                 (martian/request-for m :load-pet {:id 123})
                 (martian/request-for m :load-pet {:id "123"}))))

        (testing "throws when required path param is missing"
          (is (thrown? Throwable (martian/request-for m :load-pet {}))))

        (testing "throws when path param cannot be coerced"
          (is (thrown? Throwable (martian/request-for m :load-pet {:id "abc"}))))))))

(deftest swagger-query-params-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (swagger-martian backend)]
        (testing "optional query params can be omitted"
          (is (= {:method :get
                  :url "https://api.org/pets/"}
                 (martian/request-for m :all-pets {}))))

        (testing "enums accept strings and keywords"
          (is (= {:method :get
                  :url "https://api.org/pets/"
                  :query-params {:sort "asc"}}
                 (martian/request-for m :all-pets {:sort "asc"})
                 (martian/request-for m :all-pets {:sort :asc}))))

        (testing "csv collection format joins array values"
          (is (= {:method :get
                  :url "https://api.org/pets/"
                  :query-params {:tags "small,fluffy"}}
                 (martian/request-for m :all-pets {:tags ["small" "fluffy"]}))))))))

(deftest swagger-body-params-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (swagger-martian backend)]
        (testing "coerces body params, renaming aliased keys"
          (is (= {:method :post
                  :url "https://api.org/pets/"
                  :body {:id 123
                         :name "charlie"
                         :emailAddress "charlie@pets.org"
                         :address {:city "oslo"
                                   :zipCode "0150"}}}
                 (martian/request-for m :create-pet {:pet {:id "123"
                                                           :name "charlie"
                                                           :email-address "charlie@pets.org"
                                                           :address {:city "oslo"
                                                                     :zip-code "0150"}}})
                 (martian/request-for m :create-pet {::martian/body {:id 123
                                                                     :name "charlie"
                                                                     :email-address "charlie@pets.org"
                                                                     :address {:city "oslo"
                                                                               :zip-code "0150"}}}))))

        (testing "drops keys that are not in the schema"
          (is (= {:method :post
                  :url "https://api.org/pets/"
                  :body {:id 1
                         :name "rex"
                         :emailAddress "rex@pets.org"
                         :address {:city "bergen"}}}
                 (martian/request-for m :create-pet {:pet {:id 1
                                                           :name "rex"
                                                           :email-address "rex@pets.org"
                                                           :address {:city "bergen"}
                                                           :favourite-stick "birch"}}))))

        (testing "throws when a required body key is missing"
          (is (thrown? Throwable
                       (martian/request-for m :create-pet {:pet {:id 1 :name "rex"}}))))

        (testing "coerces primitive body arrays, turning keywords into strings"
          (is (= {:method :post
                  :url "https://api.org/orders/"
                  :body ["order-1" "order-2"]}
                 (martian/request-for m :create-orders {:order-ids ["order-1" "order-2"]})
                 (martian/request-for m :create-orders {:order-ids [:order-1 :order-2]}))))))))

(deftest swagger-form-and-header-params-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (swagger-martian backend)]
        (testing "coerces form params and aliased headers"
          (is (= {:method :put
                  :url "https://api.org/pets/5"
                  :form-params {:name "nigel"}
                  :headers {"X-AuthToken" "abc-123"}}
                 (martian/request-for m :update-pet {:id 5
                                                     :name "nigel"
                                                     :x-auth-token "abc-123"}))))))))

(deftest swagger-defaults-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (swagger-martian backend {:use-defaults? true})]
        (testing "fills in default values for missing keys"
          (is (= {:method :post
                  :url "https://api.org/pets/"
                  :body {:id 1
                         :name "rex"
                         :emailAddress "rex@pets.org"
                         :address {:city "trondheim"}}}
                 (martian/request-for m :create-pet {:pet {:id 1
                                                           :name "rex"
                                                           :email-address "rex@pets.org"
                                                           :address {}}}))))))))

(defn- stub-response [response]
  {:name ::stub-response
   :enter (fn [ctx] (assoc ctx :response response))})

(defn- validating-martian [backend response]
  (swagger-martian backend
                   {:interceptors (concat martian/default-interceptors
                                          [(interceptors/validate-response-body {:strict? true})
                                           (stub-response response)])}))

(deftest swagger-response-validation-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (testing "valid response body passes validation"
        (let [response {:status 200
                        :body {:id 1
                               :name "charlie"
                               :emailAddress "charlie@pets.org"
                               :address {:city "oslo"}}}
              m (validating-martian backend response)]
          (is (= response (martian/response-for m :load-pet {:id 1})))))

      (testing "invalid response body fails validation"
        (let [m (validating-martian backend {:status 200
                                             :body {:id "not-an-int"}})]
          (is (thrown? Throwable (martian/response-for m :load-pet {:id 1})))))

      (testing "unknown response status fails validation in strict mode"
        (let [m (validating-martian backend {:status 500
                                             :body "boom"})]
          (is (thrown? Throwable (martian/response-for m :load-pet {:id 1}))))))))

(deftest swagger-explore-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (swagger-martian backend)]
        (is (= [[:load-pet "Loads a pet by id"]
                [:update-pet nil]
                [:all-pets nil]
                [:create-pet nil]
                [:create-orders nil]]
               (martian/explore m)))

        (testing "returns are keyed by response status"
          (is (= [200] (keys (:returns (martian/explore m :load-pet))))))))))

;; ---------------------------------------------------------------------------
;; OpenAPI (3.x) spec
;; ---------------------------------------------------------------------------

(def ^:private any-content-type (keyword "*/*"))

(def openapi-definition
  {:openapi "3.0.0"
   :paths {(keyword "/animals/{id}") {:get {:operationId "load-animal"
                                            :parameters [{:name "id"
                                                          :in "path"
                                                          :required true
                                                          :schema {:type "integer"}}]
                                            :responses {:200 {:content {any-content-type {:schema {:$ref "#/components/schemas/Animal"}}}}}}}
           (keyword "/animals/")     {:post {:operationId "create-animal"
                                             :parameters [{:name "verbose"
                                                           :in "query"
                                                           :schema {:type "boolean"}}]
                                             :requestBody {:required true
                                                           :content {any-content-type {:schema {:$ref "#/components/schemas/Animal"}}}}
                                             :responses {:2XX {:content {any-content-type {:schema {:$ref "#/components/schemas/Animal"}}}}}}}}
   :components {:schemas {:Animal {:type "object"
                                   :required ["name"]
                                   :properties {:name {:type "string"}
                                                :goodBoy {:type "boolean"}
                                                :age {:type "integer"
                                                      :nullable true}
                                                :meta {:type "object"
                                                       :additionalProperties true}}}}}})

(defn- openapi-martian [backend & [opts]]
  (martian/bootstrap-openapi "https://api.org" openapi-definition
                             (merge {:schema-backend backend} opts)))

(deftest openapi-request-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (let [m (openapi-martian backend)]
        (testing "coerces path params"
          (is (= {:method :get
                  :url "https://api.org/animals/42"}
                 (martian/request-for m :load-animal {:id "42"}))))

        (testing "coerces request bodies, renaming aliased keys"
          (is (= {:method :post
                  :url "https://api.org/animals/"
                  :query-params {:verbose true}
                  :body {:name "fido"
                         :goodBoy true
                         :age nil
                         :meta {:chip-id "x1"}}}
                 (martian/request-for m :create-animal {:body {:name "fido"
                                                               :good-boy true
                                                               :age nil
                                                               :meta {:chip-id "x1"}}
                                                        :verbose true}))))

        (testing "throws when a required body key is missing"
          (is (thrown? Throwable
                       (martian/request-for m :create-animal {:body {:good-boy true}}))))))))

(deftest openapi-response-validation-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (testing "response statuses match range schemas like 2XX"
        (let [response {:status 201
                        :body {:name "fido"}}
              m (openapi-martian backend
                                 {:interceptors (concat martian/default-interceptors
                                                        [(interceptors/validate-response-body {:strict? true})
                                                         (stub-response response)])})]
          (is (= response (martian/response-for m :create-animal {:body {:name "fido"}}))))))))

(deftest validate-handlers-test
  (doseq [[backend-name backend] backends]
    (testing (str backend-name " backend")
      (testing "early handler validation works"
        (is (some? (swagger-martian backend {:validate-handlers? true})))
        (is (some? (openapi-martian backend {:validate-handlers? true})))))))
