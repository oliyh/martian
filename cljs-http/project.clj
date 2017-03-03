(defproject martian-cljs-http "0.1.4"
  :description "cljs-http implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]
            [lein-cljsbuild "1.1.3"]
            [lein-doo "0.1.6"]]
  :dependencies [[martian :version]
                 [cljs-http "0.1.41"]
                 [org.clojure/core.async "0.2.374"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.8.0"]
                                       [org.clojure/clojurescript "1.9.36"]]}
             :dev {:source-paths ["../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/tools.reader "0.10.0"]
                                  [pedestal-api "0.3.0-SNAPSHOT"]
                                  [io.pedestal/pedestal.service "0.5.0"]
                                  [io.pedestal/pedestal.jetty "0.5.0"]
                                  [lein-doo "0.1.6"]
                                  [com.cemerick/piggieback "0.2.1"]]}}
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
  :aliases {"test" ["do" ["clean"] ["cljsbuild" "once" "test"] ["run" "-m" "martian.runner"]]}
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-to "target/unit-test.js"
                                   :asset-path "target/unit-test"
                                   :output-dir "target/unit-test"
                                   :main martian.runner
                                   :optimizations :whitespace}}]})
