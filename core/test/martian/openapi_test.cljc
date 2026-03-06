(ns martian.openapi-test
  (:require [clojure.test :refer [deftest is testing]]
            [martian.openapi :refer [base-url openapi->handlers]]
            [martian.test-helpers #?@(:clj  [:refer [json-resource yaml-resource]]
                                      :cljs [:refer-macros [json-resource yaml-resource]])]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def openapi-json
  (json-resource "openapi.json"))

(def openapi-2-json
  (json-resource "openapi2.json"))

(def jira-openapi-v3-json
  (json-resource "jira-openapi-v3.json"))

(def kubernetes-openapi-v3-yaml
  (yaml-resource "kubernetes-openapi-v3-converted.yaml"))

(deftest openapi-sanity-check
  (testing "parses each handler"
    (is (= {:summary        "Update an existing pet"
            :description    "Update an existing pet by Id"
            :method         :put
            :produces       ["application/json"]
            :path-schema    nil
            :query-schema   nil
            :form-schema    nil
            :path-parts     ["/pet"]
            :headers-schema nil
            :consumes       ["application/json"]
            :body-schema
            {:body
             {(s/optional-key :id)       s/Int
              :name                      s/Str
              (s/optional-key :category) {(s/optional-key :id)   s/Int
                                          (s/optional-key :name) s/Str}
              :photoUrls                 [s/Str]
              (s/optional-key :tags)     [{(s/optional-key :id)   s/Int
                                           (s/optional-key :name) s/Str}]
              (s/optional-key :status)   (s/enum "sold" "pending" "available")}}
            :route-name     :update-pet
            :response-schemas
            [{:status (s/eq 200)
              :body
              {(s/optional-key :id)       s/Int
               :name                      s/Str
               (s/optional-key :category) {(s/optional-key :id)   s/Int
                                           (s/optional-key :name) s/Str}
               :photoUrls                 [s/Str]
               (s/optional-key :tags)     [{(s/optional-key :id)   s/Int
                                            (s/optional-key :name) s/Str}]
               (s/optional-key :status)   (s/enum "sold" "pending" "available")}}
             {:status (s/eq 400) :body nil}
             {:status (s/eq 404) :body nil}
             {:status (s/eq 405) :body nil}]}

           (-> openapi-json
               (openapi->handlers {:encodes ["application/json" "application/octet-stream"]
                                   :decodes ["application/json" "application/octet-stream"]})
               (->> (filter #(= (:route-name %) :update-pet)))
               first
               (dissoc :openapi-definition)))))

  (testing "chooses the first supported content-type"
    (is (= {:consumes ["application/xml"]
            :produces ["application/json"]}

           (-> openapi-json
               (openapi->handlers {:encodes ["application/xml"]
                                   :decodes ["application/json"]})
               (->> (filter #(= (:route-name %) :update-pet)))
               first
               (select-keys [:consumes :produces]))))))

(deftest openapi-parameters-test
  (testing "parses parameters"
    (is (= {:description nil,
            :method :get,
            :produces ["application/json"],
            :path-schema {:projectId s/Str},
            :query-schema {(s/optional-key :key) (st/default s/Str "some-default-key")},
            :form-schema nil,
            :path-parts ["/project/" :projectKey],
            :headers-schema {(s/optional-key :userAuthToken) s/Str},
            :consumes nil
            :summary "Get specific values from a configuration for a specific project",
            :body-schema nil,
            :route-name :get-project-configuration,
            :response-schemas
            [{:status (s/eq 200), :body s/Str}
             {:status (s/eq 403), :body nil}
             {:status (s/eq 404), :body nil}]}
           (-> openapi-2-json
               (openapi->handlers {:encodes ["application/json" "application/octet-stream"]
                                   :decodes ["application/json" "application/octet-stream"]})
               (->> (filter #(= (:route-name %) :get-project-configuration)))
               first
               (dissoc :openapi-definition))))))

(deftest jira-openapi-v3-test
  (is (= 410
         (-> jira-openapi-v3-json
             (openapi->handlers {:encodes ["application/json"]
                                 :decodes ["application/json"]})
             count))))

(deftest reffed-params-test
  (let [openapi-json
        {:paths {(keyword "/models/{model_id}/{version}")
                 {:get {:operationId "load-models-id-version-get"
                        :summary "Loads a pet by id"
                        :parameters [{:$ref "#/components/parameters/model_id"}
                                     {:$ref "#/components/parameters/version"}]}}}
         :components {:parameters {:model_id {:in "path"
                                              :name "model_id"
                                              :schema {:type "string"}
                                              :required true}
                                   :version {:in "path"
                                             :name "version"
                                             :schema {:type "string"}
                                             :required true}}}}
        [handler] (openapi->handlers openapi-json {:encodes ["application/json"]
                                                   :decodes ["application/json"]})]
    (is (= {:path-parts ["/models/" :model_id "/" :version],
            :path-schema {:model_id s/Str
                          :version s/Str}}
           (select-keys handler [:path-parts :path-schema])))))

(deftest reffed-responses-test
  (let [openapi-json
        {:paths {(keyword "/models")
                 {:get {:operationId "list-models"
                        :summary "Lists models"
                        :responses {:401 {:$ref "#/components/responses/Unauthorized"}
                                    :404 {:$ref "#/components/responses/NotFound"}}}}}
         :components {:responses {:NotFound
                                  {:description "The requested resource was not found."
                                   :content
                                   {:application/json
                                    {:schema {:$ref "#/components/schemas/Error"}}}}
                                  :Unauthorized
                                  {:description "Unauthorized."
                                   :content
                                   {:application/json
                                    {:schema {:$ref "#/components/schemas/Error"}}}}}
                      :schemas {:Error
                                {:type "object"
                                 :properties
                                 {:code
                                  {:description "An enumerated error for machine use.",
                                   :type "integer",
                                   :readOnly true},
                                  :details
                                  {:description "A human-readable description of the error.",
                                   :type "string",
                                   :readOnly true}}}}}}
        [handler] (openapi->handlers openapi-json {:encodes ["application/json"]
                                                   :decodes ["application/json"]})]
    (is (= [{:status (s/eq 401)
             :body {(s/optional-key :code) s/Int (s/optional-key :details) s/Str}}
            {:status (s/eq 404)
             :body {(s/optional-key :code) s/Int (s/optional-key :details) s/Str}}]
           (:response-schemas handler)))))

(deftest schemas-without-type-test
  (let [openapi-json
        {:paths {(keyword "/models")
                 {:get {:operationId "list-models"
                        :summary "Lists models"
                        :responses {:404 {:$ref "#/components/responses/NotFound"}}}}}
         :components {:responses {:NotFound
                                  {:description "The requested resource was not found."
                                   :content
                                   {:application/json
                                    {:schema {:$ref "#/components/schemas/Error"}}}}}
                      :schemas {:Error
                                {:properties
                                 {:code
                                  {:description "An enumerated error for machine use.",
                                   :type "integer",
                                   :readOnly true},
                                  :details
                                  {:description "A human-readable description of the error.",
                                   :type "string",
                                   :readOnly true}}}}}}
        [handler] (openapi->handlers openapi-json {:encodes ["application/json"]
                                                   :decodes ["application/json"]})]
    (is (= [{:status (s/eq 404)
             :body {(s/optional-key :code) s/Int (s/optional-key :details) s/Str}}]
           (:response-schemas handler)))))

(deftest body-object-without-any-parameters-takes-values
  (is (= {:body {s/Any s/Any}}
         (let [[handler] (filter #(= (:route-name %) :patch-core-v-1-namespaced-secret)
                                 (openapi->handlers kubernetes-openapi-v3-yaml
                                                    {:encodes ["application/json-patch+json"]
                                                     :decodes ["application/json"]}))]

           (:body-schema handler)))))

(deftest form-encoded-schemas-test
  (let [openapi-json
        {:paths {(keyword "/models")
                 {:post {:operationId "create-thing"
                         :summary "Creates things"
                         :requestBody {:required true,
                                       :content
                                       {:application/x-www-form-urlencoded
                                        {:schema
                                         {:type "object"
                                          :properties {:foo {:type "string"} :bar {:type "number"}}
                                          :required ["foo" "bar"]}}}}}}}}
        [handler] (openapi->handlers openapi-json {:encodes ["application/x-www-form-urlencoded"]
                                                   :decodes ["application/json"]})]
    (testing "parses parameters"
      (is (= {:body {:foo s/Str :bar s/Num}}
             (:body-schema handler))))))

(deftest reffed-requestbody-test
  (let [openapi-json
        {:paths {(keyword "/pets")
                 {:post {:operationId "create-pet"
                         :summary "Creates a pet"
                         :requestBody {:$ref "#/components/requestBodies/PetBody"}}}}
         :components {:requestBodies {:PetBody {:required true
                                                :content {:application/json
                                                          {:schema {:$ref "#/components/schemas/Pet"}}}}}
                      :schemas {:Pet {:type "object"
                                      :required ["name"]
                                      :properties {:name {:type "string"}
                                                   :age {:type "integer"}}}}}}
        [handler] (openapi->handlers openapi-json {:encodes ["application/json"]
                                                   :decodes ["application/json"]})]
    (is (= {:body {:name s/Str
                   (s/optional-key :age) s/Int}}
           (:body-schema handler)))))

(deftest status-nXX-test
  (let [oas-for (fn oas-for [n]
                  {:paths {(keyword "/getfoo")
                           {:get {:operationId "testit"
                                  :summary "For testing"
                                  :responses {(keyword (str n "XX"))
                                              {:description "Works fine"
                                               :content
                                               {:application/json
                                                {:schema {:type "integer"
                                                          :description "A number"}}}}}}}}})]
    (doseq [n [1 2 3 4 5]]
      (let [openapi-json (oas-for n)
            [handler] (openapi->handlers openapi-json {:encodes ["application/json"]
                                                       :decodes ["application/json"]})
            response-schemas (:response-schemas handler)
            status-schema (:status (first response-schemas))
            valid-statuses (repeatedly 3 #(+ (* n 100) (rand-int 100))) ; sample 3 ints in range
            invalid-status (* (inc n) 100)]
        (testing (str "checks response status range schema for " n "XX")
          (doseq [status valid-statuses]
            (is (s/validate status-schema status)))
          (is (thrown? #?(:clj Throwable
                          :cljs :default)
                       (s/validate status-schema invalid-status))))))))

(deftest base-url-test
  (let [openapi-abs-server {:openapi "3.1.0" :servers [{:url "https://sandbox.example.com"}]}
        openapi-rel-server {:openapi "3.1.0" :servers [{:url "/v3"}]}
        openapi-no-servers {:openapi "3.1.0"}
        swagger-json       {:swagger "2.0"   :basePath "/v2"}]

    (testing "OpenAPI — absolute servers[0].url wins regardless of spec URL"
      (is (= "https://sandbox.example.com"
             (base-url "http://localhost:8888/spec.json" nil openapi-abs-server))
          "absolute spec URL")
      (is (= "https://sandbox.example.com"
             (base-url "/spec.json" nil openapi-abs-server))
          "relative spec URL"))

    (testing "OpenAPI — explicit server-url overrides everything"
      (is (= "https://example.com"
             (base-url "http://localhost:8888/spec.json" "https://example.com" openapi-abs-server))
          "absolute server-url, absolute spec URL")
      (is (= "https://example.com"
             (base-url "/spec.json" "https://example.com" openapi-abs-server))
          "absolute server-url, relative spec URL"))

    (testing "OpenAPI — relative server-url resolved against absolute spec URL"
      (is (= "http://localhost:8888/v3.1"
             (base-url "http://localhost:8888/spec.json" "/v3.1" openapi-abs-server))
          "explicit relative server-url"))

    (testing "OpenAPI — relative servers[0].url resolved against absolute spec URL"
      (is (= "http://localhost:8888/v3"
             (base-url "http://localhost:8888/spec.json" nil openapi-rel-server))
          "relative servers entry, absolute spec URL"))

    (testing "OpenAPI — no servers, absolute spec URL: base is origin, no trailing slash"
      (is (= "http://localhost:8888"
             (base-url "http://localhost:8888/spec.json" nil openapi-no-servers))))

    (testing "OpenAPI — no servers, relative spec URL at root: base is empty string"
      (is (= ""
             (base-url "/spec.json" nil openapi-no-servers))
          "root-level spec → empty string (paths start with /, so api-root + path stays valid)"))

    (testing "OpenAPI — no servers, relative spec URL with path prefix: base is parent path"
      (is (= "/api"
             (base-url "/api/spec.json" nil openapi-no-servers))
          "spec in /api/ → /api, no trailing slash"))

    (testing "Swagger — absolute spec URL: origin + basePath"
      (is (= "http://localhost:8888/v2"
             (base-url "http://localhost:8888/swagger.json" nil swagger-json))))))
