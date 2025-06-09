(defproject com.github.oliyh/martian-suite "0.1.32-SNAPSHOT"
  :description "Client routing for Swagger APIs"
  :url "https://github.com/oliyh/martian"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :plugins [[bigml/lein-modules "0.4.1"]]
  :modules {:subprocess "lein"
            :versions {com.github.oliyh/martian :version
                       com.github.oliyh/martian-test :version
                       com.github.oliyh/martian-vcr :version
                       com.github.oliyh/martian-httpkit :version
                       com.github.oliyh/martian-clj-http :version
                       com.github.oliyh/martian-clj-http-lite :version
                       com.github.oliyh/martian-hato :version
                       com.github.oliyh/martian-cljs-http :version
                       com.github.oliyh/martian-cljs-http-promise :version
                       com.github.oliyh/martian-re-frame :version
                       com.github.oliyh/martian-babashka-http-client :version}}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["modules" "change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["modules" "deploy" "clojars"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["modules" "change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :aliases {"test" ["modules" "do" "test," "install"]
            "install" ["do" ["modules" "install"]]
            "deploy" ["do" ["modules" "deploy" "clojars"]]})
