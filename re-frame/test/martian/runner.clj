(ns martian.runner
  (:require [doo.core :as doo]
            [martian.server-stub :refer [with-server]]))

(defn- run-tests []
  (with-server
    #(doo/run-script :phantom {:output-to "target/unit-test.js"
                               :asset-path "target/unit-test"
                               :output-dir "target/unit-test"
                               :main 'martian.doo-runner
                               :optimizations :whitespace})))

(defn -main [& args]
  (let [out (run-tests)]
    (System/exit (:exit out))))
