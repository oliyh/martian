(ns martian.interceptors
  (:require [martian.schema :as schema]
            [clojure.walk :refer [stringify-keys]]
            [clojure.string :as string]
            [tripod.context :as tc]))

(def request-only-handler
  {:name ::request-only-handler
   :leave (fn [ctx]
            (-> ctx tc/terminate (dissoc ::tc/stack)))})

(def set-method
  {:name ::method
   :enter (fn [{:keys [handler] :as ctx}]
            (update ctx :request assoc :method (:method handler)))})

(def set-url
  {:name ::url
   :enter (fn [{:keys [params url-for handler] :as ctx}]
            (assoc-in ctx [:request :url] (url-for (:route-name handler) params)))})

(def set-query-params
  {:name ::query-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (let [query-schema (:query-schema handler)
                  coerced-params (schema/coerce-data query-schema params)]
              (if (not-empty coerced-params)
                (update ctx :request assoc :query-params coerced-params)
                ctx)))})

(def set-body-params
  {:name ::body-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (let [body-schema (:body-schema handler)
                  coerced-params (schema/coerce-data body-schema params)]
              (if (not-empty coerced-params)
                (update ctx :request assoc :body coerced-params)
                ctx)))})

(def set-form-params
  {:name ::form-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (let [form-schema (:form-schema handler)
                  coerced-params (schema/coerce-data form-schema params)]
              (if (not-empty coerced-params)
                (update ctx :request assoc :form-params coerced-params)
                ctx)))})

(def set-header-params
  {:name ::header-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (let [headers-schema (:headers-schema handler)
                  coerced-params (schema/coerce-data headers-schema params)]
              (if (not-empty coerced-params)
                (update ctx :request assoc :headers (stringify-keys coerced-params))
                ctx)))})
