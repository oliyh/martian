(defproject com.github.oliyh/martian-re-frame "0.2.0"
  :description "re-frame bindings for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[com.github.oliyh/martian]
                 [com.github.oliyh/martian-cljs-http]
                 [org.clojure/core.async]]
  :profiles {:provided {:dependencies [[org.clojure/clojure]
                                       [org.clojure/clojurescript]
                                       [re-frame "1.4.3"]]}
             :dev {:source-paths ["../test-common"]
                   :resource-paths ["target" "test-resources" "../test-common"]
                   :clean-targets ^{:protect false} ["target"]
                   :dependencies [[org.slf4j/slf4j-simple "2.0.17"]
                                  [org.clojure/tools.reader "1.5.2"]

                                  [com.bhauman/figwheel-main "0.2.18" ;; TODO: Upgrade to "0.2.20" after "pedestal-api".
                                   :exclusions [org.eclipse.jetty.websocket/websocket-server
                                                org.eclipse.jetty.websocket/websocket-servlet]]

                                  [org.eclipse.jetty.websocket/websocket-server "9.4.35.v20201120" :upgrade false]
                                  [org.eclipse.jetty.websocket/websocket-servlet "9.4.35.v20201120" :upgrade false]
                                  [pedestal-api "0.3.5"]
                                  [io.pedestal/pedestal.service "0.5.9"]
                                  [io.pedestal/pedestal.jetty "0.5.9"]
                                  [day8.re-frame/test "0.1.5"]]}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "test"]
            "test"      ["do" ["clean"] ["run" "-m" "martian.runner"]]})
