(defproject com.github.oliyh/martian-vcr "0.2.1"
  :description "Recording and playback for Martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[com.github.oliyh/martian]
                 [fipp "0.6.27"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure]
                                       [org.clojure/clojurescript]]}
             :dev {:resource-paths ["target" "../test-common"]
                   :clean-targets ^{:protect false} ["target"]
                   :dependencies [[org.slf4j/slf4j-simple "2.0.17"]
                                  [org.clojure/tools.reader "1.5.2"]

                                  [binaryage/devtools "1.0.7"]
                                  [com.bhauman/figwheel-main "0.2.20"]
                                  [nrepl/nrepl "1.3.1"]
                                  [cider/piggieback "0.6.0"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" martian.test-runner]
            "test" ["do" ["clean"] ["test"] ["fig:test"]]})
