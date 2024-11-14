(defproject com.github.oliyh/martian-vcr "0.1.30"
  :description "Recording and playback for Martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[com.github.oliyh/martian :version]
                 [fipp "0.6.24"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.3"]
                                       [org.clojure/clojurescript "1.10.866"]]}
             :dev {:resource-paths ["../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/tools.reader "1.3.5"]
                                  [binaryage/devtools "1.0.3"]
                                  [com.bhauman/figwheel-main "0.2.13"]
                                  [cider/piggieback "0.5.2"]
                                  [org.clojure/tools.nrepl "0.2.13"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" martian.test-runner]
            "test" ["do" ["clean"] ["test"] ["fig:test"]]})
