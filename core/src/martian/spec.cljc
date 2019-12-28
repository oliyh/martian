(ns martian.spec
  (:require [clojure.spec.alpha :as s]
            [schema.core :as schema]
            [clojure.spec.alpha :as spec]))

(s/def ::api-root string?)

(s/def ::method keyword?)
(s/def ::route-name keyword?)
(s/def ::path-parts (s/coll-of (s/or :literal string? :arg keyword?)))

(s/def ::input-schema (s/nilable (s/map-of (s/or :specific-key schema/specific-key?
                                                 :any-key schema/Any)
                                           #(satisfies? schema/Schema %))))

(s/def ::path-schema ::input-schema)
(s/def ::query-schema ::input-schema)
(s/def ::body-schema ::input-schema)
(s/def ::form-schema ::input-schema)
(s/def ::headers-schema ::input-schema)

(s/def ::content-types (s/nilable (s/coll-of string?)))
(s/def ::produces ::content-types)
(s/def ::consumes ::content-types)

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

(s/def ::interceptor (spec/keys :opt-un [::name ::enter ::leave ::catch]))
(s/def ::interceptors (spec/nilable (spec/coll-of ::interceptor)))

(s/def ::opts (spec/nilable (spec/keys :opt-un [::interceptors])))
