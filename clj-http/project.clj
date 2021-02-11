(defproject martian-clj-http "0.1.15"
  :description "clj-http implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[martian :version]
                 [clj-http "3.10.0"]
                 [cheshire "5.9.0"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.1"]]}
             :dev {:source-paths ["../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/clojure "1.10.1"]
                                  [org.clojure/tools.reader "1.2.2"]
                                  [pedestal-api "0.3.4"]
                                  [io.pedestal/pedestal.service "0.5.3"]
                                  [io.pedestal/pedestal.jetty "0.5.3"]]}})
