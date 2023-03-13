(ns martian.test-helpers
  #?(:clj (:require [clojure.java.io :as io]
                    [clj-yaml.core :as yaml]
                    [cheshire.core :as json])))

#?(:clj
   (defmacro json-resource [resource-name]
     (list 'quote (json/parse-string (slurp (io/resource resource-name))))))

#?(:clj
   (defmacro yaml-resource [resource-name]
     (list 'quote (yaml/parse-string (slurp (io/resource resource-name))))))
