(ns martian.runner
  (:require [figwheel.main :as fig]
            [martian.server-stub :refer [with-server]]))

(defn- run-tests []
  (with-server
    #(fig/-main "-co" "test.cljs.edn" "-m" "martian.test-runner")))

(defn -main [& args]
  (run-tests))
