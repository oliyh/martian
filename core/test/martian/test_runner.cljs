(ns martian.test-runner
  (:require [martian.core-test]
            [martian.interceptors-test]
            [martian.openapi-test]
            [martian.parameter-aliases-test]
            [martian.schema-test]
            [martian.swagger-test]
            [figwheel.main.testing :refer [run-tests-async]]))

(defn -main [& args]
  (run-tests-async 5000))
