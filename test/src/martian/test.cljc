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

(defn constant-responses [response-map]
  {:name ::constant-responses
   :leave (fn [{:keys [handler] :as ctx}]
            (let [responder (get response-map (:route-name handler))
                  response (if (fn? responder) (responder (:request ctx)) responder)]
              (assoc ctx :response response)))})

(defn contextual-responses [response-fn]
  {:name ::contextual-responses
   :leave (fn [ctx]
            (assoc ctx :response (response-fn ctx)))})

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

#?(:clj
   (def hato-responder
     {:name ::hato-responder
      :leave identity}))

#?(:clj
   (def bb-http-client-responder
     {:name ::bb-http-client-responder
      :leave identity}))

#?(:cljs
   (def cljs-http-responder
     {:name ::cljs-http-responder
      :leave (fn [ctx]
               (-> ctx
                   interceptors/remove-stack
                   (assoc :response (a/to-chan! [(:response ctx)]))))}))

#?(:cljs
   (def cljs-http-promise-responder
     {:name ::cljs-http-promise-responder
      :leave (fn [ctx]
               (-> ctx
                   interceptors/remove-stack
                   (assoc :response (.resolve js/Promise (:response ctx)))))}))

(def ^:private http-interceptors
  #?(:clj  {"martian.httpkit" httpkit-responder
            "martian.clj-http" clj-http-responder
            "martian.hato" hato-responder
            "martian.babashka.http-client" bb-http-client-responder}
     :cljs {"martian.cljs-http" cljs-http-responder
            "martian.cljs-http-promise" cljs-http-promise-responder}))

(defn http-client->interceptor [http-client]
  (let [full-name (str "martian." (name http-client))]
    (or (get http-interceptors full-name)
        (throw (ex-info "Unsupported HTTP client" {:full-name full-name
                                                   :supported (keys http-interceptors)})))))

(defn- replace-http-interceptors [martian]
  (update martian :interceptors
          (fn [interceptors]
            (->> interceptors
                 (map #(if-let [responder (and (= "perform-request" (name (:name %)))
                                               (get http-interceptors (namespace (:name %))))]
                         responder
                         %))
                 (remove (comp (set (keys http-interceptors)) namespace :name))
                 (remove (comp #{::interceptors/encode-response ::interceptors/coerce-response} :name))))))

(defn respond-with-constant
  "Adds an interceptor that simulates the server responding with responses retrieved from a given `response-map`.

   The `response-map` maps a `:route-name` to a response (plain value) or a unary function that, given the request,
   returns a response.

   Removes all interceptors that would perform real HTTP operations."
  [martian response-map]
  (-> martian
      (replace-http-interceptors)
      (update :interceptors concat [(constant-responses response-map)])))

(defn respond-with-contextual
  "Adds an interceptor that simulates the server responding with responses retrieved via a given `response-fn`.

   The `response-fn` is a unary function that, given the `ctx`, returns a response.

   It provides even more flexibility than `respond-with-constant` by allowing one to leverage the `ctx` internals
   for response production, e.g. directly use `:params` to avoid JSON decoding/encoding round trips. Be careful
   though, since this may result in the production of responses becoming \"less realistic\".

   Removes all interceptors that would perform real HTTP operations."
  [martian response-fn]
  (-> martian
      (replace-http-interceptors)
      (update :interceptors concat [(contextual-responses response-fn)])))

(defn respond-with
  "Adds an interceptor that simulates the server responding with the supplied `responses`.

   See docstrings of `respond-with-constant` and `respond-with-contextual` for details.

   Removes all interceptors that would perform real HTTP operations."
  [martian responses]
  (cond
    (map? responses)
    (respond-with-constant martian responses)

    (fn? responses)
    (respond-with-contextual martian responses)

    :else
    (throw (ex-info "Unsupported type of responses" {:type (type responses)}))))

(defn respond-with-generated
  "Adds an interceptor that simulates the server responding to operations by generating responses of the supplied response-type
   from the handler response schemas.
   Removes all interceptors that would perform real HTTP operations"
  [martian response-types]
  (-> (replace-http-interceptors martian)
      (update :interceptors concat [(generate-responses response-types)])))

(defn respond-as
  "Adds an interceptor that simulates wrapping a response into the `http-client` implementation-specific returned type.

   Implementation of HTTP requests — as provided by target HTTP clients (httpkit, clj-http, cljs-http, ...) — may give
   responses wrapped in an implementation-specific type, e.g. promise or `core.async` channel. As your production code
   will expect these response types this interceptor lets you simulate those response wrappers.

   You only need to call this if you have a `martian` instance created without the standard HTTP-specific interceptors,
   i.e. those found in `martian.httpkit`, `martian.clj-http` and so on.

   Removes all interceptors that would perform real HTTP operations."
  [martian http-client]
  (-> martian
      (replace-http-interceptors)
      (update :interceptors #(concat [(http-client->interceptor http-client)] %))))
