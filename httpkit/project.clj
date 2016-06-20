(defproject martian-httpkit "0.1.0-SNAPSHOT"
  :description "httpkit implementation for martian"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[martian "0.1.0-SNAPSHOT"]
                 [http-kit "2.1.19"]
                 [cheshire "5.6.2"]]
  :java-source-paths ["src"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :dev {:exclusions [[org.clojure/tools.reader]]
                   :dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/tools.reader "0.10.0"]]}})
