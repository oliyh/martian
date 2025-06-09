(ns martian.test-runner
  (:require [figwheel.main.testing :refer-macros [run-tests-async]]
            [martian.re-frame-test]))

(defn -main [& args]
  (run-tests-async 5000))
