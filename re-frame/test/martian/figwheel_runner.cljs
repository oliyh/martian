(ns martian.figwheel-runner
  (:require
   [martian.re-frame-test]
   [figwheel.main.testing :refer [run-tests-async]]))

(defn -main [& args]
  (run-tests-async 5000))
