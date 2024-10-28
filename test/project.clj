(defproject com.github.oliyh/martian-test "0.1.28-SNAPSHOT"
  :description "Testing tools for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[com.github.oliyh/martian :version]
                 [prismatic/schema-generators "0.1.3"]
                 [org.clojure/test.check "1.1.0"]
                 [org.clojure/core.async "1.3.618"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.3"]
                                       [org.clojure/clojurescript "1.10.520" :upgrade false] ;; upgrading this makes the tests fail for some reason...
                                       ]}
             :dev {:exclusions [[org.clojure/tools.reader]]
                   :resource-paths ["../test-common"]
                   :dependencies [[prismatic/schema "1.1.12"]
                                  [binaryage/devtools "1.0.3"]
                                  [com.bhauman/figwheel-main "0.2.13"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [org.clojure/tools.reader "1.3.5"]
                                  [cider/piggieback "0.5.2"]
                                  [com.github.oliyh/martian-httpkit :version]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" martian.test-runner]
            "test" ["do" ["clean"] ["test"] ["fig:test"]]})
