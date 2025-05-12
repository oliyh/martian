(defproject com.github.oliyh/martian-clj-http-lite "0.1.31"
  :description "clj-http-lite implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[com.github.oliyh/martian :version]
                 [cheshire "5.10.1"]
                 [org.clj-commons/clj-http-lite "1.0.13"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.3"]]}
             :dev {:source-paths ["../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/tools.reader "1.3.5"]
                                  [pedestal-api "0.3.5"]
                                  [io.pedestal/pedestal.service "0.5.9"]
                                  [io.pedestal/pedestal.jetty "0.5.9"]]}})
