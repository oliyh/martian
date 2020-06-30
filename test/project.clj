(defproject martian-test "0.1.13-SNAPSHOT"
  :description "Testing tools for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[martian :version]
                 [prismatic/schema-generators "0.1.2"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/core.async "0.4.500"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.1"]
                                       [org.clojure/clojurescript "1.10.520"]]}
             :dev {:exclusions [[org.clojure/tools.reader]]
                   :resource-paths ["../test-common"]
                   :dependencies [[org.clojure/clojure "1.10.1"]
                                  [org.clojure/clojurescript "1.10.520"]
                                  [prismatic/schema "1.1.9"]
                                  [binaryage/devtools "1.0.0"]
                                  [com.bhauman/figwheel-main "0.2.1"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [org.clojure/tools.reader "1.2.2"]
                                  [cider/piggieback "0.4.1"]
                                  [martian-httpkit :version]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" martian.test-runner]
            "test" ["do" ["clean"] ["test"] ["fig:test"]]})
