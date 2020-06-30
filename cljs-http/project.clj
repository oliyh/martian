(defproject martian-cljs-http "0.1.13-SNAPSHOT"
  :description "cljs-http implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[martian :version]
                 [cljs-http "0.1.46"]
                 [org.clojure/core.async "0.4.500"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.1"]
                                       [org.clojure/clojurescript "1.10.520"]]}
             :dev {:source-paths ["../test-common" "dev"]
                   :resource-paths ["../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/clojure "1.10.1"]
                                  [org.clojure/tools.reader "1.2.2"]
                                  [pedestal-api "0.3.4"]
                                  [com.bhauman/figwheel-main "0.2.1"
                                   :exclusions [org.eclipse.jetty.websocket/websocket-server
                                                org.eclipse.jetty.websocket/websocket-servlet]]
                                  [org.eclipse.jetty.websocket/websocket-server "9.4.18.v20190429"]
                                  [org.eclipse.jetty.websocket/websocket-servlet "9.4.18.v20190429"]
                                  [io.pedestal/pedestal.service "0.5.7"]
                                  [io.pedestal/pedestal.jetty "0.5.7"]
                                  [binaryage/devtools "1.0.0"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [org.clojure/tools.reader "1.2.2"]
                                  [cider/piggieback "0.4.1"]]}}
  :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "test"      ["do" ["clean"] ["run" "-m" "martian.runner"]]})
