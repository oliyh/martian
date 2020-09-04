(ns martian.runner
  (:require [figwheel.main :as fig]
            [martian.server-stub :refer [with-server] :as server-stub]))

(defn- run-tests []
  (with-server
    #(fig/-main "-co" "test.cljs.edn" "-m" "martian.test-runner")))

(defn -main [& args]
  (run-tests))

;; for use in the repl
(def start-server! server-stub/start-server!)

(def stop-server! server-stub/stop-server!)
