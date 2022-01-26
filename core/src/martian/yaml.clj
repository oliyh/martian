(ns martian.yaml
  "Handle OpenAPI specs in YAML."
  (:require [clojure.walk :as w]
            [clojure.string :as string]
            [clj-yaml.core :as yaml]))

;;; NOTE: Currently only for CLJ

(defn yaml-url?
  "Predicate that returns true if the `url` has a YAML extension.
  False otherwise."
  [url]
  (boolean (or (string/ends-with? url ".yaml") (string/ends-with? url ".yml"))))

(defn cleanup
  "Clean up the EDN returned by clj-commons/clj-yaml
    to be compatible with what martian expects."
  [edn]
  (w/postwalk (fn [x]
                (cond
                  ;; replace all LazySeqs with Vectors
                  ;; See https://github.com/clj-commons/clj-yaml/pull/18
                  (and (seq? x) (not (vector? x)))
                  (into [] x)
                  ;; make sure all the keys of maps are keywords, including keys that were numbers
                  ;; See https://github.com/clj-commons/clj-yaml/blob/master/src/clojure/clj_yaml/core.clj#L128-L129
                  (map? x)
                  (into {} (map (fn [[k v]] [(or (keyword k) (if (number? k) (keyword (str k)) k)) v]) x))
                  ;; otherwise do nothing
                  :else
                  x))
              edn))

(defn yaml->edn
  "Convert a YAML OpenAPI Spec string `s` to EDN compatible with martian."
  [s]
  (-> s
      (yaml/parse-string)
      (cleanup)))
