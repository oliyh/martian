(ns martian.core
  (:require [clojure.core.protocols]
            [clojure.spec.alpha :as spec]
            [clojure.string :as string]
            [clojure.walk :refer [keywordize-keys]]
            [lambdaisland.uri :refer [map->query-string]]
            [martian.interceptors :as interceptors]
            [martian.openapi :refer [openapi->handlers openapi-schema?]]
            [martian.parameter-aliases :refer [parameter-aliases alias-schema]]
            [martian.schema :as schema]
            [martian.spec :as mspec]
            [martian.swagger :refer [swagger->handlers]]
            [tripod.context :as tc]))

(defrecord Martian [api-root handlers interceptors opts])

(def default-interceptors
  "The vector with default interceptors used to build a Martian instance."
  [interceptors/keywordize-params
   interceptors/set-method
   interceptors/set-url
   interceptors/set-query-params
   interceptors/set-body-params
   interceptors/set-form-params
   interceptors/set-header-params
   interceptors/enqueue-route-specific-interceptors])

(def default-coercion-matcher
  "The default coercion matcher used by Martian for parameters coercion."
  schema/default-coercion-matcher)

(defn- resolve-instance [m]
  (cond
    (map? m) m
    (var? m) (deref m)
    (fn? m) (m)
    :else m))

(defn- validate-handler! [{:keys [exception route-name] :as handler}]
  (when (some? exception)
    (throw (ex-info (str "Invalid handler for route " route-name)
                    {:handler handler}
                    exception)))
  handler)

(defn- matching-handler? [route-name handler]
  (= (keyword route-name) (:route-name handler)))

(defn- find-handler [handlers route-name]
  (or (some-> (some #(when (matching-handler? route-name %) %) handlers)
              ;; Validate the found handler before performing any actions.
              (validate-handler!))
      (throw (ex-info (str "Could not find route " route-name)
                      {:route-name route-name
                       :handlers handlers}))))

(defn handler-for
  "Given a Martian instance, returns a handler for the given `route-name`."
  [martian route-name]
  (find-handler (:handlers (resolve-instance martian)) route-name))

(defn update-handler
  "Given a Martian instance, updates a handler with the provided `route-name`
   using the provided `update-fn` and `update-args`, if any.

   For example, route-specific interceptors can be added like that:
   ```
   (update-handler m :load-pet assoc :interceptors [an-interceptor])
   ```"
  [martian route-name update-fn & update-args]
  (update (resolve-instance martian)
          :handlers #(mapv (fn [handler]
                             (if (matching-handler? route-name handler)
                               (apply update-fn handler update-args)
                               handler))
                           %)))

(defn url-for
  "Given a Martian instance, builds a request URL for the given `route-name`
   using the provided `params` and options, if any.

   Supported options:
   - `:include-query?` — if true, will include params in the URL query string;
                         false by default."
  ([martian route-name] (url-for martian route-name {}))
  ([martian route-name params] (url-for martian route-name params {}))
  ([martian route-name params {:keys [include-query?] :as _options}]
   (let [{:keys [api-root handlers opts]} (resolve-instance martian)]
     (when-let [handler (find-handler handlers route-name)]
       (let [path-params (interceptors/coerce-data handler :path-schema (keywordize-keys params) opts)
             query-params (when include-query?
                            (interceptors/coerce-data handler :query-schema (keywordize-keys params) opts))]
         (str api-root (string/join (map #(get path-params % %) (:path-parts handler)))
              (if query-params
                (str "?" (map->query-string query-params))
                "")))))))

(defn request-for
  "Given a Martian instance, builds an HTTP request for the given `route-name`
   using the provided `params`, if any."
  ([martian route-name] (request-for martian route-name {}))
  ([martian route-name params]
   (let [{:keys [handlers interceptors opts] :as martian} (resolve-instance martian)]
     (when-let [handler (find-handler handlers route-name)]
       (let [int-vec (if-not (vector? interceptors) (vec interceptors) interceptors)
             ctx (tc/enqueue* {} (conj int-vec interceptors/request-only-handler))]
         (:request (tc/execute
                    (assoc ctx
                           :url-for (partial url-for martian)
                           :request (or (::request params) {})
                           :handler handler
                           :params params
                           :opts opts))))))))

(defn response-for
  "Given a Martian instance, makes an HTTP request for the given `route-name`
   using the provided `params`, if any, and returns the HTTP response."
  ([martian route-name] (response-for martian route-name {}))
  ([martian route-name params]
   (let [{:keys [handlers interceptors opts] :as martian} (resolve-instance martian)]
     (when-let [handler (find-handler handlers route-name)]
       (let [ctx (tc/enqueue* {} interceptors)]
         (:response (tc/execute
                     (assoc ctx
                            :url-for (partial url-for martian)
                            :request (or (::request params) {})
                            :handler handler
                            :params params
                            :opts opts))))))))

(def ^:private parameter-schemas
  [:path-schema :query-schema :body-schema :form-schema :headers-schema])

(defn- collect-parameter-aliases [handler]
  (reduce (fn [aliases param-key]
            (assoc aliases param-key (parameter-aliases (get handler param-key))))
          {}
          parameter-schemas))

(defn- collect-parameters [{:keys [parameter-aliases] :as handler}]
  (reduce (fn [params param-key]
            (merge params (alias-schema (get parameter-aliases param-key) (get handler param-key))))
          {}
          parameter-schemas))

(declare explore)

(defn- navize-routes
  [martian routes]
  (with-meta
    routes
    {`clojure.core.protocols/nav (fn [_coll _k [route-name _summary]]
                                   (explore martian route-name))}))

(defn explore
  "Explores the given Martian instance or a particular handler with the given
   `route-name`, if any.

   Returns a map containing details such as:
   - `:route-name` and `:summary` — for the Martian instance, or
   - `:summary`, `:parameters`, and `:returns` — for the handler."
  ([martian]
   (let [routes (mapv (juxt :route-name :summary)
                      (:handlers (resolve-instance martian)))]
     (navize-routes martian routes)))
  ([martian route-name]
   (when-let [{:keys [summary deprecated?] :as handler} (handler-for martian route-name)]
     (-> {:summary summary
          :parameters (collect-parameters handler)
          :returns (->> (:response-schemas handler)
                        (map (juxt (comp :v :status) :body))
                        (into {}))}
         (cond-> deprecated? (assoc :deprecated? true))))))

(defn- validate-all-handlers! [handlers]
  (when-let [invalid-handlers (not-empty (filter :exception handlers))]
    (throw (ex-info "Invalid handlers" {:handlers invalid-handlers})))
  handlers)

(defn- enrich-handler [handler]
  (try
    (assoc handler :parameter-aliases (collect-parameter-aliases handler))
    (catch #?(:clj Exception :cljs js/Error) ex
      (assoc handler :exception ex))))

(defn- build-instance
  [api-root handlers {:keys [interceptors validate-handlers?] :as opts}]
  (let [enriched-handlers (cond-> (mapv enrich-handler handlers)
                                  validate-handlers? (validate-all-handlers!))]
    (->Martian api-root
               enriched-handlers
               (vec (or interceptors default-interceptors))
               (dissoc opts :interceptors))))

(spec/fdef build-instance
  :args (spec/cat :api-root ::mspec/api-root
                  :handlers (spec/coll-of ::mspec/handler)
                  :opts ::mspec/opts))

(defn bootstrap-openapi
  "Creates a Martian instance from an OpenAPI/Swagger spec based on the `json`
   schema provided.

   Supported options:
   - `:interceptors`       — an ordered coll of Tripod interceptors to be used
                             as a global interceptor chain by Martian instance;
                             defaults to the `default-interceptors`;
   - `:validate-handlers?` — if true, will enable early validation of handlers,
                             failing fast in case of errors; false by default;
   - `:coercion-matcher`   — a unary fn of schema used for parameters coercion;
                             defaults to the `default-coercion-matcher`;
   - `:use-defaults?`      — if true, will read 'default' directives from the
                             OpenAPI/Swagger spec; false by default;
   - `:route-name-sources` — a vector of route name sources; supported sources:
                             - `:operationId` — use an \"operationId\" property
                             - `:method+path` — use method + URL (path) pattern
                             - any fn of 'url-pattern', 'method', 'definition';
                             defaults to `[:operationId]`."
  [api-root json & [opts]]
  (let [{:keys [interceptors route-name-sources] :as opts} (keywordize-keys opts)
        handlers (if (openapi-schema? json)
                   (let [content-types (-> interceptors
                                           (or default-interceptors)
                                           (interceptors/supported-content-types))]
                     (openapi->handlers json content-types route-name-sources))
                   (swagger->handlers json route-name-sources))]
    (build-instance api-root handlers opts)))

(def
  ^{:doc "Creates a Martian instance from an OpenAPI/Swagger spec based on the `json`
   schema provided.

   Supported options:
   - `:interceptors`       — an ordered coll of Tripod interceptors to be used
                             as a global interceptor chain by Martian instance;
                             defaults to the `default-interceptors`;
   - `:validate-handlers?` — if true, will enable early validation of handlers,
                             failing fast in case of errors; false by default;
   - `:coercion-matcher`   — a unary fn of schema used for parameters coercion;
                             defaults to the `default-coercion-matcher`;
   - `:use-defaults?`      — if true, will read 'default' directives from the
                             OpenAPI/Swagger spec; false by default;
   - `:route-name-sources` — a vector of route name sources; supported sources:
                             - `:operationId` — use an \"operationId\" property
                             - `:method+path` — use method + URL (path) pattern
                             - any fn of 'url-pattern', 'method', 'definition';
                             defaults to `[:operationId]`."
    :arglists '([api-root json & [opts]])}
  bootstrap-swagger
  bootstrap-openapi)

(defn bootstrap
  "Creates a Martian instance from the given `concise-handlers` description.

   Supported options:
   - `:interceptors`       — an ordered coll of Tripod interceptors to be used
                             as a global interceptor chain by Martian instance;
                             defaults to the `default-interceptors`;
   - `:validate-handlers?` — if true, will enable early validation of handlers,
                             failing fast in case of errors; false by default;
   - `:coercion-matcher`   — a unary fn of schema used for parameters coercion;
                             defaults to the `default-coercion-matcher`;
   - `:produces`           — a coll of media (content) types used as a global
                             default value for the handler's `:produces` key;
   - `:consumes`           — a coll of media (content) types used as a global
                             default value for the handler's `:consumes` key."
  [api-root concise-handlers & [{:keys [produces consumes] :as opts}]]
  (let [handlers (map (fn [handler]
                        (-> handler
                            (update :produces #(or % produces))
                            (update :consumes #(or % consumes))))
                      concise-handlers)]
    (build-instance api-root handlers opts)))
