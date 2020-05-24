(ns martian.schema-test
  (:require [martian.schema :as schema]
            [schema.core :as s]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest testing is run-tests]])))

(deftest free-form-object-test
  (let [schema (schema/make-schema {:definitions
                                    {:Body55523       {:type                 "object"
                                                       :properties           {:age    {:$ref "#/definitions/Body55523Age"}
                                                                              :name   {:$ref "#/definitions/Body55523Name"}
                                                                              :colour {:$ref "#/definitions/Body55523Colour"}}
                                                       :additionalProperties false
                                                       :required             ["name"]}
                                     :Body55523Age    {:type "object" :additionalProperties {}}
                                     :Body55523Name   {:type "object" :additionalProperties {}}
                                     :Body55523Colour {:type "object" :additionalProperties {}}}}
                                   {:in          "body"
                                    :name        "Body55523"
                                    :description ""
                                    :required    true
                                    :schema      {:$ref "#/definitions/Body55523"}})]
    (is (= {(s/optional-key :name)   (s/maybe {s/Any s/Any})
            (s/optional-key :age)    (s/maybe {s/Any s/Any})
            (s/optional-key :colour) (s/maybe {s/Any s/Any})}
           schema))))

(deftest enum-test
  (is (= (s/enum "desc" "asc")
         (schema/make-schema {:definitions {}} {:name "sort"
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

(deftest uuid-test
  (is (= (s/cond-pre s/Str s/Uuid)
         (schema/make-schema {:definitions {}} {:name "uuid"
                                                :in "path"
                                                :required true
                                                :type "string"
                                                :format "uuid"}))))

(deftest uri-test
  (is (= (s/cond-pre s/Str schema/URI)
         (schema/make-schema {:definitions {}} {:name "uri"
                                                :in "path"
                                                :required true
                                                :type "string"
                                                :format "uri"}))))

(deftest arrays-test
  (is (= [s/Str]
         (schema/make-schema {:definitions {}} {:name "tags"
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
           (schema/make-schema {:definitions definitions} body-param)))))

(deftest objects-test-with-parameter-ref
  (let [body-param {:name "Pet"
                    :in "body"
                    :required true
                    :schema {:$ref "#/parameters/Pet"}}
        parameters {:Pet {:type "object"
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
           (schema/make-schema {:parameters parameters} body-param)))))

(deftest optionality-test
  (is (= {(s/optional-key :id) (s/maybe s/Int)}
         (schema/schemas-for-parameters {} [{:name "id"
                                             :in "path"
                                             :required false
                                             :type "integer"}])))

  (let [body-param {:name "pet"
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
           (schema/schemas-for-parameters {:definitions definitions} [body-param])))))

(deftest keys-test
  (testing "default params"
    (is (= {:CamelKey s/Int}
           (schema/schemas-for-parameters
            {}
            [{:name "CamelKey"
              :in "path"
              :required true
              :type "integer"}]))))
  (testing "object with additional properties"
    (let [schema (:Pet (schema/schemas-for-parameters
                        {:definitions
                         {:Pet {:type                 "object"
                                :additionalProperties true
                                :properties           {:CamelBodyKey {:type     "integer"
                                                                      :required true}}}}}
                        [{:name     "Pet"
                          :in       "path"
                          :required true
                          :schema   {:$ref "#/definitions/Pet"}}]))]
      (is (= {:CamelBodyKey s/Int
              s/Any         s/Any} schema))
      (is (s/validate schema {:CamelBodyKey 42
                              :AdditionalDetails {"GoodBoy" true}}))))
  (testing "object with empty properties"
    (is (= {:Pet {}}
           (schema/schemas-for-parameters
            {:definitions
             {:Pet {:type       "object"
                    :properties {}}}}
            [{:name     "Pet"
              :in       "path"
              :required true
              :schema   {:$ref "#/definitions/Pet"}}]))))
  (testing "body params"
    (is (= {:Pet {:CamelBodyKey s/Int}}
           (schema/schemas-for-parameters
            {:definitions
             {:Pet {:type "object"
                    :properties {:CamelBodyKey {:type "integer"
                                                :required true}}}}}
            [{:name "Pet"
              :in "path"
              :required true
              :schema {:$ref "#/definitions/Pet"}}])))))

(deftest coerce-data-test
  (testing "maps"
    (let [data {:a "1" :b ["1" "2"] :c 3}]
      (is (= {:a 1
              :b [1 2]}
             (schema/coerce-data {:a s/Int :b [s/Int]} data)
             (schema/coerce-data (s/maybe {:a s/Int :b [s/Int]}) data)
             (schema/coerce-data (s/maybe {(s/optional-key :a) (s/maybe s/Int)
                                           (s/optional-key :b) (s/maybe [s/Int])}) data)))))

  (testing "arrays"
    (let [data ["1" "2"]]
      (is (= [1 2]
             (schema/coerce-data [s/Int] data)
             (schema/coerce-data (s/maybe [s/Int]) data)
             (schema/coerce-data (s/maybe [(s/maybe s/Int)]) data)))))

  (testing "anys are identity"
    (let [data ["a" "b"]]
      (is (= data
             (schema/coerce-data s/Any data)
             (schema/coerce-data (s/maybe s/Any) data))))

    (let [data {:a 1}]
      (is (= data
             (schema/coerce-data s/Any data)
             (schema/coerce-data {s/Keyword s/Any} data)))))

  (testing "deeply nested aliasing"
    (let [data {:aCamel {:anotherCamel {:camelsEverywhere 1}}}]
      (is (= data
             (schema/coerce-data
              {s/Keyword s/Any}
              {:a-camel {:another-camel {:camels-everywhere 1}}}
              {:a-camel :aCamel
               :another-camel :anotherCamel
               :camels-everywhere :camelsEverywhere}))))))

(deftest parameter-keys-test
  (is (= [:foo]
         (schema/parameter-keys [{:foo s/Int}])))

  (is (= [:foo]
         (schema/parameter-keys [{:foo s/Int
                                  s/Any s/Any}])))

  (is (= [:foo :bar]
         (schema/parameter-keys [{:foo s/Int}
                                 {:bar s/Str}])))

  (is (= [:foo :bar :baz :quu :quux :fizz :buzz]
         (schema/parameter-keys [{:foo s/Int}
                                 {:bar s/Str}
                                 {:baz {:quu s/Bool
                                        :quux s/Num}}
                                 {:fizz [{:buzz s/Str}]}]))))

(deftest recursive-schema-test
  (let [schema (schema/make-schema {:definitions {:A {:type       "object"
                                                      :properties {:b {:$ref "#/definitions/B"}}}
                                                  :B {:type "object"
                                                      :properties {:a {:$ref "#/definitions/A"}}}}}
                                   {:in          "body"
                                    :name        "A"
                                    :description "A -> B -> A -> B"
                                    :required    false
                                    :schema      {:$ref "#/definitions/A"}})]

    (is (= (s/maybe {(s/optional-key :b) (s/maybe {(s/optional-key :a) s/Any})})
           schema))))
