(defproject com.github.oliyh/martian "0.1.32-SNAPSHOT"
  :description "Client routing for Swagger APIs"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[lein-parent "0.3.9"]]
  :parent-project {:path "../project.clj"
                   :inherit [:managed-dependencies]}
  :dependencies [[frankiesardo/tripod "0.2.0"]
                 [prismatic/schema "1.4.1"]
                 [metosin/schema-tools "0.13.1"]
                 [metosin/spec-tools "0.10.7"
                  :exclusions [com.fasterxml.jackson.core/jackson-databind]]
                 [org.clojure/spec.alpha "0.5.238"]
                 [camel-snake-kebab "0.4.3"]
                 [cheshire "6.0.0"]
                 [clj-commons/clj-yaml "1.0.29"]
                 [lambdaisland/uri "1.19.155"]

                 [com.cognitect/transit-clj "1.0.333"]
                 [com.cognitect/transit-cljs "0.8.280"]
                 [org.flatland/ordered "1.15.12"]
                 [ring/ring-codec "1.3.0"]]
  :java-source-paths ["src"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.12.1"]
                                       [org.clojure/clojurescript "1.12.42"]]}
             :dev {:source-paths ["../test-common"]
                   :resource-paths ["target" "test-resources" "../test-common"]
                   :clean-targets ^{:protect false} ["target"]
                   :dependencies [[org.slf4j/slf4j-simple "2.0.17"]

                                  [binaryage/devtools "1.0.7"]
                                  [com.bhauman/figwheel-main "0.2.20"]
                                  [nrepl/nrepl "1.3.1"]
                                  [cider/piggieback "0.6.0"]

                                  [nubank/matcher-combinators "3.8.5"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}}
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dist"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" martian.test-runner]
            "test" ["do" ["clean"] ["test"] ["fig:test"]]})
