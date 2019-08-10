(ns martian.runner
  (:require [figwheel.main :as fig]
            [figwheel.main.testing :as result]
            [martian.server-stub :refer [with-server]]))

(defn- run-tests []
  (with-server
    #(fig/-main "-co" "test.cljs.edn" "-m" "martian.figwheel-runner")))

(defn -main [& args]
  (let [result (run-tests)]
    (System/exit (if (= ::result/success result) 0 1))))
