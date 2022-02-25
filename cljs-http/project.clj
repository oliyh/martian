(defproject com.github.oliyh/martian-cljs-http "0.1.22-SNAPSHOT"
  :description "cljs-http implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[com.github.oliyh/martian :version]
                 [cljs-http "0.1.46"]
                 [org.clojure/core.async "1.3.618"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.3"]
                                       [org.clojure/clojurescript "1.10.866"]]}
             :dev {:source-paths ["../test-common" "dev"]
                   :resource-paths ["../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/tools.reader "1.3.5"]
                                  [pedestal-api "0.3.5"]
                                  [com.bhauman/figwheel-main "0.2.13"
                                   :exclusions [org.eclipse.jetty.websocket/websocket-server
                                                org.eclipse.jetty.websocket/websocket-servlet]]
                                  [org.eclipse.jetty.websocket/websocket-server "9.4.35.v20201120" :upgrade false]
                                  [org.eclipse.jetty.websocket/websocket-servlet "9.4.35.v20201120" :upgrade false]
                                  [io.pedestal/pedestal.service "0.5.9"]
                                  [io.pedestal/pedestal.jetty "0.5.9"]
                                  [binaryage/devtools "1.0.3"]
                                  [org.clojure/tools.nrepl "0.2.13"]
                                  [cider/piggieback "0.5.2"]]}}
  :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "test"      ["do" ["clean"] ["run" "-m" "martian.runner"]]})
