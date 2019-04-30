(defproject swirrl/martian "0.1.10-SNAPSHOT"
  :description "Client routing for Swagger APIs"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[frankiesardo/tripod "0.2.0"]
                 [prismatic/schema "1.1.9"]
                 [metosin/spec-tools "0.7.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-databind]]
                 [org.clojure/spec.alpha "0.1.143"]
                 [camel-snake-kebab "0.4.0"]

                 [cheshire "5.8.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-core
                               com.fasterxml.jackson.dataformat/jackson-dataformat-cbor
                               com.fasterxml.jackson.dataformat/jackson-dataformat-smile]]
                 [com.fasterxml.jackson.core/jackson-core "2.9.5"]
                 [com.fasterxml.jackson.core/jackson-databind "2.9.5"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.9.5"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.9.5"]

                 [com.cognitect/transit-clj "0.8.300"]
                 [com.cognitect/transit-cljs "0.8.243"]
                 [frankiesardo/linked "1.2.9"]]
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.8"]]
  :java-source-paths ["src"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.9.0"]
                                       [org.clojure/clojurescript "1.9.946"]]}
             :dev {:source-paths ["../test-common"]
                   :exclusions [[org.clojure/tools.reader]]
                   :resource-paths ["test-resources"]
                   :dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/clojurescript "1.9.946"]
                                  [org.clojure/tools.reader "1.2.2"]
                                  [cider/piggieback "0.3.6"]
                                  [org.clojure/tools.nrepl "0.2.13"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
                   :jvm-opts ["-Djdk.launcher.addmods=java.xml.bind"]}}
  :aliases {"test" ["do" ["clean"] ["test"] ["doo" "nashorn" "test" "once"]]}
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-to "target/unit-test.js"
                                   :main 'martian.runner
                                   :optimizations :simple}}]})
