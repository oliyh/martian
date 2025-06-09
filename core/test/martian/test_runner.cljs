(ns martian.test-runner
  (:require [figwheel.main.testing :refer-macros [run-tests-async]]
            [martian.core-test]
            [martian.interceptors-test]
            [martian.openapi-test]
            [martian.parameter-aliases-test]
            [martian.schema-test]
            [martian.swagger-test]))

(defn -main [& args]
  (run-tests-async 5000))
