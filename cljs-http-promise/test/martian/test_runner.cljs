(ns martian.test-runner
  (:require [martian.cljs-http-promise-test]
            [figwheel.main.testing :refer [run-tests-async]]))

(defn -main [& args]
  (run-tests-async 5000))
