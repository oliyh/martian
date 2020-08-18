(ns martian.interceptors
  (:require [martian.schema :as schema]
            [clojure.walk :refer [keywordize-keys stringify-keys]]
            [tripod.context :as tc]
            [schema.core :as s]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [martian.encoding :as encoding]
            [martian.encoders :as encoders]))

(defn remove-stack [ctx]
  (-> ctx tc/terminate (dissoc ::tc/stack)))

(def request-only-handler
  {:name ::request-only-handler
   :leave remove-stack})

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

(def keywordize-params
  {:name ::keywordize-params
   :enter (fn [ctx] (update ctx :params keywordize-keys))})

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

(defn encode-body [encoders]
  {:name ::encode-body
   :encodes (keys encoders)
   :enter (fn [{:keys [request handler] :as ctx}]
            (let [content-type (and (:body request)
                                    (not (get-in request [:headers "Content-Type"]))
                                    (encoding/choose-content-type encoders (:consumes handler)))
                  {:keys [encode]} (encoding/find-encoder encoders content-type)]
              (cond-> ctx
                (get-in ctx [:request :body]) (update-in [:request :body] encode)
                content-type (assoc-in [:request :headers "Content-Type"] content-type))))})

(def default-encode-body (encode-body (encoders/default-encoders)))

(defn coerce-response [encoders]
  {:name ::coerce-response
   :decodes (keys encoders)
   :enter (fn [{:keys [request handler] :as ctx}]
            (let [content-type (and (not (get-in request [:headers "Accept"]))
                                    (encoding/choose-content-type encoders (:produces handler)))
                  {:keys [as] :or {as :text}} (encoding/find-encoder encoders content-type)]

              (cond-> (assoc-in ctx [:request :as] as)
                content-type (assoc-in [:request :headers "Accept"] content-type))))

   :leave (fn [{:keys [response] :as ctx}]
            (assoc ctx :response
                   (let [content-type (and (:body response)
                                           (not-empty (get-in response [:headers :content-type])))
                         {:keys [decode]} (encoding/find-encoder encoders content-type)]
                     (update response :body decode))))})

(def default-coerce-response (coerce-response (encoders/default-encoders)))

(defn supported-content-types
  "Return the full set of supported content-types as declared by any encoding/decoding interceptors"
  [interceptors]
  (reduce (fn [acc interceptor]
            (merge-with into acc (select-keys interceptor [:encodes :decodes])))
          {:encodes #{}
           :decodes #{}}
          interceptors))
