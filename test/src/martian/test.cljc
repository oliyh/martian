(ns martian.test
  (:require [martian.core :as martian]
            [schema-generators.generators :as g]
            [martian.schema :as schema]))

(def generate-response
  {:name ::generate-response
   :enter (fn [{:keys [handler] :as ctx}]
            (def h handler)
            (assoc ctx :response (g/generate (first (:response-schemas handler)))))})
