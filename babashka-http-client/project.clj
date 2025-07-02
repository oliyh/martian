(defproject com.github.oliyh/martian-babashka-http-client "0.1.33"
  :description "babashka http-client implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[com.github.oliyh/martian]
                 [babashka/process "0.6.23"]
                 [org.babashka/json "0.1.6"]
                 [org.babashka/http-client "0.4.23"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure]]}
             :dev {:source-paths ["../test-common"]
                   :dependencies [[org.slf4j/slf4j-simple "2.0.17"]
                                  [org.clojure/tools.reader "1.5.2"]

                                  [pedestal-api "0.3.5"]
                                  [io.pedestal/pedestal.service "0.5.9"]
                                  [io.pedestal/pedestal.jetty "0.5.9"]

                                  [nubank/matcher-combinators "3.9.1"]]}})
