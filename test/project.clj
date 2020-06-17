(defproject martian-test "0.1.13-SNAPSHOT"
  :description "Testing tools for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]
            [lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.8"]]
  :dependencies [[martian :version]
                 [prismatic/schema-generators "0.1.2"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/core.async "0.4.500"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.1"]
                                       [org.clojure/clojurescript "1.10.520"]]}
             :dev {:exclusions [[org.clojure/tools.reader]]
                   :resource-paths ["test-resources"]
                   :dependencies [[org.clojure/clojure "1.10.1"]
                                  [org.clojure/clojurescript "1.10.520"]
                                  [prismatic/schema "1.1.9"]
                                  [org.clojure/tools.reader "1.2.2"]
                                  [cider/piggieback "0.4.1"]
                                  [martian-httpkit :version]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"test" ["do" ["clean"] ["test"] ["doo" "nashorn" "test" "once"]]}
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-to "target/unit-test.js"
                                   :main 'martian.runner
                                   :optimizations :simple}}]})
