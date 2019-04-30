(defproject swirrl/martian-test "0.1.10-SNAPSHOT"
  :description "Testing tools for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]
            [lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.8"]]
  :dependencies [[swirrl/martian :version]
                 [prismatic/schema-generators "0.1.2"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/core.async "0.4.474"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.9.0"]
                                       [org.clojure/clojurescript "1.9.946"]]}
             :dev {:exclusions [[org.clojure/tools.reader]]
                   :resource-paths ["test-resources"]
                   :dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/clojurescript "1.9.946"]
                                  [prismatic/schema "1.1.9"]
                                  [org.clojure/tools.reader "1.2.2"]
                                  [com.cemerick/piggieback "0.2.2"]
                                  [swirrl/martian-httpkit :version]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}
  :aliases {"test" ["do" ["clean"] ["test"] ["doo" "nashorn" "test" "once"]]}
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-to "target/unit-test.js"
                                   :main 'martian.runner
                                   :optimizations :simple}}]})
