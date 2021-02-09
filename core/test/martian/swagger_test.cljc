(ns martian.swagger-test
  (:require [clojure.test :refer [deftest is]]
            [schema.core :as s]
            [martian.swagger :as swagger]))

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
