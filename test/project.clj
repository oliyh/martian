(defproject com.github.oliyh/martian-test "0.1.32-SNAPSHOT"
  :description "Testing tools for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[bigml/lein-modules "0.4.1"]]
  :dependencies [[com.github.oliyh/martian "0.1.32-SNAPSHOT"]
                 [prismatic/schema-generators "0.1.5"]
                 [org.clojure/test.check "1.1.1"]
                 [org.clojure/core.async "1.8.741"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.12.1"]
                                       [org.clojure/clojurescript "1.12.42" ]]}
             :dev {:resource-paths ["target" "../test-common"]
                   :clean-targets ^{:protect false} ["target"]
                   :dependencies [[org.clojure/tools.reader "1.5.2"]

                                  [binaryage/devtools "1.0.7"]
                                  [com.bhauman/figwheel-main "0.2.20"]
                                  [nrepl/nrepl "1.3.1"]
                                  [cider/piggieback "0.6.0"]

                                  [com.github.oliyh/martian-httpkit "0.1.32-SNAPSHOT"]
                                  [prismatic/schema "1.4.1"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" martian.test-runner]
            "test" ["do" ["clean"] ["test"] ["fig:test"]]})
