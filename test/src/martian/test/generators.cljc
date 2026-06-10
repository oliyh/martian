(ns martian.test.generators
  "Generation of values matching schemas, dispatched on the schema backend.

   The backends are extended here, rather than implementing a method of the
   core SchemaBackend protocol, so that the generator dependencies (test.check,
   schema-generators, malli.generator) stay within martian-test."
  (:require [clojure.test.check.generators :as tcg]
            [malli.generator :as mg]
            [martian.backends.malli :as malli]
            [martian.backends.plumatic :as plumatic]
            [schema-generators.generators :as g]))

(defprotocol SchemaGenerator
  (generator [backend schema]
    "Returns a test.check generator producing values that match schema."))

(extend-protocol SchemaGenerator
  #?(:clj  martian.backends.plumatic.PlumaticBackend
     :cljs plumatic/PlumaticBackend)
  (generator [_ schema]
    (g/generator schema))

  #?(:clj  martian.backends.malli.MalliBackend
     :cljs malli/MalliBackend)
  (generator [_ schema]
    (mg/generator schema)))

(defn generate
  "Generates a single value matching schema using the given backend."
  [backend schema]
  (tcg/generate (generator backend schema)))

(defn response-generator
  "Returns a test.check generator of {:status ... :body ...} responses for the
   given response schema. Responses without a body schema get a nil body."
  [backend {:keys [status body]}]
  (tcg/fmap (fn [[status body]]
              {:status status :body body})
            (tcg/tuple (generator backend status)
                       (if (some? body)
                         (generator backend body)
                         (tcg/return nil)))))
