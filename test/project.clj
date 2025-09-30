(defproject com.github.oliyh/martian-test "0.2.1"
  :description "Testing tools for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[com.github.oliyh/martian]
                 [prismatic/schema-generators "0.1.5"]
                 [org.clojure/test.check "1.1.1"]
                 [org.clojure/core.async]]
  :profiles {:provided {:dependencies [[org.clojure/clojure]
                                       [org.clojure/clojurescript]]}
             :dev {:resource-paths ["target" "../test-common"]
                   :clean-targets ^{:protect false} ["target"]
                   :dependencies [[org.slf4j/slf4j-simple "2.0.17"]
                                  [org.clojure/tools.reader "1.5.2"]

                                  [binaryage/devtools "1.0.7"]
                                  [com.bhauman/figwheel-main "0.2.20"]
                                  [nrepl/nrepl "1.3.1"]
                                  [cider/piggieback "0.6.0"]

                                  [com.github.oliyh/martian-httpkit]
                                  [prismatic/schema "1.4.1"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" martian.test-runner]
            "test" ["do" ["clean"] ["test"] ["fig:test"]]})
