(ns martian.file
  (:require [clojure.java.io :as io]
            [martian.yaml :as yaml]
            [clojure.string :as str]
            [cheshire.core :as json]))

(defn local-resource [url]
  (when-let [r (some-> (or (io/resource url)
                           (let [f (io/file url)]
                             (when (.exists f)
                               f)))
                       slurp)]
    (cond
      (yaml/yaml-url? url) (yaml/yaml->edn r)
      (str/ends-with? url ".edn") (read-string r)
      (str/ends-with? url ".json") (json/decode r keyword))))
