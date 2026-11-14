(ns martian.core-test
  (:require [martian.core :as martian]
            [schema.core :as s]
            [schema-tools.core :as st]
            [clojure.spec.test.alpha :as stest]
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]]))
  #?@(:bb [] :clj [(:import [martian Martian])]))

#?(:cljs
   (def Throwable js/Error))

(def cannot-coerce-pattern #"Could not coerce value to schema")

(stest/instrument)

(def swagger-definition
  {:paths {(keyword "/pets/{id}")                         {:get {:operationId "load-pet"
                                                                 :summary "Loads a pet by id"
                                                                 :parameters [{:name "id"
                                                                               :in "path"
                                                                               :type "integer"}]
                                                                 :responses {:200 {:description "The pet requested"
                                                                                   :schema {:$ref "#/definitions/Pet"}}}}}
           (keyword "/pets/")                             {:get {:operationId "all-pets"
                                                                 :parameters [{:name "sort"
                                                                               :in "query"
                                                                               :enum ["desc","asc"]
                                                                               :required false}]
                                                                 :responses {:200 {:description "An array of all the pets"
                                                                                   :schema {:type "array"
                                                                                            :items {:$ref "#/definitions/Pet"}}}}}
                                                           :post {:operationId "create-pet"
                                                                  :parameters [{:name "Pet"
                                                                                :in "body"
                                                                                :required true
                                                                                :schema {:$ref "#/definitions/Pet"}}]}
                                                           :put {:operationId "update-pet"
                                                                 :parameters [{:name "id"
                                                                               :in "formData"
                                                                               :type "integer"
                                                                               :required true}
                                                                              {:name "name"
                                                                               :in "formData"
                                                                               :type "string"
                                                                               :required true}]}}
           (keyword "/{colour}-{animal}/list")            {:get {:operationId "pet-search"
                                                                 :parameters [{:name "colour" :in "path"}
                                                                              {:name "animal" :in "path"}]}}
           (keyword "/users/{user-id}/orders/{order-id}") {:get {:operationId "order"
                                                                 :parameters [{:name "user-id"
                                                                               :in "path"}
                                                                              {:name "order-id"
                                                                               :in "path"}
                                                                              {:name "AuthToken"
                                                                               :in "header"}]}}
           (keyword "/orders/")                           {:post {:operationId "create-orders"
                                                                  :parameters [{:name "order-ids"
                                                                                :in "body"
                                                                                :type "array"
                                                                                :items {:type "string"}}]}}
           (keyword "/users/")                            {:post {:operationId "create-users"
                                                                  :parameters [{:name "Users"
                                                                                :in "body"
                                                                                :required true
                                                                                :schema {:type "array"
                                                                                         :items {:$ref "#/definitions/User"}}}]}
                                                           ;; An `:operationId` is intentionally missing from the GET method.
                                                           :get {}}}

   :definitions {:Pet {:type "object"
                       :properties {:id {:type "integer"
                                         :required true}
                                    :name {:type "string"
                                           :required true}}}
                 :User {:type "object"
                        :properties {:id {:type "integer"
                                          :required true}
                                     :name {:type "string"
                                            :required true}
                                     :emailAddress {:type "string"
                                                    :required true}}}}})

(deftest bootstrap-test
  (testing "bootstrap swagger"
    (let [m (martian/bootstrap-swagger "https://api.org" swagger-definition)]
      (is (= "https://api.org/pets/123" (martian/url-for m :load-pet {:id 123})))))

  (testing "bootstrap data"
    (let [m (martian/bootstrap "https://api.org"
                               [{:route-name :load-pet
                                 :path-parts ["/pets/" :id]
                                 :method :get
                                 :path-schema {:id s/Int}}

                                {:route-name :create-pet
                                 :produces ["application/xml"]
                                 :consumes ["application/xml"]
                                 :path-parts ["/pets/"]
                                 :method :post
                                 :body-schema {:pet {:id s/Int
                                                     :name s/Str}}}]

                               {:produces ["application/json"]
                                :consumes ["application/json"]})]

      (is (= "https://api.org/pets/123" (martian/url-for m :load-pet {:id 123})))
      (is (thrown-with-msg? Throwable cannot-coerce-pattern
                            (martian/request-for m :load-pet {:id "one"})))

      (is (= {:method :post
              :url "https://api.org/pets/"
              :body {:id 123
                     :name "Doge"}}
             (martian/request-for m :create-pet {:id 123 :name "Doge"})))

      (is (= ["application/json"] (-> m :handlers first :produces)))
      (is (= ["application/xml"] (-> m :handlers second :produces))))))

(deftest route-specific-interceptors-test
  (testing "bootstrap-swagger with helpers"
    (let [m (-> (martian/bootstrap-swagger "https://api.org" swagger-definition)
                (martian/update-handler :load-pet assoc :interceptors [{:id ::override-load-pet-method
                                                                        :enter #(assoc-in % [:request :method] :xget)}]))]
      (is (= {:method :xget
              :url "https://api.org/pets/123"}
             (martian/request-for m :load-pet {:id 123})))))

  (testing "bootstrap data"
    (let [m (martian/bootstrap "https://api.org"
                               [{:route-name :load-pet
                                 :path-parts ["/pets/" :id]
                                 :method :get
                                 :path-schema {:id s/Int}
                                 :interceptors [{:id ::override-load-pet-method
                                                 :enter #(assoc-in % [:request :method] :xget)}]}

                                {:route-name :create-pet
                                 :produces ["application/xml"]
                                 :consumes ["application/xml"]
                                 :path-parts ["/pets/"]
                                 :method :post
                                 :body-schema {:pet {:id s/Int
                                                     :name s/Str}}
                                 :interceptors [{:id ::for-create-pet-fake-response
                                                 :leave #(assoc % :response {::for-create-pet true})}]}])]

      (is (= {:method :xget
              :url "https://api.org/pets/123"}
             (martian/request-for m :load-pet {:id 123})))

      (is (= {::for-create-pet true}
             (martian/response-for m :create-pet {:pet {:id 123 :name "Charlie"}}))))))

(deftest url-for-test
  (let [m (martian/bootstrap-swagger "https://api.org" swagger-definition)
        url-for (partial martian/url-for m)]

    (is (= "https://api.org/pets/123" (url-for :load-pet {:id 123})))
    (is (= "https://api.org/pets/" (url-for :all-pets)))
    (is (= "https://api.org/pets/" (url-for :create-pet)))
    (is (= "https://api.org/users/123/orders/456" (url-for :order {:user-id 123 :order-id 456})))
    (is (= "https://api.org/yellow-canaries/list" (url-for :pet-search {:colour "yellow" :animal "canaries"})))))

(deftest string-keys-test
  (let [swagger-definition
        {"paths" {"/pets/{id}"                         {"get" {"operationId" "load-pet"
                                                               "parameters" [{"name" "id" "in" "path"}]}}
                  "/pets/"                             {"get" {"operationId" "all-pets"}
                                                        "post" {"operationId" "create-pet"}}
                  "/users/{user-id}/orders/{order-id}" {"get" {"operationId" "order"
                                                               "parameters" [{"name" "user-id" "in" "path"}
                                                                             {"name" "order-id" "in" "path"}]}}}}
        m (martian/bootstrap-swagger "https://api.org" swagger-definition)
        url-for (partial martian/url-for m)]

    (is (= "https://api.org/pets/123" (url-for :load-pet {:id 123})))
    (is (= "https://api.org/pets/" (url-for :all-pets)))
    (is (= "https://api.org/pets/" (url-for :create-pet)))
    (is (= "https://api.org/users/123/orders/456" (url-for :order {:user-id 123 :order-id 456})))))

(deftest explore-test
  (let [m (martian/bootstrap-swagger "https://api.org" swagger-definition)]

    (is (= [[:load-pet "Loads a pet by id"]
            [:all-pets nil]
            [:create-pet nil]
            [:update-pet nil]
            [:pet-search nil]
            [:order nil]
            [:create-orders nil]
            [:create-users nil]]
           (martian/explore m)))

    (is (= {:summary nil
            :parameters {:id s/Int
                         :name s/Str}
            :returns {}}
           (martian/explore m :update-pet)))

    (is (= {:summary nil
            :parameters {(s/optional-key :sort) (s/maybe (s/enum "desc" "asc"))}
            :returns {200 [{:id s/Int, :name s/Str}]}}
           (martian/explore m :all-pets))))

  (testing "route names sources option"
    (let [m (martian/bootstrap-swagger "https://api.org"
                                       swagger-definition
                                       {:route-name-sources [:operationId :method+path]})]
      (is (= [[:load-pet "Loads a pet by id"]
              [:all-pets nil]
              [:create-pet nil]
              [:update-pet nil]
              [:pet-search nil]
              [:order nil]
              [:create-orders nil]
              [:create-users nil]
              [:get-users nil]]
             (martian/explore m))))

    (let [m (martian/bootstrap-swagger "https://api.org"
                                       swagger-definition
                                       {:route-name-sources [:method+path]})]
      ;; Beware of possible collisions!
      (is (= [[:get-pet "Loads a pet by id"]
              [:get-pets nil]
              [:post-pets nil]
              [:put-pets nil]
              [:get-list nil]
              [:get-user-order nil]
              [:post-orders nil]
              [:post-users nil]
              [:get-users nil]]
             (martian/explore m))))))

(deftest request-for-test
  (let [m (martian/bootstrap-swagger "https://api.org" swagger-definition)
        request-for (partial martian/request-for m)]

    (testing "parameter coercion"
      (is (= {:method :get
              :url "https://api.org/pets/123"}
             (request-for :load-pet {:id 123})
             (request-for :load-pet {:id "123"}))))

    (testing "method and url"
      (is (= {:method :get
              :url "https://api.org/pets/"}
             (request-for :all-pets {}))))

    (testing "query-params"
      (is (= {:method :get
              :url "https://api.org/pets/"
              :query-params {:sort "asc"}}
             (request-for :all-pets {:sort "asc"})
             (request-for :all-pets {:sort :asc}))))

    (testing "headers"
      (is (= {:method :get
              :url "https://api.org/users/123/orders/234"
              :headers {"AuthToken" "abc-1234"}}
             (request-for :order {:user-id 123 :order-id 234 :auth-token "abc-1234"}))))

    (testing "body maps"
      (is (= {:method :post
              :url "https://api.org/pets/"
              :body {:id 123 :name "charlie"}}

             ;; these three forms are equivalent, demonstrating destructuring options
             (request-for :create-pet {:id 123 :name "charlie"})
             (request-for :create-pet {:pet {:id 123 :name "charlie"}})
             (request-for :create-pet {::martian/body {:id 123 :name "charlie"}})

             (request-for :create-pet {:pet {:id "123" :name "charlie"}}))))

    (testing "body arrays"
      (is (= {:method :post
              :url "https://api.org/users/"
              :body [{:id 1 :name "Bob" :emailAddress "bob@builder.com"}
                     {:id 2 :name "Barry" :emailAddress "barry@builder.com"}]}
             (request-for :create-users {:users [{:id 1 :name "Bob" :email-address "bob@builder.com"}
                                                 {:id 2 :name "Barry" :email-address "barry@builder.com"}]}))))

    (testing "primitive body arrays"
      (is (= {:method :post
              :url "https://api.org/orders/"
              :body ["order-number-one"
                     "order-number-two"]}
             (request-for :create-orders {:order-ids ["order-number-one" "order-number-two"]}))))

    (testing "providing initial request map"
      (is (= {:method :put
              :url "https://api.org/pets/"
              :form-params {:id 123 :name "nigel"}}
             (request-for :update-pet {:id 123 :name "nigel"})))

      (is (= {:method :get ;; overridden from definition
              :url "https://api.org/pets/"
              :form-params {:id 123 :name "nigel"}}
             (request-for :update-pet {::martian/request {:method :get}
                                       :id 123
                                       :name "nigel"}))))

    (testing "exceptions"
      (is (thrown-with-msg? Throwable cannot-coerce-pattern
                            (request-for :all-pets {:sort "baa"})))

      (is (thrown-with-msg? Throwable cannot-coerce-pattern
                            (request-for :load-pet {:id "one"})))

      (is (thrown-with-msg? Throwable cannot-coerce-pattern
                            (request-for :create-pet {:pet {:id "one"
                                                            :name 1}})))

      (is (thrown-with-msg? Throwable cannot-coerce-pattern
                            (request-for :create-pet))))))

(deftest with-interceptors-test
  (let [auth-headers-interceptor {:name ::auth-headers
                                  :enter (fn [ctx]
                                           (update-in ctx [:request :headers] merge {"auth-token" "1234-secret"}))}
        m (martian/bootstrap-swagger "https://api.org" swagger-definition
                                     {:interceptors (concat [auth-headers-interceptor] martian/default-interceptors)})]

    (is (= {:method :get
            :url "https://api.org/pets/123"
            :headers {"auth-token" "1234-secret"}}
           (martian/request-for m :load-pet {:id 123})))))

(deftest with-default-headers-test
  (let [add-default-headers-interceptor {:name ::add-default-headers
                                         :enter (fn [ctx]
                                                  (update-in ctx [:request :headers]
                                                             assoc :X-API-key "ABC123"))}
        m (martian/bootstrap "https://defaultheaders.com"
                             [{:route-name :get-item
                               :produces ["application/json"]
                               :consumes ["application/json"]
                               :headers-schema {:X-API-key s/Str}
                               :path-parts ["/api/" :id]
                               :path-schema {:id s/Str}
                               :method :get}]
                             {:interceptors (concat [add-default-headers-interceptor] martian/default-interceptors)})]

    (is (= {:method :get
            :url "https://defaultheaders.com/api/123"
            :headers {"X-API-key" "ABC123"}}
           (martian/request-for m :get-item {:id "123"})))))

(deftest any-body-test
  (let [m (martian/bootstrap "https://bodyblobs.com"
                               [{:route-name :create-blob
                                 :path-parts ["/"]
                                 :method :put
                                 :body-schema {:blob s/Any}}])
        body {:some-key {:some-nested "thing"}}]

    (is (= {:method :put,
            :url "https://bodyblobs.com/"
            :body body}
           (martian/request-for m :create-blob {::martian/body body})
           (martian/request-for m :create-blob {:blob body})))))

(deftest missing-route-test
  (let [m (martian/bootstrap "https://camels.org" [])]
    (try
      (martian/url-for m :missing-route {:camel-id 1})
      (catch Throwable e
        (is (= :missing-route (-> e ex-data :route-name)))))))

(deftest use-defaults-test
  (testing "in isolation"
    (let [m (martian/bootstrap
              "http://example.com"
              [{:query-schema {:version (st/default s/Int 70)}
                :route-name   :test-route
                :method       :get
                :path-parts   ["/some"]}]
              {:use-defaults? true})]

      (testing "coerces data using default values"

        (testing "adds missing values when there are defaults"
          (is (= {:method       :get
                  :url          "http://example.com/some"
                  :query-params {:version 70}}
                 (martian/request-for m :test-route {}))))

        (testing "does nothing if values are present"
          (is (= {:method       :get
                  :url          "http://example.com/some"
                  :query-params {:version 100}}
                 (martian/request-for m :test-route {:version 100})))))))

  (testing "interplay with other params"
    (let [m (martian/bootstrap
              "http://example.com"
              [{:query-schema {:version (st/default s/Int 70)}
                :path-schema  {:id s/Str}
                :route-name   :test-route
                :method       :get
                :path-parts   ["/some/" :id]}]
              {:use-defaults? true})]

      (is (thrown-with-msg? Throwable cannot-coerce-pattern
                            (martian/request-for m :test-route {}))
          "Could not coerce value to schema: {:id missing-required-key}")

      (testing "coerces data using default values"
        ;; NOTE: There must be no error of the following kind (after #215 fix):
        ;;       Value cannot be coerced to match schema: {:id disallowed-key}

        (testing "adds missing values when there are defaults"
          (is (= {:method       :get
                  :url          "http://example.com/some/id"
                  :query-params {:version 70}}
                 (martian/request-for m :test-route {:id "id"}))))

        (testing "does nothing if values are present"
          (is (= {:method       :get
                  :url          "http://example.com/some/id"
                  :query-params {:version 100}}
                 (martian/request-for m :test-route {:id      "id"
                                                     :version 100})))))))

  (testing "an optional key"
    (let [m (martian/bootstrap
              "http://example.com"
              [{:query-schema {(s/optional-key :version) (st/default s/Int 70)}
                :route-name   :test-route
                :method       :get
                :path-parts   ["/some"]}]
              {:use-defaults? true})]

      (testing "gets its default when missing"
        (is (= {:method       :get
                :url          "http://example.com/some"
                :query-params {:version 70}}
               (martian/request-for m :test-route {}))))

      (testing "gets its default when nil"
        (is (= {:method       :get
                :url          "http://example.com/some"
                :query-params {:version 70}}
               (martian/request-for m :test-route {:version nil}))))

      (testing "keeps a value that is present"
        (is (= {:method       :get
                :url          "http://example.com/some"
                :query-params {:version 100}}
               (martian/request-for m :test-route {:version 100}))))))

  (testing "a default behind a schema wrapper"
    (let [query-params (fn [schema params]
                         (-> (martian/bootstrap
                               "http://example.com"
                               [{:query-schema {(s/optional-key :version) schema}
                                 :route-name   :test-route
                                 :method       :get
                                 :path-parts   ["/some"]}]
                               {:use-defaults? true})
                             (martian/request-for :test-route params)
                             :query-params))]
      (testing "fills a missing key"
        (doseq [schema [(s/maybe (st/default s/Int 70))
                        (s/named (st/default s/Int 70) "version")
                        (s/constrained (st/default s/Int 70) pos?)
                        (s/maybe (s/named (st/default s/Int 70) "version"))]]
          (is (= {:version 70} (query-params schema {})) (pr-str schema))))

      (testing "leaves a nil given for a nullable key, where nil is a value"
        (is (= {:version nil} (query-params (s/maybe (st/default s/Int 70)) {:version nil}))))

      (testing "keeps a value that is present"
        (is (= {:version 100} (query-params (s/maybe (st/default s/Int 70)) {:version 100}))))))

  (testing "an optional Swagger param and a nullable OpenAPI one get their defaults"
    (let [swagger (martian/bootstrap-swagger
                    "https://api.org"
                    {:paths {(keyword "/items") {:get {:operationId "list-items"
                                                       :parameters [{:name "limit" :in "query"
                                                                     :type "integer" :default 10}]
                                                       :responses {:200 {}}}}}}
                    {:use-defaults? true})
          openapi (martian/bootstrap-openapi
                    "https://api.org"
                    {:openapi "3.0.0"
                     :paths {(keyword "/items") {:get {:operationId "list-items"
                                                       :parameters [{:name "limit" :in "query"
                                                                     :schema {:type "integer" :default 10
                                                                              :nullable true}}]
                                                       :responses {:200 {}}}}}}
                    {:use-defaults? true})]
      (is (= {:limit 10}
             (:query-params (martian/request-for swagger :list-items {}))
             (:query-params (martian/request-for openapi :list-items {})))))))

(deftest kebab-mapping-test
  (let [m (martian/bootstrap "https://camels.org"
                             [{:route-name :create-camel
                               :path-parts ["/camels/" :camelId]
                               :method :put
                               :path-schema {:camelId s/Int}
                               :query-schema {:camelVersion s/Int}
                               :body-schema {:Camel {:camelName s/Str
                                                     :camelTrain {:leaderName s/Str
                                                                  (s/optional-key :followerCamels) [{:followerName s/Str}]}
                                                     :anyCamel s/Any}}
                               :headers-schema {(s/optional-key "Content-Security-Policy") s/Str
                                                (s/optional-key "WWW-Authenticate") s/Str
                                                (s/optional-key "X-XSS-Protection") s/Str
                                                (s/optional-key :camelToken) s/Str}
                               :form-schema {:camelHumps (s/maybe s/Int)}}])]

    (is (= "https://camels.org/camels/1"
           (martian/url-for m :create-camel {:camel-id 1
                                             :camel-version 2})))

    (is (= "https://camels.org/camels/1?camelVersion=2"
           (martian/url-for m :create-camel {:camel-id 1
                                             :camel-version 2}
                            {:include-query? true})))

    (let [param-aliases (:parameter-aliases (martian/handler-for m :create-camel))]
      (is (= {:camel-id :camelId}
             (get-in param-aliases [:path-schema []])))
      (is (= {:camel-version :camelVersion}
             (get-in param-aliases [:query-schema []])))
      (is (= {:camel :Camel}
             (get-in param-aliases [:body-schema []])))
      (is (= {:camel-name :camelName
              :camel-train :camelTrain
              :any-camel :anyCamel}
             (get-in param-aliases [:body-schema [:camel]])))
      (is (= {:leader-name :leaderName
              :follower-camels :followerCamels}
             (get-in param-aliases [:body-schema [:camel :camel-train]])))
      (is (= {:follower-name :followerName}
             (get-in param-aliases [:body-schema [:camel :camel-train :follower-camels]])))
      (is (= {:camel-humps :camelHumps}
             (get-in param-aliases [:form-schema []])))
      (is (= {"content-security-policy" "Content-Security-Policy"
              "www-authenticate" "WWW-Authenticate"
              "x-xss-protection" "X-XSS-Protection"
              :camel-token :camelToken}
             (get-in param-aliases [:headers-schema []]))))

    (is (= {:method :put
            :url "https://camels.org/camels/1"
            :query-params {:camelVersion 2}
            :body {:camelName "kebab"
                   :camelTrain {:leaderName "camel leader"
                                :followerCamels [{:followerName "OCaml"}]}
                   :anyCamel {:camel-train "choo choo"}}
            :form-params {:camelHumps 2}
            :headers {"camelToken" "cAmEl"}}

           ;; fully destructured
           (martian/request-for m :create-camel {:camel-id 1
                                                 :camel-version 2
                                                 :camel-token "cAmEl"
                                                 :camel-humps 2
                                                 :camel-name "kebab"
                                                 :camel-train {:leader-name "camel leader"
                                                               :follower-camels [{:follower-name "OCaml"}]}
                                                 :any-camel {:camel-train "choo choo"}})

           ;; nested under (kebabbed) body key
           (martian/request-for m :create-camel {:camel-id 1
                                                 :camel-version 2
                                                 :camel-token "cAmEl"
                                                 :camel-humps 2
                                                 :camel {:camel-name "kebab"
                                                         :camel-train {:leader-name "camel leader"
                                                                       :follower-camels [{:follower-name "OCaml"}]}
                                                         :any-camel {:camel-train "choo choo"}}})

           ;; destructured, already in camel case
           (martian/request-for m :create-camel {:camelId 1
                                                 :camelVersion 2
                                                 :camelToken "cAmEl"
                                                 :camelHumps 2
                                                 :camelName "kebab"
                                                 :camelTrain {:leaderName "camel leader"
                                                              :followerCamels [{:followerName "OCaml"}]}
                                                 :anyCamel {:camel-train "choo choo"}})

           ;; nested under (already camelled) body key
           (martian/request-for m :create-camel {:camelId 1
                                                 :camelVersion 2
                                                 :camelToken "cAmEl"
                                                 :camelHumps 2
                                                 :Camel {:camelName "kebab"
                                                         :camelTrain {:leaderName "camel leader"
                                                                      :followerCamels [{:followerName "OCaml"}]}
                                                         :anyCamel {:camel-train "choo choo"}}})))

    (testing "explore shows idiomatic kebab keys"
      (is (= {:summary nil
              :parameters
              {:camel-id s/Int
               :camel-version s/Int
               :camel {:camel-name s/Str
                       :camel-train {:leader-name s/Str
                                     (s/optional-key :follower-camels) [{:follower-name s/Str}]}
                       :any-camel s/Any}
               :camel-humps (s/maybe s/Int)
               (s/optional-key "content-security-policy") s/Str
               (s/optional-key "www-authenticate") s/Str
               (s/optional-key "x-xss-protection") s/Str
               (s/optional-key :camel-token) s/Str}
              :returns {}}
             (martian/explore m :create-camel))))))

#?(:bb nil
   :clj
   (deftest java-api-test
     (let [swagger-definition
           {"paths" {"/pets/{id}"                         {"get" {"operationId" "load-pet"
                                                                  "parameters" [{"name" "id" "in" "path"}]}}
                     "/pets/"                             {"get" {"operationId" "all-pets"}
                                                           "post" {"operationId" "create-pet"}}
                     "/users/{user-id}/orders/{order-id}" {"get" {"operationId" "order"
                                                                  "parameters" [{"name" "user-id" "in" "path"}
                                                                                {"name" "order-id" "in" "path"}]}}}}
           m (Martian. "https://api.org" swagger-definition)]

       (is (= "https://api.org/pets/123" (.urlFor m "load-pet" {"id" 123})))
       (is (= "https://api.org/pets/" (.urlFor m "all-pets")))
       (is (= "https://api.org/pets/" (.urlFor m "create-pet")))
       (is (= "https://api.org/users/123/orders/456" (.urlFor m "order" {"user-id" 123 "order-id" 456}))))))

(defrecord TestRecord [x y z])

(deftest keywordize-keys-test
  (let [swagger-definition {:paths {"/pets" {:post {:operationId "create-pet"
                                                    :parameters [{:name "record" :in "body"}]}}}}
        default (martian/bootstrap-swagger "https://api.org" swagger-definition)
        without (martian/bootstrap-swagger "https://api.org" swagger-definition {:interceptors (rest martian/default-interceptors)})
        record (-> (->TestRecord 1 2 3) (assoc "thing" 4 :five 5))
        params {:record record}
        actual-default (martian/request-for default :create-pet params)
        actual-without (martian/request-for without :create-pet params)]
    (is (= {:method :post, :url "https://api.org/pets", :body {:x 1, :y 2, :z 3, :thing 4, :five 5}}
           actual-default))
    (is (not (instance? TestRecord (:body actual-default))))
    (is (= {:method :post, :url "https://api.org/pets", :body record}
           actual-without))
    (is (instance? TestRecord (:body actual-without)))))

(def dev-mode-martian
  (let [routes [{:route-name :load-pet
                 :path-parts ["/pets/" :id]
                 :method :get
                 :path-schema {:id s/Int}
                 :interceptors [{:name ::fake-response
                                 :leave (fn [ctx]
                                          (assoc ctx :response {:status 200 :body "Pet!"}))}]}]]
    (martian/bootstrap "https://api.com" routes {})))

(deftest dev-mode-test
  (testing "martian instance can be a var or a function"
    (is (= dev-mode-martian
           (@#'martian/resolve-instance dev-mode-martian)
           (@#'martian/resolve-instance (constantly dev-mode-martian))
           (@#'martian/resolve-instance #'dev-mode-martian)))

    (is (= {:summary nil, :parameters {:id s/Int}, :returns {}}
           (martian/explore dev-mode-martian :load-pet)
           (martian/explore (constantly dev-mode-martian) :load-pet)
           (martian/explore #'dev-mode-martian :load-pet)))

    (is (= "https://api.com/pets/123"
           (martian/url-for dev-mode-martian :load-pet {:id 123})
           (martian/url-for (constantly dev-mode-martian) :load-pet {:id 123})
           (martian/url-for #'dev-mode-martian :load-pet {:id 123})))

    (is (= {:method :get, :url "https://api.com/pets/123"}
           (martian/request-for dev-mode-martian :load-pet {:id 123})
           (martian/request-for (constantly dev-mode-martian) :load-pet {:id 123})
           (martian/request-for #'dev-mode-martian :load-pet {:id 123})))

    (is (= {:status 200 :body "Pet!"}
           (martian/response-for dev-mode-martian :load-pet {:id 123})
           (martian/response-for (constantly dev-mode-martian) :load-pet {:id 123})
           (martian/response-for #'dev-mode-martian :load-pet {:id 123})))))

(def flat-body-definition
  (let [any-content-type (keyword "*/*")]
    {:openapi "3.0.0"
     :paths {(keyword "/open/{id}")   {:put {:operationId "update-open"
                                             :parameters [{:name "id" :in "path" :required true
                                                           :schema {:type "integer"}}
                                                          {:name "q" :in "query" :schema {:type "string"}}
                                                          {:name "X-Request-Id" :in "header" :schema {:type "string"}}]
                                             :requestBody {:required true
                                                           :content {any-content-type {:schema {:type "object"
                                                                                                :additionalProperties true
                                                                                                :properties {:name {:type "string"}}}}}}
                                             :responses {:200 {}}}}
             (keyword "/shared/{id}") {:put {:operationId "update-shared"
                                             :parameters [{:name "id" :in "path" :required true
                                                           :schema {:type "integer"}}]
                                             :requestBody {:required true
                                                           :content {any-content-type {:schema {:type "object"
                                                                                                :properties {:id {:type "integer"}
                                                                                                             :name {:type "string"}}}}}}
                                             :responses {:200 {}}}}
             (keyword "/closed/{id}") {:put {:operationId "update-closed"
                                             :parameters [{:name "id" :in "path" :required true
                                                           :schema {:type "integer"}}]
                                             :requestBody {:required true
                                                           :content {any-content-type {:schema {:type "object"
                                                                                                :properties {:name {:type "string"}}}}}}
                                             :responses {:200 {}}}}}}))

(deftest flat-body-params-test
  (let [m (martian/bootstrap-openapi "https://api.org" flat-body-definition)]
    (testing "an open body takes no param that another destination declares"
      (is (= {:method :put
              :url "https://api.org/open/1"
              :query-params {:q "x"}
              :headers {"X-Request-Id" "r-1" "X-Extra" "1"}
              :body {:name "n" :extra 1}}
             (martian/request-for m :update-open
                                  {:id 1 :q "x" :x-request-id "r-1" :name "n" :extra 1
                                   ::martian/request {:headers {"X-Extra" "1"}}}))))

    (testing "a param that the body declares too goes to both"
      (is (= {:method :put
              :url "https://api.org/shared/1"
              :body {:id 1 :name "n"}}
             (martian/request-for m :update-shared {:id 1 :name "n"}))))

    (testing "a closed body takes only the params it declares"
      (is (= {:name "n"}
             (:body (martian/request-for m :update-closed {:id 1 :name "n" :extra 1})))))))

(def flat-body-cases-definition
  (let [route (fn [operation-id params body]
                {:put {:operationId operation-id
                       :parameters params
                       :requestBody {:required true :content {(keyword "*/*") {:schema body}}}
                       :responses {:200 {}}}})
        path (fn [n] {:name n :in "path" :required true :schema {:type "integer"}})
        object (fn [& kvs] (apply assoc {:type "object"} kvs))]
    {:openapi "3.0.0"
     :paths {(keyword "/a/{petId}") (route "open-alias"
                                           [(path "petId")
                                            {:name "q" :in "query" :schema {:type "string"}}
                                            {:name "X-Request-Id" :in "header" :schema {:type "string"}}]
                                           (object :properties {:name {:type "string"}} :additionalProperties true))
             (keyword "/b/{id}") (route "open-shared" [(path "id")]
                                        (object :properties {:id {:type "integer"} :name {:type "string"}}
                                                :additionalProperties true))
             (keyword "/c/{petId}") (route "closed-shared-alias" [(path "petId")]
                                           (object :properties {:petId {:type "integer"} :name {:type "string"}}))
             (keyword "/d/{petId}") (route "open-shared-alias" [(path "petId")]
                                           (object :properties {:petId {:type "integer"} :name {:type "string"}}
                                                   :additionalProperties true))
             (keyword "/e/{id}") (route "nullable" [(path "id") {:name "q" :in "query" :schema {:type "string"}}]
                                        (object :nullable true :additionalProperties true
                                                :properties {:id {:type "integer"} :name {:type "string"}}))
             (keyword "/f/{id}") (route "defaulted" [(path "id") {:name "q" :in "query" :schema {:type "string"}}]
                                        (object :additionalProperties true
                                                :properties {:id {:type "integer"} :name {:type "string"}}
                                                :default {:name "dflt"}))
             (keyword "/g/{id}") (route "all-of" [(path "id")]
                                        {:allOf [(object :required ["id"]
                                                         :properties {:id {:type "integer"} :name {:type "string"}})
                                                 (object :properties {:tag {:type "string"}})]})
             (keyword "/h/{id}") (route "schemaless"
                                        [(path "id") {:name "q" :in "query" :schema {:type "string"}}]
                                        {})}}))

(def flat-body-cases-swagger-definition
  {:paths {(keyword "/s/{id}") {:put {:operationId "optional-body"
                                      :parameters [{:name "id" :in "path" :type "integer" :required true}
                                                   {:name "Thing" :in "body"
                                                    :schema {:type "object"
                                                             :properties {:id {:type "integer"}
                                                                          :name {:type "string"}}
                                                             :additionalProperties true}}
                                                   {:name "q" :in "query" :type "string"}]
                                      :responses {:200 {}}}}}})

(deftest flat-body-params-cases-test
  (let [m (martian/bootstrap-openapi "https://api.org" flat-body-cases-definition)
        request (fn [route-name params] (martian/request-for m route-name params))]
    (testing "a param given in its own key form or as its idiomatic alias stays out of an open body"
      (doseq [params [{:pet-id 1 :q "x" :x-request-id "r" :name "n" :extra 1}
                      {:petId 1 :q "x" :X-Request-Id "r" :name "n" :extra 1}
                      {"petId" 1 "q" "x" "X-Request-Id" "r" "name" "n" "extra" 1}]]
        (is (= {:method :put
                :url "https://api.org/a/1"
                :query-params {:q "x"}
                :headers {"X-Request-Id" "r"}
                :body {:name "n" :extra 1}}
               (request :open-alias params))
            (pr-str params))))

    (testing "a param that an open body declares too goes to both"
      (is (= {:method :put :url "https://api.org/b/1" :body {:id 1 :name "n" :extra 1}}
             (request :open-shared {:id 1 :name "n" :extra 1}))))

    (testing "a shared param given as its idiomatic alias goes to both"
      (is (= {:method :put :url "https://api.org/c/1" :body {:petId 1 :name "n"}}
             (request :closed-shared-alias {:pet-id 1 :name "n"})))
      (is (= {:method :put :url "https://api.org/d/1" :body {:petId 1 :name "n" :extra 1}}
             (request :open-shared-alias {:pet-id 1 :name "n" :extra 1}))))

    (testing "a body wrapped as nullable or with a default still declares its keys"
      (doseq [route-name [:nullable :defaulted]]
        (is (= {:query-params {:q "x"} :body {:id 1 :name "n" :extra 1}}
               (select-keys (request route-name {:id 1 :q "x" :name "n" :extra 1}) [:query-params :body]))
            (name route-name))))

    (testing "a body whose keys Martian can't tell takes every param but Martian's own"
      (is (= {:id 1 :name "n" :tag "t"} (:body (request :all-of {:id 1 :name "n" :tag "t"}))))
      (is (= {:method :put
              :url "https://api.org/h/1"
              :query-params {:q "x"}
              :headers {"X" "1"}
              :body {:id 1 :q "x" :name "n"}}
             (request :schemaless {:id 1 :q "x" :name "n" ::martian/request {:headers {"X" "1"}}}))))

    (testing "`::martian/body` wins over the flat params, and a nil one leaves the body flat"
      (is (= {:method :put
              :url "https://api.org/a/1"
              :query-params {:q "x"}
              :body {:name "n" :extra 1 :q "inbody"}}
             (request :open-alias {:pet-id 1 :q "x" :name "flat"
                                   ::martian/body {:name "n" :extra 1 :q "inbody"}})))
      (is (= {:name "n"} (:body (request :open-alias {:pet-id 1 :name "n" ::martian/body nil})))))))

(deftest flat-body-params-wrappers-test
  (testing "an optional Swagger body, which is `s/maybe`, still declares its keys"
    (let [m (martian/bootstrap-swagger "https://api.org" flat-body-cases-swagger-definition)]
      (is (= {:query-params {:q "x"} :body {:id 1 :name "n" :extra 1}}
             (select-keys (martian/request-for m :optional-body {:id 1 :q "x" :name "n" :extra 1})
                          [:query-params :body])))))

  (testing "a hand-written body in any wrapper still declares its keys"
    (doseq [:let [thing {:id s/Int :name s/Str s/Keyword s/Any}]
            [wrapper body-schema] [["st/default" (st/default thing {:id 0 :name "dflt"})]
                                   ["s/named" (s/named thing "Thing")]
                                   ["s/constrained" (s/constrained thing map?)]
                                   ["nested s/maybe" (s/maybe (s/maybe thing))]]]
      (let [m (martian/bootstrap "https://api.org" [{:route-name :put-thing
                                                     :path-parts ["/things/" :id]
                                                     :method :put
                                                     :path-schema {:id s/Int}
                                                     :query-schema {(s/optional-key :q) s/Str}
                                                     :body-schema {:thing body-schema}}])]
        (is (= {:query-params {:q "x"} :body {:id 1 :name "n" :extra 1}}
               (select-keys (martian/request-for m :put-thing {:id 1 :q "x" :name "n" :extra 1})
                            [:query-params :body]))
            wrapper))))

  (testing "form params stay out of a flat body"
    (let [m (martian/bootstrap "https://api.org" [{:route-name :upload
                                                   :path-parts ["/things/" :id]
                                                   :method :put
                                                   :path-schema {:id s/Int}
                                                   :form-schema {(s/optional-key :upload) s/Str}
                                                   :body-schema {:thing {:name s/Str s/Keyword s/Any}}}])]
      (is (= {:method :put
              :url "https://api.org/things/1"
              :form-params {:upload "u"}
              :body {:name "n" :extra 1}}
             (martian/request-for m :upload {:id 1 :upload "u" :name "n" :extra 1}))))))
