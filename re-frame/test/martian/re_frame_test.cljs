(ns martian.re-frame-test
  (:require [martian.re-frame :as martian]
            [martian.core :as mc]
            [cljs.test :refer-macros [deftest testing is run-tests async]]
            [re-frame.core :as re-frame]
            [re-frame.db :as rdb]
            [day8.re-frame.test :as rf-test]))

(def interceptors [re-frame/trim-v])

(re-frame/reg-event-db
 ::create-pet-success
 interceptors
 (fn [db [{:keys [body]} operation-id params]]
   (assoc db :pet-id (:id body))))

(re-frame/reg-event-db
 ::http-failure
 interceptors
 (fn [db [response-or-error operation-id params]]
   (update db :errors conj [operation-id response-or-error])))

(deftest re-frame-test
  (rf-test/run-test-async

   (martian/init "http://localhost:8888/swagger.json")

   (rf-test/wait-for
    [::martian/init]

    (testing "can subscribe to the instance"
      (let [m @(re-frame/subscribe [::martian/instance])]
        (is (= "http://localhost:8888/pets/123"
               (mc/url-for m :get-pet {:id 123})))))

    (testing "can make http requests by dispatching an event"
      (re-frame/dispatch [::martian/request
                          :create-pet
                          {:name "Doggy McDogFace"
                           :type "Dog"
                           :age 3}
                          ::create-pet-success
                          ::http-failure])

      (rf-test/wait-for
       [#{::create-pet-success ::http-failure}]
       (is (= 123 (:pet-id @rdb/app-db))))))))

(deftest async-init-test
  (rf-test/run-test-async

   (testing "dispatch request before init"
     (re-frame/dispatch [::martian/request
                         :create-pet
                         {:name "Doggy McDogFace"
                          :type "Dog"
                          :age 3}
                         ::create-pet-success
                         ::http-failure])
     (martian/init "http://localhost:8888/swagger.json")

     (testing "request is dispatched once init has occurred"
       (rf-test/wait-for
        [#{::create-pet-success ::http-failure}]
        (is (= 123 (:pet-id @rdb/app-db))))))))

(deftest re-frame-failure-test
  (rf-test/run-test-async

   (martian/init "http://localhost:8888/swagger.json")

   (rf-test/wait-for
    [::martian/init]

    (testing "calls failure handler when input coercion error occurs"
      (re-frame/dispatch [::martian/request
                          :create-pet
                          {:name 1}
                          ::create-pet-success
                          ::http-failure])

      (rf-test/wait-for
       [#{::create-pet-success ::http-failure}]
       (let [[op-id error] (last (:errors @rdb/app-db))]
         (is (= :create-pet op-id))
         (is (= "Interceptor Exception: Could not coerce value to schema: {:Pet {:name (not (string? 1)), :type missing-required-key, :age missing-required-key}}"
                (.-message error)))))))))

(deftest re-frame-pending-requests-test
  (rf-test/run-test-async

   (re-frame/reg-event-db
    ::create-pet-success
    (fn [db]
      (is (= :create-pet (ffirst @(re-frame/subscribe [::martian/pending-requests]))))
      db))

   (martian/init "http://localhost:8888/swagger.json")

   (rf-test/wait-for
    [::martian/init]

    (testing "can make http requests by dispatching an event"
      (re-frame/dispatch [::martian/request
                          :create-pet
                          {:name "Doggy McDogFace"
                           :type "Dog"
                           :age 3}
                          ::create-pet-success
                          ::http-failure]))

    (rf-test/wait-for
     [::martian/on-complete]
     (is (empty? @(re-frame/subscribe [::martian/pending-requests])))))))

(deftest deduplicate-requests-test
  (rf-test/run-test-async

   (let [request-counter (atom {})]

     (re-frame/reg-fx
      ::martian/request
      (fn [[_ _ operation-id & args]]
        (swap! request-counter update operation-id inc)))

     (martian/init "http://localhost:8888/swagger.json")

     (rf-test/wait-for
      [::martian/init]

      (testing "duplicate requests are only made once"
        (let [req [::martian/request
                   :create-pet
                   {:name "Doggy McDogFace"
                    :type "Dog"
                    :age 3}
                   ::create-pet-success
                   ::http-failure]]
          (re-frame/dispatch-sync req)
          (re-frame/dispatch-sync req)
          (re-frame/dispatch-sync [::martian/request :get-pet {:id 123}])))

      (re-frame/dispatch [::test-complete])
      (rf-test/wait-for
       [::test-complete]

       (is (= {:create-pet 1
               :get-pet 1}
              @request-counter)))))))

(deftest re-frame-w-custom-id-test
  (rf-test/run-test-async

   (martian/init "http://localhost:8888/swagger.json" {::martian/instance-id :custom-id})

   (rf-test/wait-for
    [::martian/init]

    (testing "can subscribe to the instance"
      (let [m @(re-frame/subscribe [::martian/instance :custom-id])]
        (is (= "http://localhost:8888/pets/123"
               (mc/url-for m :get-pet {:id 123})))))

    (testing "can make http requests by dispatching an event"
      (re-frame/dispatch [::martian/request
                          :create-pet
                          {:name "Doggy McDogFace"
                           :type "Dog"
                           :age 3
                           ::martian/instance-id :custom-id}
                          ::create-pet-success
                          ::http-failure])

      (rf-test/wait-for
       [#{::create-pet-success ::http-failure}]
       (is (= 123 (:pet-id @rdb/app-db))))))))

(deftest re-frame-pending-requests-w-custom-id-test
  (rf-test/run-test-async

   (re-frame/reg-event-db
    ::create-pet-success
    (fn [db]
      (is (= :create-pet (ffirst @(re-frame/subscribe [::martian/pending-requests :custom-id]))))
      db))

   (martian/init "http://localhost:8888/swagger.json" {::martian/instance-id :custom-id})

   (rf-test/wait-for
    [::martian/init]

    (testing "can make http requests by dispatching an event"
      (re-frame/dispatch [::martian/request
                          :create-pet
                          {:name "Doggy McDogFace"
                           :type "Dog"
                           :age 3
                           ::martian/instance-id :custom-id}
                          ::create-pet-success
                          ::http-failure]))

    (rf-test/wait-for
     [::martian/on-complete]
     (is (empty? @(re-frame/subscribe [::martian/pending-requests :custom-id])))))))
