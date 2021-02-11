(defproject martian-re-frame "0.1.16-SNAPSHOT"
  :description "re-frame bindings for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[martian :version]
                 [martian-cljs-http :version]
                 [org.clojure/core.async "0.4.474"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.1"]
                                       [org.clojure/clojurescript "1.10.520"]
                                       [re-frame "0.10.8"]]}
             :dev {:source-paths ["../test-common"]
                   :resource-paths ["test-resources" "../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/clojure "1.10.1"]
                                  [com.bhauman/figwheel-main "0.2.1"
                                   :exclusions [org.eclipse.jetty.websocket/websocket-server
                                                org.eclipse.jetty.websocket/websocket-servlet]]
                                  [org.eclipse.jetty.websocket/websocket-server "9.4.18.v20190429"]
                                  [org.eclipse.jetty.websocket/websocket-servlet "9.4.18.v20190429"]
                                  [org.clojure/tools.reader "1.2.2"]
                                  [pedestal-api "0.3.4"]
                                  [io.pedestal/pedestal.service "0.5.7"]
                                  [io.pedestal/pedestal.jetty "0.5.7"]
                                  [day8.re-frame/test "0.1.5"]]}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "test"]
            "test"      ["do" ["clean"] ["run" "-m" "martian.runner"]]})
