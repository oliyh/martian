(ns martian.openapi-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [cheshire.core :refer [parse-string]]
            [schema.core :as s]
            [martian.openapi :refer [openapi->handlers]]))

(deftest openapi-sanity-check
  (is (= (-> (parse-string (slurp (io/resource "openapi.json")))
             (openapi->handlers ["application/json" "application/octet-stream"])
             (->> (filter #(= (:route-name %) :update-pet)))
             first
             (dissoc :openapi-definition))
         {:summary        "Update an existing pet"
          :description    "Update an existing pet by Id"
          :method         :put
          :produces       ["application/json"]
          :path-schema    {}
          :query-schema   {}
          :form-schema    {}
          :path-parts     ["/pet"]
          :headers-schema {}
          :consumes       ["application/json"]
          :body-schema
          {:body
           {(s/optional-key :id)       s/Int
            :name                      s/Str
            (s/optional-key :category) {(s/optional-key :id)   s/Int
                                        (s/optional-key :name) s/Str}
            :photoUrls                 [s/Str]
            (s/optional-key :tags)     [{(s/optional-key :id)   s/Int
                                         (s/optional-key :name) s/Str}]
            (s/optional-key :status)   (s/enum "sold" "pending" "available")}}
          :route-name     :update-pet
          :response-schemas
          [{:status (s/eq 200)
            :body
            {(s/optional-key :id)       s/Int
             :name                      s/Str
             (s/optional-key :category) {(s/optional-key :id)   s/Int
                                         (s/optional-key :name) s/Str}
             :photoUrls                 [s/Str]
             (s/optional-key :tags)     [{(s/optional-key :id)   s/Int
                                          (s/optional-key :name) s/Str}]
             (s/optional-key :status)   (s/enum "sold" "pending" "available")}}
           {:status (s/eq 400) :body nil}
           {:status (s/eq 404) :body nil}
           {:status (s/eq 405) :body nil}]})))
