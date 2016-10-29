(ns martian.test-test
  (:require [martian.core :as martian]
            [martian.test :as martian-test]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :as tct]
            [schema.core :as s]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest testing is run-tests]])))

#?(:cljs
   (def Throwable js/Error))

(def swagger-definition
  {:paths {(keyword "/pets/{id}") {:get {:operationId "load-pet"
                                         :parameters [{:in "path"
                                                       :name "id"
                                                       :type "integer"
                                                       :required true}]
                                         :responses {200 {:description "A pet"
                                                          :schema {:$ref "#/definitions/Pet"}}
                                                     404 {:schema {:type "string"}}}}}}
   :definitions {:Pet {:type "object"
                       :properties {:id {:type "integer"
                                         :required true}
                                    :name {:type "string"
                                           :required true}}}}})

(deftest generate-response-test
  (let [m (martian/bootstrap-swagger "https://api.com" swagger-definition
                                     {:interceptors (concat martian/default-interceptors
                                                            [martian-test/generate-response])})]

    (is (thrown-with-msg? Throwable #"Value cannot be coerced to match schema"
                          (martian/response-for m :load-pet {:id "abc"})))

    (is (nil? (s/check (s/conditional
                        #(= 200 (:status %)) {:status (s/eq 200)
                                              :body {:id s/Int
                                                     :name s/Str}}
                        #(= 404 (:status %)) {:status (s/eq 404)
                                              :body s/Str})
                       (martian/response-for m :load-pet {:id 123}))))))

(deftest generate-successful-response-test
  (let [m (martian/bootstrap-swagger "https://api.com" swagger-definition
                                     {:interceptors (concat martian/default-interceptors
                                                            [martian-test/generate-success-response])})]

    (is (nil? (s/check {:status (s/eq 200)
                        :body {:id s/Int
                               :name s/Str}}
                       (martian/response-for m :load-pet {:id 123}))))))

(deftest generate-error-response-test
  (let [m (martian/bootstrap-swagger "https://api.com" swagger-definition
                                     {:interceptors (concat martian/default-interceptors
                                                            [martian-test/generate-error-response])})]

    (is (nil? (s/check {:status (s/eq 404)
                        :body s/Str}
                       (martian/response-for m :load-pet {:id 123}))))))

;; want to be able to take a real martian built with prod code and turn it into a test one?
;; this would rely on the prod source of the mappings being available though, not guaranteed with the http/bootstrap-swagger

(deftest test-check-test
  ;; should be able to generate a response using a bootstrapped martian
  ;; should be able to make martian return it on the next request
  ;; should be able to assert that it has behaved as expected...

  ;; will need a martian which returns generated responses for a particular route, i think
  ;; set up the thing which generates the responses we want
  ;; e.g. response (martian-gen/response-for m :load-pet)
  ;; then need to be able to create a martian which will respond with that
  ;; e.g. (martian-test/constantly-respond response) (yields a martian that constantly responds with response)

  ;; then a function which is calling that endpoint, which will be some prod code function i want to test behaves properly
  ;; then i pass in the constant responder martian
  ;; then i assert that for all responses it either updates some atom or something with successful responses
  ;; or ignores the failing responses

  (let [martian (martian/bootstrap-swagger "https://api.com" swagger-definition)
        my-fn (fn [m]
                (let [{:keys [status body]} (martian/response-for m :load-pet {:id 123})]
                  (if (= 200 status)
                    body
                    nil)))
        p (prop/for-all [response (martian-test/response-for m :load-pet)]

                        (
                            (my-fn (martian-test/constantly-respond m response))))]

    (tct/assert-check (tc/quick-check 10 p))))
