(ns martian.interceptors
  (:require [martian.schema :as schema]
            [clojure.walk :refer [stringify-keys]]))

(def request-building-handler
  {:name ::request-building-handler
   :leave (fn [{:keys [request response] :as ctx}]
            (if (nil? response)
              (assoc ctx :response (dissoc request :params))
              ctx))})

(def set-method
  {:name ::method
   :enter (fn [{:keys [handler] :as ctx}]
            (update ctx :request assoc :method (:method handler)))})

(def set-url
  {:name ::url
   :enter (fn [{:keys [request path-for handler] :as ctx}]
            (let [path-schema (:path-schema handler)]
              (update ctx :request
                      assoc :url (path-for (:path-parts handler)
                                           (schema/coerce-data path-schema (:params request))))))})

(def set-query-params
  {:name ::query-params
   :enter (fn [{:keys [request handler] :as ctx}]
            (let [query-schema (:query-schema handler)
                  coerced-params (schema/coerce-data query-schema (:params request))]
              (if (not-empty coerced-params)
                (update ctx :request assoc :query-params coerced-params)
                ctx)))})

(def set-body-params
  {:name ::body-params
   :enter (fn [{:keys [request handler] :as ctx}]
            (let [body-schema (:body-schema handler)
                  coerced-params (schema/coerce-data body-schema (:params request))]
              (if (not-empty coerced-params)
                (update ctx :request assoc :body coerced-params)
                ctx)))})

(def set-form-params
  {:name ::form-params
   :enter (fn [{:keys [request handler] :as ctx}]
            (let [form-schema (:form-schema handler)
                  coerced-params (schema/coerce-data form-schema (:params request))]
              (if (not-empty coerced-params)
                (update ctx :request assoc :form-params coerced-params)
                ctx)))})

(def set-header-params
  {:name ::header-params
   :enter (fn [{:keys [request handler] :as ctx}]
            (let [headers-schema (:headers-schema handler)
                  coerced-params (schema/coerce-data headers-schema (:params request))]
              (if (not-empty coerced-params)
                (update ctx :request assoc :headers (stringify-keys coerced-params))
                ctx)))})
