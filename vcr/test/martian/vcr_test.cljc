(ns martian.vcr-test
  (:require #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer [deftest testing is]])
            [martian.vcr :as vcr]
            [martian.core :as m]
            [schema.core :as s]
            #?(:clj [clojure.java.io :as io])))

#?(:cljs (def Exception js/Error))

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
       (let [opts {:store {:kind :file
                           :root-dir "target"
                           :pprint? true}}]

         (testing "recording"
           (let [m (m/bootstrap "http://foo.com" routes {:interceptors (into m/default-interceptors
                                                                             [(vcr/record opts)
                                                                              dummy-responder])})]
             (is (= dummy-response (m/response-for m :load-pet {:id 123})))
             (is (.exists (io/file "target" "load-pet" (str (hash {:id 123})) "1.edn"))))

           (testing "and playback"
             (let [m (m/bootstrap "http://foo.com" routes {:interceptors (into m/default-interceptors
                                                                               [(vcr/playback opts)])})]
               (is (= dummy-response (m/response-for m :load-pet {:id 123})))))))))

  (testing "atom store"
    (let [store (atom {})
          opts {:store {:kind :atom
                        :store store}}]

      (testing "recording"
        (let [m (m/bootstrap "http://foo.com" routes {:interceptors (into m/default-interceptors
                                                                          [(vcr/record opts)
                                                                           dummy-responder])})]
          (is (= dummy-response (m/response-for m :load-pet {:id 123})))
          (is (= dummy-response (get-in @store [:load-pet {:id 123} 1]))))

        (testing "and playback"
          (let [m (m/bootstrap "http://foo.com" routes {:interceptors (into m/default-interceptors
                                                                            [(vcr/playback opts)])})]
            (is (= dummy-response (m/response-for m :load-pet {:id 123})))))))))

(deftest playback-interceptor-test
  (let [store (atom {:load-pet {{:id 123} {1 {:status 200 :body "Hello"}}}})
        opts {:store {:kind :atom
                      :store store}}]

    (testing "entry present"
      (is (= {:status 200, :body "Hello"}
             (:response
              ((:enter (vcr/playback opts))
               {::vcr/request-count 1
                :params {:id 123}
                :handler {:route-name :load-pet}})))))

    (testing "entry missing"

      (testing "default behaviour (do nothing)"
        (is (nil? (:response
                   ((:enter (vcr/playback opts))
                    {::vcr/request-count 1
                     :params {:id 999}
                     :handler {:route-name :load-pet}})))))

      (testing "throw error"
        (is (thrown-with-msg? Exception #"No response stored for request \:load-pet \{\:id 999\}"
                              ((:enter (vcr/playback (assoc opts :on-missing-response :throw-error)))
                               {::vcr/request-count 1
                                :params {:id 999}
                                :handler {:route-name :load-pet}}))))

      (testing "generate 404"
        (is (= {:status 404}
               (:response
                ((:enter (vcr/playback (assoc opts :on-missing-response :generate-404)))
                 {::vcr/request-count 1
                  :params {:id 999}
                  :handler {:route-name :load-pet}}))))))))
