(ns martian.test
  (:require [martian.core :as martian]
            [martian.interceptors :as interceptors]
            [schema-generators.generators :as g]
            [clojure.test.check.generators :as tcg]
            #?(:clj [tripod.context :as tc])
            #?(:cljs [cljs.core.async :as a])))

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

(defn generate-responses [response-types]
  {:name ::generate-responses
   :leave (fn [{:keys [handler] :as ctx}]
            (let [response-type (get response-types (:route-name handler) :random)]
              (assoc ctx :response (make-response response-type (:response-schemas handler)))))})

(defn always-generate-response [response-type]
  {:name ::always-generate-response
   :leave (fn [{:keys [handler] :as ctx}]
            (assoc ctx :response (make-response response-type (:response-schemas handler))))})

(def generate-response (always-generate-response :random))

(def generate-error-response (always-generate-response :error))

(def generate-success-response (always-generate-response :success))

(defn constant-responses [responses]
  {:name ::constant-responses
   :leave (fn [{:keys [handler] :as ctx}]
            (assoc ctx :response (get responses (:route-name handler))))})

(defn response-generator [{:keys [handlers]} route-name]
  (let [{:keys [response-schemas]} (martian/find-handler handlers route-name)]
    (make-generator :random response-schemas)))


#?(:clj
   (def httpkit-responder
     {:name ::httpkit-responder
      :leave (fn [ctx]
               (-> ctx
                   interceptors/remove-stack
                   (assoc :response (future (:response (tc/execute ctx))))))}))

#?(:clj
   (def clj-http-responder
     {:name ::clj-http-responder
      :leave identity}))

#?(:cljs
   (def cljs-http-responder
     {:name ::cljs-http-responder
      :leave (fn [ctx]
               (let [c (a/chan)]
                 (a/put! c (:response ctx))
                 (assoc ctx :response c)))}))

(def ^:private http-interceptors
  #?(:clj {"martian.httpkit" httpkit-responder
           "martian.clj-http" clj-http-responder}
     :cljs {"martian.cljs-http" cljs-http-responder}))

(defn- replace-http-interceptors [martian]
  (update martian :interceptors
          (fn [interceptors]
            (->> interceptors
                 (map #(if-let [responder (and (= "perform-request" (name (:name %)))
                                               (get http-interceptors (namespace (:name %))))]
                         responder
                         %))
                 (remove (comp (set (keys http-interceptors)) namespace :name))
                 (remove (comp #{::interceptors/encode-body ::interceptors/coerce-response} :name))))))

(defn respond-with-constant
  "Adds an interceptor that simulates the server constantly responding with the supplied response.
   Removes all interceptors that would perform real HTTP operations."
  [martian responses]
  (-> (replace-http-interceptors martian)
      (update :interceptors concat [(constant-responses responses)])))

(defn respond-with-generated
  "Adds an interceptor that simulates the server responding to operations by generating responses of the supplied response-type
   from the handler response schemas.
   Removes all interceptors that would perform real HTTP operations"
  [martian response-types]
  (-> (replace-http-interceptors martian)
      (update :interceptors concat [(generate-responses response-types)])))

(defn respond-as
  "You only need to call this if you have a martian which was created without martian's standard http-specific interceptors,
   i.e. those found in martian.httpkit and so on.

  Implementations of http requests - as provided by martian httpkit, clj-http and cljs-http - give
  implementation-specific response types; promises, data and core.async channels respectively.
  As your production code will expect these response types this interceptor lets you simulate those response wrappers.
  Removes all interceptors that would perform real HTTP operations"
  [martian implementation-name]
  (-> (replace-http-interceptors martian)
      (update :interceptors #(concat [(get http-interceptors (str "martian." (name implementation-name)))] %))))
