(ns martian.backends.malli-test
  (:require [martian.backends.malli :as malli]
            [martian.parameter-aliases :as aliases]
            [martian.schema :as schema]
            [martian.schema-backend :as sb]
            [malli.core :as m]
            [malli.transform :as mt]
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

#?(:cljs
   (def Throwable js/Error))

(def backend malli/backend)

(defn- make-schema [ref-lookup param]
  (schema/make-schema ref-lookup param backend))

(deftest primitives-test
  (is (= [:map
          [:id :int]
          [:weight number?]
          [:name :string]
          [:dog? :boolean]
          [:unknown :any]]
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
                                             :type "unknown"}]
                                        backend))))

(deftest enum-test
  (is (= [:enum "desc" "asc"]
         (make-schema {:definitions {}} {:name "sort"
                                         :in "query"
                                         :enum ["desc" "asc"]
                                         :required true}))))

(deftest string-formats-test
  (let [make-format-schema (fn [format]
                             (make-schema {:definitions {}} {:name "x"
                                                             :in "path"
                                                             :required true
                                                             :type "string"
                                                             :format format}))]
    (is (= [:or :string :int] (make-format-schema "int-or-string")))
    (is (= [:or :string :uuid] (make-format-schema "uuid")))
    (is (= [:or :string inst?] (make-format-schema "date-time")))
    (is (= [:or :string malli/uri-schema] (make-format-schema "uri")))
    (is (= [:or :string malli/binary-schema] (make-format-schema "binary")))))

(deftest arrays-test
  (is (= [:sequential :string]
         (make-schema {:definitions {}} {:name "tags"
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
    (is (= [:map
            [:id :int]
            [:name :string]
            [:tags [:sequential :string]]]
           (make-schema {:definitions definitions} body-param)))))

(deftest optionality-test
  (is (= [:map [:id {:optional true} [:maybe :int]]]
         (schema/schemas-for-parameters {} [{:name "id"
                                             :in "path"
                                             :required false
                                             :type "integer"}]
                                        backend))))

(deftest additional-properties-test
  (testing "open objects keep their concrete entries"
    (let [s (make-schema {:definitions {:Pet {:type "object"
                                              :additionalProperties true
                                              :properties {:id {:type "integer"
                                                                :required true}}}}}
                         {:name "Pet"
                          :in "body"
                          :required true
                          :schema {:$ref "#/definitions/Pet"}})]
      (is (= [:map [:id :int] [:malli.core/default :any]] s))
      (is (m/validate s {:id 1 :anything "goes"}))))

  (testing "objects with no properties at all allow anything"
    (is (= [:map [:malli.core/default :any]]
           (make-schema {:definitions {:Free {:type "object"
                                              :additionalProperties {}}}}
                        {:name "Free"
                         :in "body"
                         :required true
                         :schema {:$ref "#/definitions/Free"}})))))

(deftest recursive-schema-test
  (let [s (make-schema {:definitions {:A {:type "object"
                                          :properties {:b {:$ref "#/definitions/B"}}}
                                      :B {:type "object"
                                          :properties {:a {:$ref "#/definitions/A"}}}}}
                       {:in "body"
                        :name "A"
                        :required false
                        :schema {:$ref "#/definitions/A"}})]
    (is (= [:maybe [:map [:b {:optional true} [:maybe [:map [:a {:optional true} :any]]]]]]
           s))))

(deftest default-values-test
  (testing "defaults become schema properties"
    (is (= [:int {:default 123}] (sb/with-default-value backend {:default 123} :int)))
    (is (= :int (sb/with-default-value backend {} :int))))

  (testing "an 'inf' default on an integer becomes the maximum integer"
    (is (= [:int {:default #?(:clj Long/MAX_VALUE
                              :cljs (.-MAX_SAFE_INTEGER js/Number))}]
           (sb/with-default-value backend {:default "inf"} :int))))

  (testing "coercion applies defaults to missing and nil values"
    (let [s [:map
             [:name :string]
             [:address [:map [:city [:string {:default "trondheim"}]]]]]]
      (is (= {:name "Brachiosaurus" :address {:city "stavanger"}}
             (sb/coerce-data backend s {:name "Brachiosaurus" :address {:city "stavanger"}} {:use-defaults? true})))
      (is (= {:name "Brachiosaurus" :address {:city "trondheim"}}
             (sb/coerce-data backend s {:name "Brachiosaurus" :address {:city nil}} {:use-defaults? true})
             (sb/coerce-data backend s {:name "Brachiosaurus" :address {}} {:use-defaults? true}))))))

(deftest coerce-data-test
  (testing "maps"
    (let [data {:a "1" :b ["1" "2"] :c 3}]
      (is (= {:a 1 :b [1 2]}
             (sb/coerce-data backend [:map [:a :int] [:b [:sequential :int]]] data nil)
             (sb/coerce-data backend [:maybe [:map
                                              [:a {:optional true} [:maybe :int]]
                                              [:b {:optional true} [:maybe [:sequential :int]]]]] data nil)))))

  (testing "arrays"
    (is (= [1 2]
           (sb/coerce-data backend [:sequential :int] ["1" "2"] nil))))

  (testing "anys are identity"
    (let [data ["a" "b"]]
      (is (= data (sb/coerce-data backend :any data nil))))
    (let [data {:a 1}]
      (is (= data (sb/coerce-data backend :any data nil)))))

  (testing "keywords to strings"
    (is (= "foo" (sb/coerce-data backend :string :foo nil)))
    (is (= "asc" (sb/coerce-data backend [:enum "desc" "asc"] :asc nil)))
    (is (= "all" (sb/coerce-data backend [:= "all"] :all nil))))

  (testing "extra keys are stripped"
    (is (= {:a 1}
           (sb/coerce-data backend [:map [:a :int]] {:a 1 :b 2} nil))))

  (testing "invalid data throws"
    (is (thrown? Throwable (sb/coerce-data backend [:map [:a :int]] {} nil)))
    (is (thrown? Throwable (sb/coerce-data backend [:map [:a :int]] {:a "abc"} nil))))

  (testing "deeply nested aliasing"
    (let [s [:map [:aCamel [:map [:anotherCamel [:map [:camelsEverywhere :int]]]]]]
          data {:aCamel {:anotherCamel {:camelsEverywhere 1}}}]
      (is (= data
             (sb/coerce-data backend s
                             {:a-camel {:another-camel {:camels-everywhere 1}}}
                             {:parameter-aliases (aliases/registry backend s)})))))

  (testing "a custom transformer replaces the default one"
    (is (= {:flag "yes"}
           (sb/coerce-data backend [:map [:flag :string]]
                           {:flag :yes}
                           {:transformer (mt/transformer
                                          {:decoders {:string (fn [x] (if (keyword? x) (name x) x))}})}))
        "Custom transformer still decodes keywords to strings")
    (is (thrown? Throwable
                 (sb/coerce-data backend [:map [:flag :string]]
                                 {:flag :yes}
                                 {:transformer (mt/transformer {})}))
        "An empty transformer stops keyword->string conversion")))

(deftest collection-format-test
  (let [csv-schema (sb/wrap-collection-format-schema backend [:sequential :string] "csv")]
    (testing "wraps string arrays with a collection format property"
      (is (= [:string {::malli/collection-format "csv"}] csv-schema)))

    (testing "joins array values on coercion"
      (is (= "foo,bar" (sb/coerce-data backend csv-schema ["foo" "bar"] nil)))
      (is (= "foo bar" (sb/coerce-data backend
                                       (sb/wrap-collection-format-schema backend [:sequential :string] "ssv")
                                       ["foo" "bar"] nil)))
      (is (= "foo|bar" (sb/coerce-data backend
                                       (sb/wrap-collection-format-schema backend [:sequential :string] "pipes")
                                       ["foo" "bar"] nil))))

    (testing "leaves multi alone"
      (is (= [:sequential :string]
             (sb/wrap-collection-format-schema backend [:sequential :string] "multi"))))))

(deftest map-schema-keys-test
  (is (= [:id :name]
         (sb/map-schema-keys backend [:map [:id :int] [:name {:optional true} :string]])))
  (is (= [:id]
         (sb/map-schema-keys backend [:map [:id :int] [:malli.core/default :any]]))
      "The catch-all entry of an open map is not a key")
  (is (nil? (sb/map-schema-keys backend nil)))
  (is (nil? (sb/map-schema-keys backend :int))))

(deftest merge-map-schemas-test
  (is (= [:map [:a :int] [:b :string]]
         (sb/merge-map-schemas backend [[:map [:a :int]] [:map [:b :string]]])))
  (is (= [:map] (sb/merge-map-schemas backend []))))

(deftest check-and-validate-test
  (testing "check-schema returns nil when valid, error info otherwise"
    (is (nil? (sb/check-schema backend [:= 200] 200)))
    (is (some? (sb/check-schema backend [:= 200] 500))))

  (testing "validate-schema returns the value when valid, throws otherwise"
    (is (= {:id 1} (sb/validate-schema backend [:map [:id :int]] {:id 1})))
    (is (thrown? Throwable (sb/validate-schema backend [:map [:id :int]] {:id "x"})))))

(deftest aliases-test
  (let [s [:map
           [:fooBar :string]
           [:foo {:optional true} [:maybe [:map [:dooDar {:optional true} [:maybe :string]]]]]
           [:tags [:sequential [:map [:tagName :string]]]]]]

    (testing "key paths cover all map levels"
      (is (= [[] [:fooBar] [:foo] [:foo :dooDar] [:tags] [:tags :tagName]]
             (sb/key-paths backend s))))

    (testing "aliases are computed per idiomatic path"
      (is (= {:foo-bar :fooBar} (sb/aliases-at backend s [])))
      (is (= {:doo-dar :dooDar} (sb/aliases-at backend s [:foo])))
      (is (= {:tag-name :tagName} (sb/aliases-at backend s [:tags])))
      (is (nil? (sb/aliases-at backend s [:nope]))))

    (testing "alias-schema renames keys to their idiomatic forms"
      (is (= [:map
              [:foo-bar :string]
              [:foo {:optional true} [:maybe [:map [:doo-dar {:optional true} [:maybe :string]]]]]
              [:tags [:sequential [:map [:tag-name :string]]]]]
             (aliases/alias-schema backend (aliases/registry backend s) s))))

    (testing "unalias-data renames data keys back to the schema's keys"
      (is (= {:fooBar "a" :foo {:dooDar "b"} :tags [{:tagName "c"}]}
             (aliases/unalias-data (aliases/registry backend s)
                                   {:foo-bar "a" :foo {:doo-dar "b"} :tags [{:tag-name "c"}]}))))))
