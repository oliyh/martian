(ns martian.interceptors-test
  (:require [martian.interceptors :as i]
            [martian.encoders :as encoders]
            [tripod.context :as tc]
            [schema.core :as s]
            #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest testing is]])
            #?(:clj [martian.test-utils :as tu])))

#?(:cljs
   (def Throwable js/Error))

(deftest encode-request-test
  (let [i i/default-encode-request]

    #?(:bb nil
       :default
       (testing "simple encoders"
         (let [body {:the "wheels"
                     :on "the"
                     :bus ["go" "round" "and" "round"]}]
           (testing "form"
             (is (= {:body #?(:clj  "the=wheels&on=the&bus=go&bus=round&bus=and&bus=round"
                              :cljs "the=wheels&on=the&bus=go%2Cround%2Cand%2Cround")
                     :headers {"Content-Type" "application/x-www-form-urlencoded"}}
                    (:request ((:enter i) {:request {:body body}
                                           :handler {:consumes ["application/x-www-form-urlencoded"]}}))))))))

    (testing "complex encoders"
      (let [body {:the {:wheels ["on" "the" "bus"]}
                  :go {:round {:and "round"}}}]
        (testing "json"
          (is (= {:body (encoders/json-encode body)
                  :headers {"Content-Type" "application/json"}}
                 (:request ((:enter i) {:request {:body body}
                                        :handler {:consumes ["application/json"]}})))))

        (testing "edn"
          (is (= {:body (pr-str body)
                  :headers {"Content-Type" "application/edn"}}
                 (:request ((:enter i) {:request {:body body}
                                        :handler {:consumes ["application/edn"]}})))))

        (testing "transit"
          (testing "+json"
            (is (= {:body body
                    :headers {"Content-Type" "application/transit+json"}}
                   (-> ((:enter i) {:request {:body body}
                                    :handler {:consumes ["application/transit+json"]}})
                       :request
                       (update :body #?(:clj  (comp #(encoders/transit-decode % :json)
                                                    tu/input-stream->byte-array)
                                        :cljs #(encoders/transit-decode % :json)))))))

          #?(:bb nil
             :clj
             (testing "+msgpack"
               (is (= {:body body
                       :headers {"Content-Type" "application/transit+msgpack"}}
                      (-> ((:enter i) {:request {:body body}
                                       :handler {:consumes ["application/transit+msgpack"]}})
                          :request
                          (update :body (comp #(encoders/transit-decode % :msgpack)
                                              tu/input-stream->byte-array)))))))))

      #?(:clj
         (testing "multipart"
           (let [body {:alpha 12345
                       :omega "abc"}
                 i (i/encode-request
                     (assoc (encoders/default-encoders)
                       "multipart/form-data" {:encode encoders/multipart-encode}))]
             (is (= {:multipart [{:name "alpha" :content "12345"}
                                 {:name "omega" :content "abc"}]
                     :headers nil}
                    (:request ((:enter i) {:request {:body body}
                                           :handler {:consumes ["multipart/form-data"]}}))))))))))

(defn- stub-response
  ([content-type-val body]
   (stub-response :content-type content-type-val body))
  ([content-type-key content-type-val body]
   {:name ::stub-response
    :enter (fn [ctx]
             (assoc ctx :response {:body body
                                   :headers {content-type-key content-type-val}}))}))

(deftest coerce-response-test
  (let [i i/default-coerce-response]

    #?(:bb nil
       :default
       (testing "simple decoders"
         (let [body {:the "wheels"
                     :on "the"
                     :bus ["go" "round" "and" "round"]}]
           (testing "form"
             (let [ctx (tc/enqueue* {} [i (stub-response "application/x-www-form-urlencoded" "the=wheels&on=the&bus=go&bus=round&bus=and&bus=round")])]
               (is (= body
                      (-> (tc/execute ctx) :response :body))))))))

    (testing "complex decoders"
      (let [body {:the {:wheels ["on" "the" "bus"]}
                  :go {:round {:and "round"}}}]
        (testing "json"
          (let [ctx (tc/enqueue* {} [i (stub-response "application/json" (encoders/json-encode body))])]
            (is (= body
                   (-> (tc/execute ctx) :response :body)))))

        (testing "edn"
          (let [ctx (tc/enqueue* {} [i (stub-response "application/edn" (pr-str body))])]
            (is (= body
                   (-> (tc/execute ctx) :response :body)))))

        (testing "transit"
          (testing "+json"
            (let [ctx (tc/enqueue* {} [i (stub-response "application/transit+json"
                                                        #?(:clj  (slurp (encoders/transit-encode body :json))
                                                           :cljs (encoders/transit-encode body :json)))])]
              (is (= body
                     (-> (tc/execute ctx) :response :body)))))

          #?(:bb nil
             :clj
             (testing "+msgpack"
               (let [ctx (tc/enqueue* {} [i (stub-response "application/transit+msgpack"
                                                           (tu/input-stream->byte-array (encoders/transit-encode body :msgpack)))])]
                 (is (= body
                        (-> (tc/execute ctx) :response :body)))))))))

    (testing "alternative content-type header"
      (let [body {:the {:wheels ["on" "the" "bus"]}
                  :go {:round {:and "round"}}}]
        (testing "Content-Type"
          (let [ctx (tc/enqueue* {} [i (stub-response "Content-Type" "application/json" (encoders/json-encode body))])]
            (is (= body
                   (-> (tc/execute ctx) :response :body)))))

        (testing "content-type"
          (let [ctx (tc/enqueue* {} [i (stub-response "content-type" "application/json" (encoders/json-encode body))])]
            (is (= body
                   (-> (tc/execute ctx) :response :body)))))))))

(deftest custom-encoding-test
  (testing "a user can support an encoding that martian doesn't know about by default"
    (let [reverse-string #(apply str (reverse %))
          encoders (assoc (encoders/default-encoders)
                          "text/magical+json" {:encode (comp reverse-string encoders/json-encode)
                                               :decode (comp #(encoders/json-decode % keyword) reverse-string)
                                               :as :magic})
          body {:the {:wheels ["on" "the" "bus"]}
                :go {:round {:and "round"}}}
          encoded-body (-> body encoders/json-encode reverse-string)
          ctx (tc/enqueue* {:request {:body body}
                              :handler {:consumes ["text/magical+json"]
                                        :produces ["text/magical+json"]}}
                             [(i/encode-request encoders)
                              (i/coerce-response encoders)
                              (stub-response "text/magical+json" encoded-body)])
          result (tc/execute ctx)]

      (is (= {:body encoded-body
              :headers {"Content-Type" "text/magical+json"
                        "Accept" "text/magical+json"}
              :as :magic}
             (:request result)))

      (is (= {:body body
              :headers {:content-type "text/magical+json"}}
             (:response result))))))

(deftest auto-encoder-test
  (testing "when the server speaks a language martian doesn't understand it leaves it alone"
    (let [reverse-string #(apply str (reverse %))
          body {:the {:wheels ["on" "the" "bus"]}
                :go {:round {:and "round"}}}
          encoded-body (-> body encoders/json-encode reverse-string)
          ctx (tc/enqueue* {:request {:body encoded-body
                                        :headers {"Content-Type" "text/magical+json"
                                                  "Accept" "text/magical+json"}}
                              :handler {:consumes ["text/magical+json"]
                                        :produces ["text/magical+json"]}}
                             [i/default-encode-request
                              i/default-coerce-response
                              (stub-response "text/magical+json" encoded-body)])
          result (tc/execute ctx)]

      (is (= {:body encoded-body
              :headers {"Content-Type" "text/magical+json"
                        "Accept" "text/magical+json"}
              :as :auto}
             (:request result)))

      (is (= {:body encoded-body
              :headers {:content-type "text/magical+json"}}
             (:response result))))))

(deftest supported-content-types-test
  (testing "picks up the supported content-types from the encoding/decoding interceptors"
    (let [encode-request i/default-encode-request
          coerce-response (i/coerce-response (assoc (encoders/default-encoders)
                                                    "text/magical+json" {:encode encoders/json-encode
                                                                         :decode #(encoders/json-decode % keyword)
                                                                         :as :magic}))]

      (is (= {:encodes #?(:bb #{"application/json" "application/transit+msgpack" "application/transit+json" "application/edn"}
                          :clj #{"application/json" "application/transit+msgpack" "application/transit+json" "application/edn" "application/x-www-form-urlencoded"}
                          :cljs #{"application/json" "application/transit+json" "application/edn" "application/x-www-form-urlencoded"})
              :decodes #?(:bb #{"application/json" "text/magical+json" "application/transit+msgpack" "application/transit+json" "application/edn"}
                          :clj #{"application/json" "text/magical+json" "application/transit+msgpack" "application/transit+json" "application/edn" "application/x-www-form-urlencoded"}
                          :cljs #{"application/json" "text/magical+json" "application/transit+json" "application/edn" "application/x-www-form-urlencoded"})}
             (i/supported-content-types [encode-request coerce-response]))))))

(deftest validate-response-test
  (let [handler {:response-schemas [{:status (s/eq 200),
                                     :body {(s/optional-key :name) (s/maybe s/Str),
                                            (s/optional-key :age) (s/maybe s/Int),
                                            (s/optional-key :type) (s/maybe s/Str)}}
                                    {:status (s/eq 400)
                                     :body s/Str}
                                    {:status (s/eq 404)
                                     :body s/Str}]}
        happy? (fn [response & [strict?]]
                 (let [ctx {:response response
                            :handler handler}]
                   (= ctx ((:leave (i/validate-response-body {:strict? strict?})) ctx))))]

    (is (happy? {:status 200
                 :body {:name "Dupont"
                        :age 4
                        :type "Goldfish"}}))

    (is (happy? {:status 400
                 :body "You must supply a pet id"}))

    (is (happy? {:status 404
                 :body "No pet with that id"}))

    (is (thrown-with-msg?
         Throwable
         #"Value does not match schema"
         (happy? {:status 200
                  :body "Here is you pet :)"})))

    (is (thrown-with-msg?
         Throwable
         #"Value does not match schema"
         (happy? {:status 400
                  :body {:message "Bad times"}})))


    (testing "does not allow responses that aren't defined in strict mode"
      (is (thrown-with-msg?
           Throwable
           #"No response body schema found for status 500"
           (happy? {:status 500
                    :body {:message "That did not go well"}}
                   true))))

    (testing "allows responses that aren't defined in non-strict mode"
      (is (happy? {:status 500
                   :body {:message "That did not go well"}}
                  false)))))
