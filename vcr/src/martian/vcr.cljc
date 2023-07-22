(ns martian.vcr
  (:require #?@(:clj [[clojure.java.io :as io]
                      [clojure.edn :as edn]
                      [fipp.clojure :as fipp]
                      [martian.interceptors :refer [remove-stack]]]
                :cljs [[martian.interceptors :refer [remove-stack]]])))

(defmulti persist-response! (fn [opts _ctx] (get-in opts [:store :kind])))
(defmulti load-response (fn [opts _ctx] (get-in opts [:store :kind])))

(defn- request-op [ctx]
  (get-in ctx [:handler :route-name]))

(defn- request-key [ctx]
  (:params ctx))

#?(:clj
   (defn- response-dir [{:keys [store]} ctx]
     (io/file (:root-dir store)
              (name (request-op ctx))
              (str (hash (request-key ctx))))))

#?(:clj
   (defn- last-response [opts ctx]
     (let [response-dir (response-dir opts ctx)]
       (when (.exists response-dir)
         (let [last-index (dec (count (.listFiles response-dir)))]
           (io/file response-dir (str last-index ".edn")))))))

#?(:clj
   (defn- cycled-response [opts ctx]
     (let [response-dir (response-dir opts ctx)]
       (when (.exists response-dir)
         (let [file-count (count (.listFiles response-dir))
               request-index (mod (::request-count ctx) file-count)]
           (io/file response-dir (str request-index ".edn")))))))

#?(:clj
   (defn- response-file [opts ctx]
     (io/file (response-dir opts ctx)
              (str (::request-count ctx) ".edn"))))

#?(:clj
   (defmethod persist-response! :file [{:keys [store] :as opts} {:keys [response] :as ctx}]
     (let [file (response-file opts ctx)]
       (io/make-parents file)
       (spit file (if (:pprint? store)
                    (with-out-str (fipp/pprint response))
                    (pr-str response))))))

#?(:clj
   (defmethod load-response :file [{:keys [extra-requests] :as opts} ctx]
     (let [file (response-file opts ctx)]
       (if (.exists file)
         (edn/read-string (slurp file))
         (some->
          (condp = extra-requests
            :repeat-last (last-response opts ctx)
            :cycle (cycled-response opts ctx)
            nil)
          slurp
          edn/read-string)))))

(defmethod persist-response! :atom [{:keys [store]} {:keys [response] :as ctx}]
  (swap! (:store store) assoc-in [(request-op ctx) (request-key ctx) (::request-count ctx)] response))

(defmethod load-response :atom [{:keys [store extra-requests]} ctx]
  (let [responses (get-in @(:store store) [(request-op ctx) (request-key ctx)])]
    (or (get responses (::request-count ctx))
        (condp = extra-requests
          :repeat-last (get responses (dec (count responses)))
          :cycle (get responses (mod (::request-count ctx) (count responses)))
          nil))))

(defn- inc-counter! [counters ctx]
  (let [k [(request-op ctx) (request-key ctx)]
        new-counters (swap! counters update k (fnil inc -1))]
    (get new-counters k)))

(defn record [opts]
  (let [counters (atom {})]
    {:name ::record
     :leave (fn [ctx]
              (let [request-count (inc-counter! counters ctx)]
                (persist-response! opts (assoc ctx ::request-count request-count)))
              ctx)}))

(defn playback [{:keys [on-missing-response] :as opts}]
  (let [counters (atom {})]
    {:name ::playback
     :enter (fn [ctx]
              (let [request-count (inc-counter! counters ctx)]
                (if-let [response (load-response opts (assoc ctx ::request-count request-count))]
                  (assoc (remove-stack ctx) :response response)
                  (condp = on-missing-response
                    :throw-error (let [message (str "No response stored for request " (request-op ctx) " " (request-key ctx))]
                                   (throw #?(:clj (Exception. message)
                                             :cljs (js/Error. message))))
                    :generate-404 (assoc (remove-stack ctx) :response {:status 404})
                    ctx))))}))
