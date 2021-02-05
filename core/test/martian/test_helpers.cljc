(ns martian.test-helpers
  #?(:clj (:require [clojure.java.io :as io]
                    [cheshire.core :as json])))

#?(:clj
   (defmacro json-resource [resource-name]
     (json/parse-string (slurp (io/resource resource-name)))))
