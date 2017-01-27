(ns martian.core-test
  (:require [martian.core :as martian]
            [schema.core :as s]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest testing is run-tests]]))
  #?(:clj (:import [martian Martian])))

#?(:cljs
   (def Throwable js/Error))

(def swagger-definition
  {:paths {(keyword "/pets/{id}")                         {:get {:operationId "load-pet"
                                                                 :summary "Loads a pet by id"
                                                                 :parameters [{:name "id"
                                                                               :in "path"
                                                                               :type "integer"}]
                                                                 :responses {200 {:description "The pet requested"
                                                                                  :schema {:$ref "#/definitions/Pet"}}}}}
           (keyword "/pets/")                             {:get {:operationId "all-pets"
                                                                 :parameters [{:name "sort"
                                                                               :in "query"
                                                                               :enum ["desc","asc"]
                                                                               :required false}]
                                                                 :responses {200 {:description "An array of all the pets"
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
                                                                              {:name "auth-token"
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
      (is (thrown-with-msg? Throwable #"Value cannot be coerced to match schema"
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

    (is (= {:method :get
            :url "https://api.org/pets/123"}
           (request-for :load-pet {:id 123})
           (request-for :load-pet {:id "123"})))

    (is (= {:method :get
            :url "https://api.org/pets/"}
           (request-for :all-pets {})))

    (is (= {:method :get
            :url "https://api.org/pets/"
            :query-params {:sort "asc"}}
           (request-for :all-pets {:sort "asc"})
           (request-for :all-pets {:sort :asc})))

    (is (= {:method :get
            :url "https://api.org/users/123/orders/234"
            :headers {"auth-token" "abc-1234"}}
           (request-for :order {:user-id 123 :order-id 234 :auth-token "abc-1234"})))

    (is (= {:method :post
            :url "https://api.org/pets/"
            :body {:id 123 :name "charlie"}}

           ;; these three forms are equivalent
           (request-for :create-pet {:id 123 :name "charlie"})
           (request-for :create-pet {:pet {:id 123 :name "charlie"}})
           (request-for :create-pet {::martian/body {:id 123 :name "charlie"}})

           (request-for :create-pet {:pet {:id "123" :name "charlie"}})))

    (is (= {:method :post
            :url "https://api.org/users/"
            :body [{:id 1 :name "Bob"}
                   {:id 2 :name "Barry"}]}
           (request-for :create-users {:users [{:id 1 :name "Bob"}
                                               {:id 2 :name "Barry"}]})))

    (is (= {:method :post
            :url "https://api.org/orders/"
            :body ["order-number-one"
                   "order-number-two"]}
           (request-for :create-orders {:order-ids ["order-number-one" "order-number-two"]})))

    (is (= {:method :put
            :url "https://api.org/pets/"
            :form-params {:id 123 :name "nigel"}}
           (request-for :update-pet {:id 123 :name "nigel"})))

    (testing "providing initial request map"
      (is (= {:method :get
              :url "https://api.org/pets/"
              :form-params {:id 123 :name "nigel"}}
             (request-for :update-pet {::martian/request {:method :get}
                                       :id 123
                                       :name "nigel"}))))

    (testing "exceptions"
      (is (thrown-with-msg? Throwable #"Value cannot be coerced to match schema"
                            (request-for :all-pets {:sort "baa"})))

      (is (thrown-with-msg? Throwable #"Value cannot be coerced to match schema"
                            (request-for :load-pet {:id "one"})))

      (is (thrown-with-msg? Throwable #"Value cannot be coerced to match schema"
                            (request-for :create-pet {:pet {:id "one"
                                                            :name 1}})))

      (is (thrown-with-msg? Throwable #"Value cannot be coerced to match schema: \{:id missing-required-key, :name missing-required-key\}"
                            (request-for :create-pet))))))

(deftest with-interceptors-test
  (let [auth-headers-interceptor {:name ::auth-headers
                                  :enter (fn [ctx]
                                           (update-in ctx [:request :headers] merge {"auth-token" "1234-secret"}))}
        m (martian/bootstrap-swagger "https://api.org" swagger-definition
                                     {:interceptors (concat [auth-headers-interceptor] martian/default-interceptors)})
        request-for (partial martian/request-for m)]

    (is (= {:method :get
            :url "https://api.org/pets/123"
            :headers {"auth-token" "1234-secret"}}
           (request-for :load-pet {:id 123})))))

#?(:clj
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
