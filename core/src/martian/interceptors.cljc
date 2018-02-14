(ns martian.interceptors
  (:require [martian.schema :as schema]
            [clojure.walk :refer [stringify-keys]]
            [clojure.string :as string]
            [tripod.context :as tc]
            [schema.core :as s]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            #?(:clj [martian.encoding :as encoding])))

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

(defn coerce-data [{:keys [parameter-aliases] :as handler} schema-key params]
  (schema/coerce-data (get handler schema-key) params parameter-aliases))

(def set-query-params
  {:name ::query-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (update ctx :request insert-or-merge :query-params (coerce-data handler :query-schema params)))})

(def set-body-params
  {:name ::body-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (if-let [[body-key body-schema] (first (:body-schema handler))]
              (let [parameter-aliases (:parameter-aliases handler)
                    body-params (or (:martian.core/body params)
                                    (get params (s/explicit-schema-key body-key))
                                    (get params (->kebab-case-keyword (s/explicit-schema-key body-key)))
                                    params)]
                (update ctx :request insert-or-merge :body (schema/coerce-data body-schema body-params parameter-aliases)))
              ctx))})

(def set-form-params
  {:name ::form-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (update ctx :request insert-or-merge :form-params (coerce-data handler :form-schema params)))})

(def set-header-params
  {:name ::header-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (update ctx :request insert-or-merge :headers (stringify-keys (coerce-data handler :headers-schema params))))})

(def enqueue-route-specific-interceptors
  {:name ::enqueue-route-specific-interceptors
   :enter (fn [{:keys [handler] :as ctx}]
            (if-let [i (:interceptors handler)]
              (update ctx ::tc/queue #(into (into tc/queue i) %))
              ctx))})

#?(:clj
   (defn encode-body
     ([] (encode-body (encoding/default-encoders)))
     ([encoders]
      (let [encoders (encoding/compile-encoder-matches encoders)]
        {:name ::encode-body
         :enter (fn [{:keys [request handler] :as ctx}]
                  (let [content-type (and (:body request)
                                          (not (get-in request [:headers "Content-Type"]))
                                          (encoding/choose-content-type encoders (:consumes handler)))
                        {:keys [encode] :as encoder} (encoding/find-encoder encoders content-type)]
                    (cond-> ctx
                      (get-in ctx [:request :body]) (update-in [:request :body] encode)
                      content-type (assoc-in [:request :headers "Content-Type"] content-type))))}))))

#?(:clj
   (def default-encode-body (encode-body)))

#?(:clj
   (defn coerce-response
     ([] (coerce-response (encoding/default-encoders)))
     ([encoders]
      (let [encoders (encoding/compile-encoder-matches encoders)]
        {:name ::coerce-response
         :enter (fn [{:keys [request handler] :as ctx}]
                  (let [content-type (and (not (get-in request [:headers "Accept"]))
                                          (encoding/choose-content-type encoders (:produces handler)))
                        {:keys [as] :or {as :text}} (encoding/find-encoder encoders content-type)]

                    (-> ctx
                        (assoc-in [:request :headers "Accept"] content-type)
                        (assoc-in [:request :as] as))))

         :leave (fn [{:keys [request response handler] :as ctx}]
                  (assoc ctx :response
                         (let [content-type (and (:body response)
                                                 (not-empty (get-in response [:headers :content-type])))
                               {:keys [matcher decode] :as encoder} (encoding/find-encoder encoders content-type)]
                           (update response :body decode))))}))))

#?(:clj
   (def default-coerce-response (coerce-response)))
