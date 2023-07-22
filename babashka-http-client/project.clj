(defproject com.github.oliyh/martian-babashka-http-client "0.1.26-SNAPSHOT"
  :description "babashka http-client implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[com.github.oliyh/martian :version]
                 [babashka/process "0.4.16"]
                 [org.babashka/json "0.1.1"]
                 [org.babashka/http-client "0.1.6"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.11.1"]]}
             :dev {:source-paths ["../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/tools.reader "1.3.5"]
                                  [pedestal-api "0.3.5"]
                                  [io.pedestal/pedestal.service "0.5.9"]
                                  [io.pedestal/pedestal.jetty "0.5.9"]]}})
