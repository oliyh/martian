(ns martian.schema-test
  (:require [martian.schema :as schema]
            [schema.core :as s]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
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
           (schema/schemas-for-parameters definitions [body-param])))))

(deftest keys-test
  (testing "default params"
    (is (= {:CamelKey s/Int}
           (schema/schemas-for-parameters
            {}
            [{:name "CamelKey"
              :in "path"
              :required true
              :type "integer"}]))))

  (testing "kebab params"
    (is (= {:kebab-key s/Int}
           (schema/schemas-for-parameters
            {}
            [{:name "KebabKey"
              :in "path"
              :required true
              :type "integer"}]
            ->kebab-case-keyword))))

  (testing "body params"
    (is (= {:pet {:CamelBodyKey s/Int}}

           (schema/schemas-for-parameters
            {:Pet {:type "object"
                   :properties {:CamelBodyKey {:type "integer"
                                               :required true}}}}
            [{:name "Pet"
              :in "path"
              :required true
              :schema {:$ref "#/definitions/Pet"}}]
            ->kebab-case-keyword)))))

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
             (schema/coerce-data (s/maybe [(s/maybe s/Int)]) data))))))

(deftest parameter-keys-test
  (is (= [:foo]
         (schema/parameter-keys [{:foo s/Int}])))

  (is (= [:foo :bar]
         (schema/parameter-keys [{:foo s/Int}
                                 {:bar s/Str}])))

  (is (= [:foo :bar :baz :quu :quux :fizz :buzz]
         (schema/parameter-keys [{:foo s/Int}
                                 {:bar s/Str}
                                 {:baz {:quu s/Bool
                                        :quux s/Num}}
                                 {:fizz [{:buzz s/Str}]}]))))
