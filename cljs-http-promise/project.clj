(defproject com.github.oliyh/martian-cljs-http-promise "0.1.32-SNAPSHOT"
  :description "cljs-http-promise implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[bigml/lein-modules "0.4.1"]]
  :dependencies [[com.github.oliyh/martian "0.1.32-SNAPSHOT"]
                 [com.github.oliyh/cljs-http-promise "0.1.47"]
                 [org.clojure/core.async "1.8.741"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.12.1"]
                                       [org.clojure/clojurescript "1.12.42"]]}
             :dev {:source-paths ["../test-common" "dev"]
                   :resource-paths ["target" "../test-common"]
                   :clean-targets ^{:protect false} ["target"]
                   :dependencies [[org.clojure/tools.reader "1.5.2"]

                                  [binaryage/devtools "1.0.7"]
                                  [com.bhauman/figwheel-main "0.2.18" ;; TODO: Upgrade to "0.2.20" after "pedestal-api".
                                   :exclusions [org.eclipse.jetty.websocket/websocket-server
                                                org.eclipse.jetty.websocket/websocket-servlet]]
                                  [nrepl/nrepl "1.3.1"]
                                  [cider/piggieback "0.6.0"]

                                  [org.eclipse.jetty.websocket/websocket-server "9.4.35.v20201120" :upgrade false]
                                  [org.eclipse.jetty.websocket/websocket-servlet "9.4.35.v20201120" :upgrade false]
                                  [pedestal-api "0.3.5"]
                                  [io.pedestal/pedestal.service "0.5.9"]
                                  [io.pedestal/pedestal.jetty "0.5.9"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "test"      ["do" ["clean"] ["run" "-m" "martian.runner"]]})
