(ns martian.test
  (:require [martian.core :as martian]
            [martian.interceptors :as interceptors]
            #?(:clj  [schema.core :as s]
               :cljs [schema.core :as s :refer [EqSchema]])
            [schema-generators.generators :as g]
            [clojure.test.check.generators :as tcg]
            #?(:clj [tripod.context :as tc])
            #?(:cljs [cljs.core.async :as a]))
  #?(:clj (:import [schema.core EqSchema])))

(defn- exact-status
  "Returns the status code that the `status` schema allows, if it's only one."
  [status]
  (when (instance? EqSchema status)
    (let [code (:v status)]
      (when (int? code) code))))

(defn- default-status?
  "Whether the `status` schema is that of a `default` response: any value for
   OpenAPI, or the symbol `default` for Swagger."
  [status]
  (or (= s/Any status)
      (and (instance? EqSchema status) (= 'default (:v status)))))

(defn- response-codes
  "Returns the status codes that each of `response-schemas` covers, in order:
   its exact code; the codes that its range, like `2XX`, or any other status
   schema allows, but for the exact codes of other responses, which win over
   a range; or, for `default`, the codes from 200 to 599 that no other response
   covers."
  [response-schemas]
  (let [statuses (map :status response-schemas)
        allowed  (map (fn [status]
                        (cond
                          (exact-status status)    [(exact-status status)]
                          (default-status? status) nil
                          :else                    (filter #(nil? (s/check status %)) (range 100 600))))
                      statuses)
        exact    (set (keep exact-status statuses))
        covered  (set (apply concat allowed))]
    (map (fn [status codes]
           (cond
             (exact-status status)    codes
             (default-status? status) (remove covered (range 200 600))
             :else                    (remove exact codes)))
         statuses
         allowed)))

(defn- response-generator*
  "Returns a generator of responses for a response schema, whose status is one
   of `codes`, and whose body is nil if it has no body schema."
  [codes {:keys [body] :as response-schema}]
  (tcg/fmap (fn [[response status body']]
              (cond-> (assoc response :status status)
                (contains? response-schema :body) (assoc :body body')))
            (tcg/tuple (g/generator (dissoc response-schema :status :body))
                       (tcg/elements codes)
                       (if (some? body) (g/generator body) (tcg/return nil)))))

(def ^:private response-type-statuses
  {:success [200 399]
   :error   [400 599]})

(defn- make-generator
  "Returns a generator of the responses of `response-schemas` whose status can
   be of `response-type`: `:success` (200-399), `:error` (400-599) or `:random`
   (any). Throws if there are response schemas but none of them can."
  [response-type response-schemas]
  (let [[from to] (get response-type-statuses response-type)
        generators (keep (fn [[response-schema codes]]
                           (when-some [codes (seq (cond->> codes
                                                    from (filter #(<= from % to))))]
                             (response-generator* codes response-schema)))
                         (map vector response-schemas (response-codes response-schemas)))]
    (cond
      (seq generators)
      (tcg/one-of generators)

      (seq response-schemas)
      (throw (ex-info (str "No response can be generated with a " (name response-type) " status")
                      {:response-type response-type
                       :statuses (map :status response-schemas)})))))

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

(defn response-generator [martian route-name]
  (let [{:keys [response-schemas]} (martian/handler-for martian route-name)]
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

(defn replace-http-interceptors [martian]
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
  "Adds an interceptor that simulates the server responding with responses generated from the handler response schemas.

   The `response-types` maps a `:route-name` to a response type which can be `:random` (default, any HTTP status code),
   `:success` (HTTP status codes 200-399), or `:error` (HTTP status codes 400-599).

   Removes all interceptors that would perform real HTTP operations."
  [martian response-types]
  (-> martian
      (replace-http-interceptors)
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
