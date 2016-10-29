(ns martian.test
  (:require [martian.core :as martian]
            [schema-generators.generators :as g]
            [clojure.test.check.generators :as tcg]
            [schema.core :as s]))

(defn- status-range [from to]
  (fn [{:keys [status]}]
    (<= from (g/generate status) to)))

(defn- filter-response-schema [response-type response-schemas]
  (let [filter-fn (get {:random (constantly true)
                        :success (status-range 200 399)
                        :error (status-range 400 599)}
                       response-type)]
   (filter filter-fn response-schemas)))

(defn- make-generator [response-type response-schemas]
  (some->> response-schemas
           (filter-response-schema response-type)
           (map g/generator)
           (tcg/one-of)))

(defn- make-response [response-type response-schemas]
  (some-> (make-generator response-type response-schemas)
          (tcg/generate)))

(defn generate-response-interceptor [response-type]
  {:name ::generate-response
   :leave (fn [{:keys [handler] :as ctx}]
            (assoc ctx :response (make-response response-type (:response-schemas handler))))})

(def generate-response (generate-response-interceptor :random))

(def generate-error-response (generate-response-interceptor :error))

(def generate-success-response (generate-response-interceptor :success))

(defn constant-response [response]
  {:name ::constant-response
   :leave (fn [ctx] (assoc ctx :response response))})

(defn response-generator [{:keys [handlers]} route-name]
  (let [{:keys [response-schemas]} (martian/find-handler handlers route-name)]
    (make-generator :random response-schemas)))

(defn constantly-respond [martian response]
  (update martian :interceptors conj (constant-response response)))

(defn respond-with [martian response-type]
  (update martian :interceptors conj (generate-response-interceptor response-type)))
