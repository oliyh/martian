(defproject swirrl/martian-re-frame "0.1.10-SNAPSHOT"
  :description "re-frame bindings for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]
            [lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.8"]]
  :dependencies [[swirrl/martian :version]
                 [swirrl/martian-cljs-http :version]
                 [org.clojure/core.async "0.4.474"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.9.0"]
                                       [org.clojure/clojurescript "1.9.946"]
                                       [re-frame "0.10.5"]]}
             :dev {:source-paths ["../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/tools.reader "1.2.2"]
                                  [pedestal-api "0.3.4"]
                                  [io.pedestal/pedestal.service "0.5.3"]
                                  [io.pedestal/pedestal.jetty "0.5.3"]
                                  [lein-doo "0.1.8"]
                                  [com.cemerick/piggieback "0.2.2"]
                                  [day8.re-frame/test "0.1.5"]]}}
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
  :aliases {"test" ["do" ["clean"] ["cljsbuild" "once" "test"] ["run" "-m" "martian.runner"]]}
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-to "target/unit-test.js"
                                   :asset-path "target/unit-test"
                                   :output-dir "target/unit-test"
                                   :optimizations :whitespace}}]})
