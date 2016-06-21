(defproject martian-clj-http "0.1.0-SNAPSHOT"
  :description "clj-http implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-modules "0.3.11"]]
  :dependencies [[martian :version]
                 [clj-http "2.2.0"]
                 [cheshire "5.6.2"]]
  :java-source-paths ["src"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :dev {:exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/tools.reader "0.10.0"]]}})
