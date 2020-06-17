(ns martian.vcr
  (:require #?@(:clj [[clojure.java.io :as io]
                      [clojure.edn :as edn]
                      [fipp.clojure :as fipp]])))

(defmulti persist-response! (fn [opts _ctx] (get-in opts [:store :kind])))
(defmulti load-response (fn [opts _ctx] (get-in opts [:store :kind])))

(defn- request-op [ctx]
  (get-in ctx [:handler :route-name]))

(defn- request-key [ctx]
  (:params ctx))

#?(:clj
   (defn- response-file [{:keys [store]} ctx]
     (io/file (:root-dir store) (name (request-op ctx)) (str (hash (request-key ctx)) ".edn"))))

#?(:clj
   (defmethod persist-response! :file [{:keys [store] :as opts} {:keys [response] :as ctx}]
     (let [file (response-file opts ctx)]
       (io/make-parents file)
       (spit file (if (:pprint? store)
                    (with-out-str (fipp/pprint response))
                    (pr-str response))))))

#?(:clj
   (defmethod load-response :file [opts ctx]
     (let [file (response-file opts ctx)]
       (when (.exists file)
         (edn/read-string (slurp file))))))

(defmethod persist-response! :atom [{:keys [store]} {:keys [response] :as ctx}]
  (swap! (:store store) assoc-in [(request-op ctx) (request-key ctx)] response))

(defmethod load-response :atom [{:keys [store]} ctx]
  (get-in @(:store store) [(request-op ctx) (request-key ctx)]))

(defn record [opts]
  {:name ::record
   :leave (fn [ctx]
            (persist-response! opts ctx)
            ctx)})

(defn playback [{:keys [on-missing-response] :as opts}]
  {:name ::playback
   :enter (fn [ctx]
            (if-let [response (load-response opts ctx)]
              (assoc ctx :response response)
              (condp = on-missing-response
                :throw-error (let [message (str "No response stored for request " (request-op ctx) " " (request-key ctx))]
                               (throw #?(:clj (Exception. message)
                                         :cljs (js/Error. message))))
                :generate-404 (assoc ctx :response {:status 404})
                ctx)))})
