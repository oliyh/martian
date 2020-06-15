(ns martian.vcr-test
  (:require #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer [deftest testing is]])
            [martian.vcr :as vcr]
            [martian.core :as m]
            [schema.core :as s]
            #?(:clj [clojure.java.io :as io])))

(def dummy-response
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body {:foo "bar"}})

(def dummy-responder
  {:name ::dummy
   :leave (fn [ctx]
            (assoc ctx :response dummy-response))})

(def routes [{:route-name :load-pet
              :path-parts ["/pets/" :id]
              :method :get
              :path-schema {:id s/Int}}])

(deftest vcr-test
  #?(:clj
     (testing "file store"
       (let [opts {:store-type :file
                   :root-dir "target"}]

         (testing "recording"
           (let [m (m/bootstrap "http://foo.com" routes {:interceptors (into m/default-interceptors
                                                                             [(vcr/record opts)
                                                                              dummy-responder])})]
             (is (= dummy-response (m/response-for m :load-pet {:id 123})))
             (is (.exists (io/file "target" "load-pet" (str (hash {:id 123}) ".edn")))))

           (testing "and playback"
             (let [m (m/bootstrap "http://foo.com" routes {:interceptors (into m/default-interceptors
                                                                               [(vcr/playback opts)])})]
               (is (= dummy-response (m/response-for m :load-pet {:id 123})))))))))

  (testing "atom store"
    (let [store (atom {})
          opts {:store-type :atom
                :store store}]

      (testing "recording"
        (let [m (m/bootstrap "http://foo.com" routes {:interceptors (into m/default-interceptors
                                                                          [(vcr/record opts)
                                                                           dummy-responder])})]
          (is (= dummy-response (m/response-for m :load-pet {:id 123})))
          (is (= dummy-response (get-in @store [:load-pet {:id 123}]))))

        (testing "and playback"
          (let [m (m/bootstrap "http://foo.com" routes {:interceptors (into m/default-interceptors
                                                                            [(vcr/playback opts)])})]
            (is (= dummy-response (m/response-for m :load-pet {:id 123})))))))))
