(ns martian.vcr
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defmulti persist-response! (fn [opts _ctx] (:store-type opts)))
(defmulti load-response (fn [opts _ctx] (:store-type opts)))

(defn- request-op [ctx]
  (get-in ctx [:handler :route-name]))

(defn- request-key [ctx]
  (:params ctx))

#?(:clj
   (defn- response-file [{:keys [root-dir]} ctx]
     (io/file root-dir (name (request-op ctx)) (str (hash (request-key ctx)) ".edn"))))

#?(:clj
   (defmethod persist-response! :file [opts {:keys [response] :as ctx}]
     (let [file (response-file opts ctx)]
       (io/make-parents file)
       (spit file (pr-str response)))))

#?(:clj
   (defmethod load-response :file [opts ctx]
     (let [file (response-file opts ctx)]
       (when (.exists file)
         (edn/read-string (slurp file))))))

(defmethod persist-response! :atom [{:keys [store]} {:keys [response] :as ctx}]
  (swap! store assoc-in [(request-op ctx) (request-key ctx)] response))

(defmethod load-response :atom [{:keys [store]} ctx]
  (get-in @store [(request-op ctx) (request-key ctx)]))

(defn record [opts]
  {:name ::record
   :leave (fn [ctx]
            (persist-response! opts ctx)
            ctx)})

(defn playback [opts]
  {:name ::playback
   :enter (fn [ctx]
            (if-let [response (load-response opts ctx)]
              (assoc ctx :response response)
              ctx))})

;; options
;; - pprint? maybe use fipp?
;; - select-keys on response?
;; - supply own file hash?
;; ---- is current hash even good enough? test with maps in different order
;; how should playback behave if file is missing?
