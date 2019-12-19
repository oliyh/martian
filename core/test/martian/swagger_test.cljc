(ns martian.swagger-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [martian.swagger :as swagger]))

(deftest path-item-obj-parameters-test
  (let [[get-handler delete-handler] (swagger/swagger->handlers {"paths" {"/pets/{id}" {"get"        {"operationId" "getPetById"
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
