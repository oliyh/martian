(defproject com.github.oliyh/martian-httpkit "0.1.32-SNAPSHOT"
  :description "httpkit implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[bigml/lein-modules "0.4.1"]]
  :dependencies [[com.github.oliyh/martian :version]
                 [cheshire "6.0.0"]
                 [http-kit "2.8.0"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.12.1"]]}
             :dev {:source-paths ["../test-common"]
                   :dependencies [[org.slf4j/slf4j-simple "2.0.17"]
                                  [org.clojure/tools.reader "1.5.2"]

                                  [pedestal-api "0.3.5"]
                                  [io.pedestal/pedestal.service "0.5.9"]
                                  [io.pedestal/pedestal.jetty "0.5.9"]]}})
