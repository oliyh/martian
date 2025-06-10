;; This test runner is intended to be run from the command line
(ns martian.test-runner
  (:require [figwheel.main.testing :refer-macros [run-tests-async]]
            [martian.vcr-test]))

(defn -main [& args]
  (run-tests-async 5000))
