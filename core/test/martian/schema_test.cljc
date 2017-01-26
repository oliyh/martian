(ns martian.schema-test
  (:require [martian.schema :as schema]
            [schema.core :as s]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest testing is run-tests]])))

(deftest enum-test
  (is (= (s/enum "desc" "asc")
         (schema/make-schema {} {:name "sort"
                                 :in "query"
                                 :enum ["desc","asc"]
                                 :required true}))))

(deftest primitives-test
  (is (= {:id s/Int
          :name s/Str
          :dog? s/Bool
          :unknown s/Any}
         (schema/schemas-for-parameters {} [{:name "id"
                                              :in "path"
                                              :required true
                                             :type "integer"}
                                            {:name "name"
                                             :in "path"
                                             :required true
                                             :type "string"}
                                            {:name "dog?"
                                             :in "path"
                                             :required true
                                             :type "boolean"}
                                            {:name "unknown"
                                             :in "path"
                                             :required true
                                             :type "unknown"}]))))

(deftest arrays-test
  (is (= [s/Str]
         (schema/make-schema {} {:name "tags"
                                 :in "body"
                                 :required true
                                 :type "array"
                                 :items {:type "string"}}))))

(deftest objects-test
  (let [body-param {:name "Pet"
                    :in "body"
                    :required true
                    :schema {:$ref "#/definitions/Pet"}}
        definitions {:Pet {:type "object"
                           :properties {:id {:type "integer"
                                             :required true}
                                        :name {:type "string"
                                               :required true}
                                        :tags {:type "array"
                                               :required true
                                               :items {:type "string"}}}}}]

    (is (= {:id s/Int
            :name s/Str
            :tags [s/Str]}
           (schema/make-schema definitions body-param)))))

(deftest optionality-test
  (is (= {(s/optional-key :id) (s/maybe s/Int)}
         (schema/schemas-for-parameters {} [{:name "id"
                                             :in "path"
                                             :required false
                                             :type "integer"}])))

  (let [body-param {:name "Pet"
                    :in "body"
                    :required false
                    :schema {:$ref "#/definitions/Pet"}}
        definitions {:Pet {:type "object"
                           :properties {:id {:type "integer"
                                             :required false}
                                        :name {:type "string"
                                               :required true}
                                        :tags {:type "array"
                                               :required false
                                               :items {:type "string"}}}}}]

    (is (= {(s/optional-key :pet)
            (s/maybe {(s/optional-key :id) (s/maybe s/Int)
                      :name s/Str
                      (s/optional-key :tags) [s/Str]})}
           (schema/schemas-for-parameters definitions [body-param])))))
