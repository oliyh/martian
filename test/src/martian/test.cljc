(ns martian.test
  (:require [martian.core :as martian]
            [schema-generators.generators :as g]
            [schema.core :as s]))

(defn- status-range [from to]
  (fn [response-schemas]
    (some->> (filter #(<= from (g/generate (:status %)) to) response-schemas)
             first)))

(defn- choose-response-schema [response-type response-schemas]
  ((get {:random rand-nth
         :success (status-range 200 399)
         :error (status-range 400 599)}
        response-type
        rand-nth)
   response-schemas))

(defn- make-response [response-type response-schemas]
  (some->> response-schemas
           (choose-response-schema response-type)
           (g/generate)))

(def generate-response
  {:name ::generate-response
   :enter (fn [{:keys [handler] :as ctx}]
            (assoc ctx :response (make-response :random (:response-schemas handler))))})

(def generate-error-response
  {:name ::generate-error-response
   :enter (fn [{:keys [handler] :as ctx}]
            (assoc ctx :response (make-response :error (:response-schemas handler))))})

(def generate-success-response
  {:name ::generate-success-response
   :enter (fn [{:keys [handler] :as ctx}]
            (assoc ctx :response (make-response :success (:response-schemas handler))))})
