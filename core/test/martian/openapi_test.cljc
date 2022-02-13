(ns martian.openapi-test
  (:require [martian.test-helpers #?@(:clj [:refer [json-resource]]
                                      :cljs [:refer-macros [json-resource]])]
            [schema-tools.core :as st]
            [clojure.test :refer [deftest is testing]]
            [schema.core :as s]
            [martian.openapi :refer [openapi->handlers]]))

(def openapi-json
  (json-resource "openapi.json"))

(def openapi-2-json
  (json-resource "openapi2.json"))

(def jira-openapi-v3-json
  (json-resource "jira-openapi-v3.json"))

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
            :consumes [nil],
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
             (openapi->handlers {:encodes ["json"]
                                 :decodes ["json"]})
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
