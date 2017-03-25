(ns martian.test
  (:require [martian.core :as martian]
            [schema-generators.generators :as g]
            [clojure.test.check.generators :as tcg]
            [schema.core :as s]
            [clojure.core.async :as a]))

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
  (update martian :interceptors concat [(constant-response response)]))

(defn respond-with [martian response-type]
  (update martian :interceptors concat [(generate-response-interceptor response-type)]))

#?(:clj
   (def httpkit-responder
     {:name :httpkit-responder
      :leave (fn [ctx]
               (let [p (promise)]
                 (deliver p (:response ctx))
                 (assoc ctx :response p)))}))

#?(:clj
   (def clj-http-responder
     {:name :clj-http-responder
      :leave identity}))

#?(:cljs
   (def cljs-http-responder
     {:name :cljs-http-responder
      :leave (fn [ctx]
               (let [c (a/chan)]
                 (a/put! c (:response ctx))
                 (assoc ctx :response c)))}))

(def responders
  #?(:clj
     {:httpkit httpkit-responder
      :clj-http clj-http-responder}
     :cljs
     {:cljs-http cljs-http-responder}))

(defn respond-as [martian implementation-name]
  "Implementations of http requests - as provided by martian httpkit, clj-http and cljs-http - give
  implementation-specific response types; promises, data and core.async channels respectively.
  As your production code will expect these response types this interceptor lets you simulate those response wrappers."
  (update martian :interceptors #(concat [(get responders implementation-name)] %)))
