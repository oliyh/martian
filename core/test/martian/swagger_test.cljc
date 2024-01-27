(ns martian.swagger-test
  (:require [clojure.test :refer [deftest is]]
            [schema.core :as s]
            [schema-tools.core :as st]
            [martian.swagger :as swagger]
            [martian.test-helpers #?@(:clj [:refer [json-resource]]
                                      :cljs [:refer-macros [json-resource]])]))

(deftest parameter-schemas-test
  (let [[get-handler]
        (swagger/swagger->handlers {"paths" {"/pets/{id}"
                                             {"get"        {"operationId" "getPetById"
                                                            "method"      "get"
                                                            "parameters"  [{"name"     "id"
                                                                            "in"       "path"
                                                                            "type"     "string"
                                                                            "required" "true"}]}}}})]
    (is (= {:id s/Str} (:path-schema get-handler)))
    (is (= nil (:query-schema get-handler)))))

(deftest parameter-schemas-ref-test
  (let [params {:idParam {:name     "id"
                         :in       "path"
                         :type     "string"
                         :required "true"}}]
    (is (= {:id s/Str} (-> {:definitions params
                            "paths"      {"/pets/{id}"
                                          {"get" {"operationId" "getPetById"
                                                  "method"      "get"
                                                  "parameters"  [{"$ref" "#/definitions/idParam"}]}}}}
                           swagger/swagger->handlers
                           first
                           :path-schema)))
    (is (= {:id s/Str} (-> {:definitions params
                            "paths"      {"/pets/{id}"
                                          {"parameters" [{"$ref" "#/definitions/idParam"}]
                                           "get"        {"operationId" "getPetById"
                                                         "method"      "get"}}}}
                           swagger/swagger->handlers
                           first
                           :path-schema)))))

(deftest path-item-obj-parameters-test
  (let [[get-handler delete-handler]
        (swagger/swagger->handlers {"paths" {"/pets/{id}"
                                             {"get"        {"operationId" "getPetById"
                                                            "method"      "get"
                                                            "parameters"  [{"name" "watch"
                                                                            "in"   "query"}]}
                                              "delete"     {"operationId" "deletePetById"
                                                            "method"      "delete"
                                                            "parameters"  [{"name" "prettyprint"
                                                                            "in"   "query"}]}
                                              "parameters" [{"name"     "id"
                                                             "in"       "path"
                                                             "type"     "string"
                                                             "required" "true"}]}}})]
    (is (= {:id s/Str} (:path-schema get-handler)))
    (is (= {:id s/Str} (:path-schema delete-handler)))))

(deftest inline-object-schema-test
  (let [swagger-json {"paths"
                      {"/codes"
                       {"post"
                        {"operationId" "codes_create"
                         "summary" "codes create"
                         "description" "API endpoint that allows code_models to be viewed or edited."
                         "parameters" [{"name" "data"
                                        "in" "body"
                                        "required" true
                                        "schema" {"type" "object"
                                                  "required" ["dataset"]
                                                  "properties" {"dataset" {"type" "integer"}
                                                                "description" {"type" "string"}
                                                                "goal" {"type" "integer"}
                                                                "name" {"type" "string"}
                                                                "state" {"type" "integer"}
                                                                "systems" {"type" "array"
                                                                           "items" {"type" "integer"}}}}}]
                         "tags" ["code"]}}}}
        [handler] (swagger/swagger->handlers swagger-json)]

    (is (= {:data {:dataset s/Int
                   (s/optional-key :description) (s/maybe s/Str)
                   (s/optional-key :goal) (s/maybe s/Int)
                   (s/optional-key :name) (s/maybe s/Str)
                   (s/optional-key :state) (s/maybe s/Int)
                   (s/optional-key :systems) [s/Int]}}
           (:body-schema handler)))))

(deftest body-object-without-any-parameters-takes-values
  (is (= {:body {s/Any s/Any}}
         (let [[handler] (->> (json-resource "kubernetes-swagger-v2.json")
                              (swagger/swagger->handlers)
                              (filter #(= (:route-name %) :patch-core-v-1-namespaced-secret)))]
           (:body-schema handler)))))

(deftest response-schema-test
  (let [swagger-json
        {:paths {(keyword "/pets/{id}")
                 {:get {:operationId "load-pet"
                        :summary "Loads a pet by id"
                        :parameters [{:name "id"
                                      :in "path"
                                      :type "integer"}]
                        :responses {:200 {:description "The pet requested"
                                          :schema {:type "object"
                                                   :properties {:name {:type "string"}
                                                                :age {:type "integer"}
                                                                :type {:type "string"}}}}
                                    :400 {:schema {:type "string"}}
                                    :404 {:schema {:type "string"}}}}}}}
        [handler] (swagger/swagger->handlers swagger-json)]
    (is (= [{:status (s/eq 200),
             :body {(s/optional-key :name) (s/maybe s/Str),
                    (s/optional-key :age) (s/maybe s/Int),
                    (s/optional-key :type) (s/maybe s/Str)}}
            {:status (s/eq 400)
             :body s/Str}
            {:status (s/eq 404)
             :body s/Str}]
           (:response-schemas handler)))))

(deftest swagger-sanity-check
  (is (= {:path "/api/datasets/phones",
          :method :post,
          :produces
          ["application/json"
           "application/transit+msgpack"
           "application/transit+json"
           "application/edn"],
          :path-schema nil,
          :query-schema nil,
          :form-schema nil,
          :path-parts ["/api/datasets/phones"],
          :headers-schema nil,
          :consumes
          ["application/json"
           "application/transit+msgpack"
           "application/transit+json"
           "application/edn"],
          :summary "select from table `phones`",
          :body-schema
          {:body
           {(s/optional-key :phone)
            {:operation (st/default (s/enum "=" "contains" "like" "startswith") "=")
             :value s/Str},
            (s/optional-key :gender)
            {:operation (st/default (s/enum "=" "contains" "like" "startswith") "=")
             :value s/Str},
            (s/optional-key :age)
            {:operation (st/default (s/enum "=" "contains" "like" "startswith") "=")
             :value s/Str}}},
          :route-name :phones,
          :response-schemas [{:status (s/eq 'default), :body s/Any}]}

         (->> (json-resource "swagger-issue-111.json")
              (swagger/swagger->handlers)
              (filter #(= :phones (:route-name %)))
              first
              (#(dissoc % :swagger-definition))))))
