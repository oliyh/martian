(ns martian.openapi-test
  (:require [martian.test-helpers #?@(:clj [:refer [json-resource]]
                                      :cljs [:refer-macros [json-resource]])]
            [martian.schema :refer [schema-with-meta]]
            [clojure.test :refer [deftest is testing]]
            [schema.core :as s]
            [martian.openapi :refer [openapi->handlers]]))

(def openapi-json
  (json-resource "openapi.json"))

(def openapi-2-json
  (json-resource "openapi2.json"))

(deftest openapi-sanity-check
  (testing "parses each handler"
    (is (= {:summary        "Update an existing pet"
            :description    "Update an existing pet by Id"
            :method         :put
            :produces       ["application/json"]
            :path-schema    {}
            :query-schema   {}
            :form-schema    {}
            :path-parts     ["/pet"]
            :headers-schema {}
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
            :query-schema {(s/optional-key :key) (schema-with-meta s/Str {:default "some-default-key"})},
            :form-schema {},
            :path-parts ["/project/" :projectKey],
            :headers-schema {},
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
