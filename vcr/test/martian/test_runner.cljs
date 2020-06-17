;; This test runner is intended to be run from the command line
(ns martian.test-runner
  (:require [martian.vcr-test]
            [figwheel.main.testing :refer [run-tests-async]]))

(defn -main [& args]
  (run-tests-async 5000))
