(defproject com.github.oliyh/martian-re-frame "0.1.30"
  :description "re-frame bindings for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[com.github.oliyh/martian :version]
                 [com.github.oliyh/martian-cljs-http :version]
                 [org.clojure/core.async "1.3.618"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.3"]
                                       [org.clojure/clojurescript "1.10.866"]
                                       [re-frame "1.2.0"]]}
             :dev {:source-paths ["../test-common"]
                   :resource-paths ["test-resources" "../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :dependencies [[com.bhauman/figwheel-main "0.2.13"
                                   :exclusions [org.eclipse.jetty.websocket/websocket-server
                                                org.eclipse.jetty.websocket/websocket-servlet]]
                                  [org.eclipse.jetty.websocket/websocket-server "9.4.35.v20201120" :upgrade false]
                                  [org.eclipse.jetty.websocket/websocket-servlet "9.4.35.v20201120" :upgrade false]
                                  [org.clojure/tools.reader "1.3.5"]
                                  [pedestal-api "0.3.5"]
                                  [io.pedestal/pedestal.service "0.5.9"]
                                  [io.pedestal/pedestal.jetty "0.5.9"]
                                  [day8.re-frame/test "0.1.5"]]}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "test"]
            "test"      ["do" ["clean"] ["run" "-m" "martian.runner"]]})
