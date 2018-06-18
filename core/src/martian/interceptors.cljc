(ns martian.interceptors
  (:require [martian.schema :as m-schema]
            [martian.spec :as m-spec]
            [clojure.walk :refer [stringify-keys]]
            [clojure.string :as string]
            [tripod.context :as tc]
            [schema.core :as schema]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [martian.encoding :as encoding]
            [martian.encoders :as encoders]
            #?(:clj [clojure.spec.alpha :as spec]
               :cljs [cljs.spec.alpha :as spec])))

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

(defn- coerce-data-schema [{:keys [parameter-aliases] :as handler} schema params]
  (m-schema/coerce-data schema params parameter-aliases))

(defn- coerce-data-spec [{:keys [parameter-aliases] :as handler} spec params]
  (m-spec/conform-data spec params parameter-aliases))

(defn coerce-data [handler schema-key params]
  (let [spec-or-schema (get handler schema-key)]
    (if (spec/get-spec spec-or-schema)
      (coerce-data-spec handler spec-or-schema params)
      (coerce-data-schema handler spec-or-schema params))))

(def set-query-params
  {:name ::query-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (update ctx :request insert-or-merge :query-params (coerce-data handler :query-schema params)))})

(defn body-keys [body-spec-or-schema]
  (let [aliases (juxt identity ->kebab-case-keyword)]
    (if (spec/get-spec body-spec-or-schema)
      (set (mapcat aliases [body-spec-or-schema (keyword (name body-spec-or-schema))]))
      (-> body-spec-or-schema ffirst schema/explicit-schema-key aliases set))))


(def set-body-params
  {:name ::body-params
   :enter (fn [{:keys [params handler] :as ctx}]
            (if-let [body-spec-or-schema (:body-schema handler)]
              (let [body-params (or (some params (cons :martian.core/body (body-keys body-spec-or-schema)))
                                    params)
                    parameter-aliases (:parameter-aliases handler)]
                (update ctx :request insert-or-merge :body
                        (if (spec/get-spec body-spec-or-schema)
                          (coerce-data-spec handler body-spec-or-schema body-params)
                          (coerce-data-schema handler (val (first body-spec-or-schema)) body-params))))
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

(defn encode-body [encoders]
  {:name ::encode-body
   :enter (fn [{:keys [request handler] :as ctx}]
            (let [content-type (and (:body request)
                                    (not (get-in request [:headers "Content-Type"]))
                                    (encoding/choose-content-type encoders (:consumes handler)))
                  {:keys [encode] :as encoder} (encoding/find-encoder encoders content-type)]
              (cond-> ctx
                (get-in ctx [:request :body]) (update-in [:request :body] encode)
                content-type (assoc-in [:request :headers "Content-Type"] content-type))))})

(def default-encode-body (encode-body (encoders/default-encoders)))

(defn coerce-response [encoders]
  {:name ::coerce-response
   :enter (fn [{:keys [request handler] :as ctx}]
            (let [content-type (and (not (get-in request [:headers "Accept"]))
                                    (encoding/choose-content-type encoders (:produces handler)))
                  {:keys [as] :or {as :text}} (encoding/find-encoder encoders content-type)]

              (cond-> (assoc-in ctx [:request :as] as)
                content-type (assoc-in [:request :headers "Accept"] content-type))))

   :leave (fn [{:keys [request response handler] :as ctx}]
            (assoc ctx :response
                   (let [content-type (and (:body response)
                                           (not-empty (get-in response [:headers :content-type])))
                         {:keys [matcher decode] :as encoder} (encoding/find-encoder encoders content-type)]
                     (update response :body decode))))})

(def default-coerce-response (coerce-response (encoders/default-encoders)))
