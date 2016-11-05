(ns martian.interceptors
  (:require [martian.schema :as schema]
            [clojure.walk :refer [stringify-keys]]
            [clojure.string :as string]
            [tripod.context :as tc]))

(def request-only-handler
  {:name ::request-only-handler
   :leave (fn [ctx]
            (-> ctx tc/terminate (dissoc ::tc/stack)))})

(defn- create-only [m k v]
  (if (get m k)
    m
    (assoc m k v)))

(defn- insert-or-merge [m k v]
  (cond
    (get m k) (update m k #(merge v %))
    (not-empty v) (assoc m k v)
    :else m))

(def set-method
  {:name ::method
   :enter (fn [{:keys [handler] :as ctx}]
            (update ctx :request create-only :method (:method handler)))})

(def set-url
  {:name ::url
   :enter (fn [{:keys [params url-for handler] :as ctx}]
            (update ctx :request create-only :url (url-for (:route-name handler) params)))})

(def set-query-params
  {:name ::query-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (update ctx :request insert-or-merge :query-params (schema/coerce-data (:query-schema handler) params)))})

(def set-body-params
  {:name ::body-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (update ctx :request insert-or-merge :body (schema/coerce-data (:body-schema handler) params)))})

(def set-form-params
  {:name ::form-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (update ctx :request insert-or-merge :form-params (schema/coerce-data (:form-schema handler) params)))})

(def set-header-params
  {:name ::header-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (update ctx :request insert-or-merge :headers (stringify-keys (schema/coerce-data (:headers-schema handler) params))))})
