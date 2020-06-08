(ns martian.vcr
  (:require [clojure.java.io :as io]
            [martian.core :as m]
            [schema.core :as s]))

(def dummy-response
  {:name ::dummy
   :leave (fn [ctx]
            (assoc ctx :response {:status 200
                                  :headers {"Content-Type" "application/json"}
                                  :body {:foo "bar"}}))})

(defn record [{:keys [root-dir]}]
  {:name ::record
   :leave (fn [{:keys [response] :as ctx}]
            (println ctx)
            (let [dir (io/file root-dir (name (get-in ctx [:handler :route-name])))
                  file (io/file dir (str (hash (:params ctx)) ".edn"))]
              (io/make-parents file)
              (spit file (pr-str response))))})

(defn playback []
  {:name ::playback
   :enter (fn [ctx])})

;;???
;; or better to point towards using inject-interceptors;; and let user decide how to stick it in
(defn with-recording [interceptors])

;;???
(defn with-playback [interceptors])


;; todo tests
;; options
;; - pprint? maybe use fipp?
;; - select-keys on response?
;; - supply own file hash?
;; ---- is current hash even good enough? test with maps in different order
;; how should playback behave if file is missing?


(def m (m/bootstrap "http://foo.com" [{:route-name :load-pet
                                       :path-parts ["/pets/" :id]
                                       :method :get
                                       :path-schema {:id s/Int}}]
                    {:interceptors (into m/default-interceptors [(record {:root-dir "target"})
                                                                 dummy-response])}))

(m/response-for m :load-pet {:id 123})
