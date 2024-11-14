(ns martian.vcr-test
  (:require #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer [deftest testing is]])
            [clojure.edn :as edn]
            [martian.vcr :as vcr]
            [martian.core :as m]
            [martian.interceptors :as mi]
            [schema.core :as s]
            #?(:clj [clojure.java.io :as io])))


#?(:cljs (def Exception js/Error))

(def dummy-response
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body "{\"foo\": \"bar\"}"})

(def dummy-responder
  {:name ::dummy
   :leave (fn [ctx] (assoc ctx :response dummy-response))})

(def cooked-response
  (assoc dummy-response :body {:foo "bar"}))

(def return-cooked-response
  "Test mimic resembling default-coerce-response but returning a cooked response.
  This is necessary to ensure that, even when using VCR, the interceptor chain
  is still correctly evaluated (including the :leave stage)."
  {:name ::coerce-response
   :leave (fn [ctx]
            (print "returning a cooked response for vcr tests")
            (assoc ctx :response cooked-response))})

(def interceptors
  "Like default-interceptors but adds response coercion to ensure the interceptor
  chain actually runs completely (including the :leave stage)."
  (conj m/default-interceptors return-cooked-response))

(def routes
  [{:route-name :load-pet
    :path-parts ["/pets/" :id]
    :method :get
    :path-schema {:id s/Int}}])

(def bootstrap
  (partial m/bootstrap "http://foo.com" routes))

(defn recording-boostrap
  [vcr-opts]
  (bootstrap {:interceptors (into interceptors [(vcr/record vcr-opts) dummy-responder])}))

(defn playback-boostrap
  [vcr-opts]
  (bootstrap {:interceptors (into interceptors [(vcr/playback vcr-opts)])}))

(deftest vcr-test
  #?(:clj
     (testing "file store"
       (let [opts {:store {:kind :file
                           :root-dir "target"
                           :pprint? true}
                   :extra-requests :repeat-last}]

         (testing "recording"
           (let [m (recording-boostrap opts)]
             (is (= cooked-response (m/response-for m :load-pet {:id 123})))
             (let [vcr-file (io/file "target" "load-pet" (str (hash {:id 123})) "0.edn")]
               (is (.exists vcr-file))
               (is (= dummy-response (edn/read-string (slurp vcr-file)))))))

         (testing "and playback"
           (let [m (playback-boostrap opts)]
             (is (= cooked-response (m/response-for m :load-pet {:id 123})))

             (testing "repeating last response"
               (is (= cooked-response (m/response-for m :load-pet {:id 123}))))))))))

(testing "atom store"
  (let [store (atom {})
        opts {:store {:kind :atom :store store}
              :extra-requests :repeat-last}]

    (testing "recording"
      (let [m (recording-boostrap opts)]
        (is (= cooked-response (m/response-for m :load-pet {:id 123})))
        (is (= dummy-response (get-in @store [:load-pet {:id 123} 0]))))

      (testing "and playback"
        (let [m (playback-boostrap opts)]
          (is (= cooked-response (m/response-for m :load-pet {:id 123})))

          (testing "repeating last response"
            (is (= cooked-response (m/response-for m :load-pet {:id 123})))))))))

(deftest playback-interceptor-test
  (let [store (atom {:load-pet {{:id 123} {0 {:status 200 :body "Hello"}
                                           1 {:status 200 :body "Goodbye"}}}})
        opts {:store {:kind :atom
                      :store store}}]

    (testing "entry present"
      (is (= {:status 200, :body "Hello"}
             (:response
              ((:enter (vcr/playback opts))
               {:params {:id 123}
                :handler {:route-name :load-pet}})))))

    (testing "entry missing"

      (testing "extra-requests strategies"
        (testing "repeat-last"
          (let [playback (vcr/playback (assoc opts :extra-requests :repeat-last))]
            (is (= [{:status 200, :body "Hello"}
                    {:status 200, :body "Goodbye"}
                    {:status 200, :body "Goodbye"}
                    {:status 200, :body "Goodbye"}]

                   (map :response (repeatedly 4 #((:enter playback)
                                                  {:params {:id 123}
                                                   :handler {:route-name :load-pet}})))))))

        (testing "cycle"
          (let [playback (vcr/playback (assoc opts :extra-requests :cycle))]
            (is (= [{:status 200, :body "Hello"}
                    {:status 200, :body "Goodbye"}
                    {:status 200, :body "Hello"}
                    {:status 200, :body "Goodbye"}]

                   (map :response (repeatedly 4 #((:enter playback)
                                                  {:params {:id 123}
                                                   :handler {:route-name :load-pet}}))))))))

      (testing "default behaviour (do nothing)"
        (is (nil? (:response
                   ((:enter (vcr/playback opts))
                    {:params {:id 999}
                     :handler {:route-name :load-pet}})))))

      (testing "throw error"
        (is (thrown-with-msg? Exception #"No response stored for request \:load-pet \{\:id 999\}"
                              ((:enter (vcr/playback (assoc opts :on-missing-response :throw-error)))
                               {:params {:id 999}
                                :handler {:route-name :load-pet}}))))

      (testing "generate 404"
        (is (= {:status 404}
               (:response
                ((:enter (vcr/playback (assoc opts :on-missing-response :generate-404)))
                 {:params {:id 999}
                  :handler {:route-name :load-pet}}))))))))
