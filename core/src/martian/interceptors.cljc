(ns martian.interceptors
  (:require [martian.schema :as schema]
            [clojure.walk :refer [stringify-keys]]
            [clojure.string :as string]
            [tripod.context :as tc]
            [schema.core :as s]
            [clojure.set :refer [rename-keys]]))

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

(def rename-parameters
  {:name ::rename-parameters
   :enter (fn [{:keys [handler] :as ctx}]
            (update ctx :params rename-keys (:parameter-aliases handler)))})

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
            (if-let [[body-key body-schema] (first (:body-schema handler))]
              (let [body-params (or (:martian.core/body params)
                                    (get params (s/explicit-schema-key body-key))
                                    params)]
                (update ctx :request insert-or-merge :body (schema/coerce-data body-schema body-params)))
              ctx))})

(def set-form-params
  {:name ::form-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (update ctx :request insert-or-merge :form-params (schema/coerce-data (:form-schema handler) params)))})

(def set-header-params
  {:name ::header-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (update ctx :request insert-or-merge :headers (stringify-keys (schema/coerce-data (:headers-schema handler) params))))})

(def enqueue-route-specific-interceptors
  {:name ::enqueue-route-specific-interceptors
   :enter (fn [{:keys [handler] :as ctx}]
            (if-let [i (:interceptors handler)]
              (update ctx ::tc/queue #(into (into tc/queue i) %))
              ctx))})
