(defproject com.github.oliyh/martian-clj-http-lite "0.1.34-SNAPSHOT"
  :description "clj-http-lite implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[com.github.oliyh/martian]
                 [cheshire]
                 [org.clj-commons/clj-http-lite "1.0.13"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure]]}
             :dev {:source-paths ["../test-common"]
                   :dependencies [[org.slf4j/slf4j-simple "2.0.17"]
                                  [org.clojure/tools.reader "1.5.2"]

                                  [pedestal-api "0.3.5"]
                                  [io.pedestal/pedestal.service "0.5.9"]
                                  [io.pedestal/pedestal.jetty "0.5.9"]

                                  [nubank/matcher-combinators "3.8.5"]]}})
