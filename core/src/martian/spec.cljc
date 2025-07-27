(ns martian.spec
  (:require [clojure.spec.alpha :as s]
            [schema.core :as schema]))

(s/def ::api-root string?)

(s/def ::method keyword?)
(s/def ::route-name keyword?)
(s/def ::path-parts (s/coll-of (s/or :literal string? :arg keyword?)))

(s/def ::input-schema (s/nilable (s/and map? #(satisfies? schema/Schema %))))

(s/def ::path-schema ::input-schema)
(s/def ::query-schema ::input-schema)
(s/def ::body-schema ::input-schema)
(s/def ::form-schema ::input-schema)
(s/def ::headers-schema ::input-schema)

(s/def ::media-types (s/nilable (s/coll-of string?)))
(s/def ::produces ::media-types)
(s/def ::consumes ::media-types)

(s/def ::handler
  (s/keys
   :req-un [::route-name
            ::path-parts
            ::method]

   :opt-un [::path-schema
            ::query-schema
            ::body-schema
            ::form-schema
            ::headers-schema
            ::response-schemas
            ::produces
            ::consumes
            ::summary

            ;; only for swagger, information only
            ::swagger-definition
            ::path]))

(s/def ::interceptor (s/keys :opt-un [::name ::enter ::leave ::catch]))
(s/def ::interceptors (s/nilable (s/coll-of ::interceptor)))
(s/def ::coercion-matcher fn?)
(s/def ::use-defaults? boolean?)
(s/def ::validate-handlers? boolean?)

(s/def ::opts (s/nilable (s/keys :opt-un [::interceptors
                                          ::produces
                                          ::consumes
                                          ::coercion-matcher
                                          ::use-defaults?
                                          ::validate-handlers?])))
