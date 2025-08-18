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
                                                           ;; operationId is intentionally missing from the get method
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
           (martian/explore m :all-pets)))))

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
                                                             assoc :x-api-key "ABC123"))}
        m (martian/bootstrap "https://defaultheaders.com"
                             [{:route-name :get-item
                               :produces ["application/json"]
                               :consumes ["application/json"]
                               :headers-schema {:x-api-key s/Str}
                               :path-parts ["/api/" :id]
                               :path-schema {:id s/Str}
                               :method :get}]
                             {:interceptors (concat [add-default-headers-interceptor] martian/default-interceptors)})]

    (is (= {:method :get
            :url "https://defaultheaders.com/api/123"
            :headers {"x-api-key" "ABC123"}}
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
                                                     :version 100}))))))))

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
                                 :headers-schema {(s/optional-key :camelToken) s/Str}
                                 :form-schema {:camelHumps (s/maybe s/Int)}}])]

    (is (= "https://camels.org/camels/1"
           (martian/url-for m :create-camel {:camel-id 1
                                             :camel-version 2})))

    (is (= "https://camels.org/camels/1?camelVersion=2"
           (martian/url-for m :create-camel {:camel-id 1
                                             :camel-version 2}
                            {:include-query? true})))

    (is (= {:path-schema {[] {:camel-id :camelId}},
            :query-schema {[] {:camel-version :camelVersion}},
            :body-schema {[] {:camel :Camel},
                          [:camel] {:camel-name :camelName,
                                    :camel-train :camelTrain,
                                    :any-camel :anyCamel},
                          [:camel :camel-train] {:leader-name :leaderName,
                                                 :follower-camels :followerCamels},
                          [:camel :camel-train :follower-camels] {:follower-name :followerName}},
            :form-schema {[] {:camel-humps :camelHumps}},
            :headers-schema {[] {:camel-token :camelToken}}}
           (:parameter-aliases (martian/handler-for m :create-camel))))

    (is (= {:method :put,
            :url "https://camels.org/camels/1",
            :query-params {:camelVersion 2},
            :body {:camelName "kebab"
                   :camelTrain {:leaderName "camel leader"
                                :followerCamels [{:followerName "OCaml"}]}
                   :anyCamel {:camel-train "choo choo"}},
            :form-params {:camelHumps 2},
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
      (is (= {:summary nil,
              :parameters
              {:camel-id                     s/Int,
               :camel-version                s/Int,
               :camel                        {:camel-name s/Str
                                              :camel-train {:leader-name s/Str
                                                            (s/optional-key :follower-camels) [{:follower-name s/Str}]}
                                              :any-camel s/Any},
               :camel-humps                  (s/maybe s/Int)
               (s/optional-key :camel-token) s/Str},
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
