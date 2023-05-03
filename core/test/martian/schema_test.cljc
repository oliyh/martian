(ns martian.schema-test
  (:require [martian.schema :as schema]
            [matcher-combinators.test]
            [schema.core :as s]
            [schema-tools.core :as st]
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

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
    (is (= {:name                    {s/Any s/Any}
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
          :weight s/Num
          :name s/Str
          :dog? s/Bool
          :unknown s/Any}
         (schema/schemas-for-parameters {} [{:name "id"
                                             :in "path"
                                             :required true
                                             :type "integer"}
                                            {:name "weight"
                                             :in "path"
                                             :required true
                                             :type "number"}
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

(deftest default-values-test
  (testing "explaining and printing"
    (is (= '(default Int 123)
           (s/explain (st/default s/Int 123)))))

  (let [schema (schema/schemas-for-parameters {} [{:name "id"
                                                   :in "path"
                                                   :required true
                                                   :type "integer"
                                                   :default 123}
                                                  {:name "name"
                                                   :in "path"
                                                   :required true
                                                   :type "string"
                                                   :default "Gershwin"}
                                                  {:name "dog?"
                                                   :in "path"
                                                   :required true
                                                   :type "boolean"
                                                   :default false}
                                                  {:name "unknown"
                                                   :in "path"
                                                   :type "unknown"
                                                   :default "foo"}])]

    (testing "builds the schemas preserving default information"
      (is (= {:id (st/default s/Int 123)
              :name (st/default s/Str "Gershwin")
              :dog? (st/default s/Bool false)
              (s/optional-key :unknown) (s/maybe (st/default s/Any "foo"))}
             schema)))

    (testing "works on nested params inside the body"
      (let [body-param {:name "Pet"
                        :in "body"
                        :required true
                        :schema {:$ref "#/definitions/Pet"}}
            definitions {:Pet {:type "object"
                               :properties {:name {:type "string"
                                                   :required true}
                                            :tags {:type "array"
                                                   :items {:schema {:$ref "#/definitions/Tag"}}}
                                            :address {:schema {:$ref "#/definitions/Address"}
                                                      :required true}}}
                         :Address {:type "object"
                                   :properties {:city {:type "string"
                                                       :required true
                                                       :default "trondheim"}}}
                         :Tag {:type "object"
                               :properties {:k {:type "string"
                                                :required true
                                                :default "ExampleTag"}} }}
            schema (schema/make-schema {:definitions definitions} body-param)]
        (is (= {:name s/Str
                (s/optional-key :tags) [s/Any]
                :address {:city (st/default s/Str "trondheim")}}
               schema))

        (testing "and coerces data using default values"
          (testing "does nothing if values are present"
            (is (= {:name "Brachiosaurus"
                    :address {:city "stavanger"}}
                   (schema/coerce-data schema
                                       {:name "Brachiosaurus"
                                        :address {:city "stavanger"}}
                                       nil
                                       true))))

         (testing "adds missing values when there are defaults"
           (is (= {:name "Brachiosaurus"
                    :address {:city "trondheim"}}
                  (schema/coerce-data schema
                                      {:name "Brachiosaurus"
                                       :address {:city nil}}
                                      nil
                                      true))))

         (testing "adds missing keys"
           (is (= {:name "Brachiosaurus"
                   :address {:city "trondheim"}}
                  (schema/coerce-data schema
                                      {:name "Brachiosaurus"
                                       :address {}}
                                      nil
                                      true))))

         (testing "removing extra keys"
           (is (= {:name "Brachiosaurus"
                   :address {:city "trondheim"}}
                  (schema/coerce-data schema
                                      {:name "Brachiosaurus"
                                       :extra "key"
                                       :address {}}
                                      nil
                                      true))))

         ;; doesn't work - a limitation of spec-tools?
         #_(testing "works inside arrays"
           (is (= {:name "Brachiosaurus"
                   :address {:city "stavanger"}
                   :tags [{}]}
                  (schema/coerce-data schema
                                      {:name "Brachiosaurus"
                                       :address {:city "stavanger"}
                                       :tags [{:k nil}]}
                                      nil
                                      true)))))))))
;; "int-or-string" (s/cond-pre s/Str s/Int)
(deftest int-or-string-test
  (is (= (s/cond-pre s/Str s/Int)
         (schema/make-schema {:definitions {}} {:name "int-or-string"
                                                :in "path"
                                                :required true
                                                :type "string"
                                                :format "int-or-string"}))))

(deftest date-time-test
  (is (= (s/cond-pre s/Str s/Inst)
         (schema/make-schema {:definitions {}} {:name "date-time"
                                                :in "path"
                                                :required true
                                                :type "string"
                                                :format "date-time"}))))
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

(deftest inline-objects-test
  (let [body-param {:name "Pet"
                    :in "body"
                    :required true
                    :schema {:type "object"
                             :additionalProperties {}
                             :required ["id"]
                             :properties {:id {:type "integer"}
                                          :name {:type "string"
                                                 :required true}
                                          :tags {:type "array"
                                                 :required true
                                                 :items {:type "string"}}}}}]

    (is (= {:id s/Int
            :name s/Str
            :tags [s/Str]
            s/Any s/Any}
           (schema/make-schema {} body-param)))))

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
              {[] {:a-camel :aCamel}
               [:a-camel] {:another-camel :anotherCamel}
               [:a-camel :another-camel] {:camels-everywhere :camelsEverywhere}})))))

  (testing "keywords to strings"
    (is (= "foo" (schema/coerce-data s/Str :foo)))))

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


(def definition {:org.chaos-mesh.v1alpha1.NetworkChaos
                 {:description "NetworkChaos is the Schema for the networkchaos API"
                  :type "object"
                  :required ["spec"]
                  :properties {:spec {:type "object"
                                      :required ["action" "mode" "selector"]
                                      :properties {:selector {:type "object"
                                                              :required ["namespaces" "fieldSelectors"]
                                                              :properties {:namespaces {:type "array"
                                                                                        :items {:type "string"}}
                                                                           :fieldSelectors {:type "object"
                                                                                            :additionalProperties {:type "string"}}
                                                                           :pods {:type "object"
                                                                                  :additionalProperties {:type "array"
                                                                                                         :items {:type "string"}}}}}
                                                   :mode {:type "string"
                                                          :enum ["one"
                                                                 "all"
                                                                 "fixed"
                                                                 "fixed-percent"
                                                                 "random-max-percent"]}
                                                   :duration {:type "string"}
                                                   :action {:type "string"
                                                            :enum ["netem"
                                                                   "delay"
                                                                   "loss"
                                                                   "duplicate"
                                                                   "corrupt"
                                                                   "partition"
                                                                   "bandwidth"]}}}}}})

(deftest require-nested-objects
  (let [schema (schema/make-schema {:definitions definition}
                                   {:name "body",
                                    :in "body",
                                    :required true,
                                    :schema {:$ref "#/definitions/org.chaos-mesh.v1alpha1.NetworkChaos"}})]
    (is (match? {:spec {:mode any?
                        :action any?
                        :selector {:namespaces any?
                                   :fieldSelectors any?
                                   (s/optional-key :pods) any?}
                        (s/optional-key :duration) any?}}
                schema))))