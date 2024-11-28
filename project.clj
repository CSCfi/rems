(defproject rems "2.38"
  :description "Resource Entitlement Management System is a tool for managing access rights to resources, such as research datasets."
  :url "https://github.com/CSCfi/rems"

  :dependencies [[better-cond "2.1.5"]
                 [buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-sign "3.5.351"]
                 [ch.qos.logback/logback-classic "1.5.3"]
                 [clj-http "3.12.3"]
                 [cheshire "5.12.0" :exclusions [com.fasterxml.jackson.core/jackson-core]] ; clj-http uses cheshire's json parsing
                 [clj-pdf "2.6.8"]
                 [clj-time "0.15.2"]
                 [com.attendify/schema-refined "0.3.0-alpha5"]
                 [com.draines/postal "2.0.5"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-joda "2.16.1"]
                 [com.stuartsierra/dependency "1.0.0"]
                 [com.rpl/specter "1.1.4"]
                 [com.taoensso/tempura "1.5.3"]
                 [compojure "1.7.1"]
                 [conman "0.8.4"] ; 0.8.5 switches to next.jdbc, which breaks stuff and requires proper testing in production
                 [cprop "0.1.20"]
                 [dev.weavejester/medley "1.7.0"]
                 [garden "1.3.10"]
                 [hiccup "1.0.5"]
                 [com.cognitect/transit-clj "1.0.333"]
                 [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
                 [lambdaisland/deep-diff "0.0-47"]
                 [lambdaisland/kaocha "1.91.1392"]
                 [luminus-jetty "0.2.3"]
                 [luminus-migrations "0.7.2"] ; 0.7.3 switches to next.jdbc, 0.7.5 fails: No such var: prepare/statement
                 [luminus-nrepl "0.1.7"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [macroz/hiccup-find "0.6.1"]
                 [markdown-clj "1.11.9"]
                 [metosin/compojure-api "2.0.0-alpha30" :exclusions [cheshire com.fasterxml.jackson.core/jackson-core]]
                 [metosin/jsonista "0.3.8"]
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "4.15.5"]
                 [mount "0.1.17"]
                 [ns-tracker "0.4.0"]
                 [org.apache.lucene/lucene-core "9.10.0"]
                 [org.apache.lucene/lucene-queryparser "9.10.0"]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/core.cache "1.1.234"]
                 [org.clojure/core.memoize "1.1.266"]
                 [org.clojure/data.csv "1.1.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.cli "1.1.230"]
                 [org.clojure/tools.logging "1.3.0"]
                 [org.postgresql/postgresql "42.7.2"]
                 [org.webjars.bower/tether "1.4.7"] ; doesn't work with "2.0.0-beta.5", error serving the file
                 [org.webjars.npm/axe-core "4.6.3"]
                 [org.webjars.npm/better-dateinput-polyfill "4.0.0-beta.2"]
                 [org.webjars.npm/popper.js "1.16.1"]
                 [org.webjars/bootstrap "4.6.2"] ; latest before 5.x series
                 [org.webjars/font-awesome "6.1.0"] ; icons don't work with "6.2.0"
                 [org.webjars/jquery "3.7.1"]
                 [prismatic/schema-generators "0.1.4"] ; event consistency tests fail with "0.1.5"
                 [ring-cors "0.1.13"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.9.6"]
                 [ring/ring-defaults "0.4.0"]
                 [ring/ring-devel "1.9.6"]
                 [nano-id "1.1.0"]
                 [org.babashka/sci "0.8.41"]
                 [com.nextjournal/beholder "1.0.2"]]

  :min-lein-version "2.9.8"

  :source-paths ["src/clj" "src/cljc"]
  :java-source-paths ["src/java"]
  :javac-options ["-source" "8" "-target" "8"]
  :test-paths ["src/clj" "src/cljc" "test/clj" "test/cljc"] ; also run tests from src files
  :resource-paths ["resources" "target/shadow"]
  :target-path "target/%s/"
  :main rems.main
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL" "postgresql://localhost/rems?user=rems")}

  :plugins [[dev.weavejester/lein-cljfmt "0.12.0"]
            [lein-shell "0.5.0"]
            [migratus-lein "0.5.7"]
            [com.github.liquidz/antq "RELEASE"]]

  :antq {}

  :cljfmt {:load-config-file? true} ; reads from root .cljfmt.edn

  :clean-targets ["target"]

  :aliases {"npm-deps" ["shell" "sh" "-c" "npm run deps-ci"] ; npm ci always removes node_modules by design, which is unnecessary and time consuming
            "shadow-build" ["do" ["npm-deps"] ["shell" "sh" "-c" "npm run shadow-build"]]
            "shadow-release" ["do" ["npm-deps"] ["shell" "sh" "-c" "npm run shadow-release"]]
            "shadow-test" ["do" ["npm-deps"] ["shell" "sh" "-c" "npm run shadow-test"]]
            "shadow-watch" ["do" ["npm-deps"] ["shell" "sh" "-c" "npm run shadow-watch"]]
            "kaocha" ["with-profile" "test" "run" "-m" "kaocha.runner"]
            "browsertests" ["do" ["shadow-build"] ["kaocha" "browser"]]
            "alltests" ["do" ["shadow-build"] ["kaocha"] ["shadow-test"]]
            "test-ancient" ["do" ["shadow-build"] ["kaocha"] ["shadow-test"]]} ; for lein ancient to work and run all tests

  :profiles
  {:uberjar {:omit-source true
             :prep-tasks [["shell" "sh" "-c" "mkdir -p target/uberjar/resources"]
                          ["shell" "sh" "-c" "git describe --tags --long --always --dirty=-custom > target/uberjar/resources/git-describe.txt"]
                          ["shell" "sh" "-c" "git rev-parse HEAD > target/uberjar/resources/git-revision.txt"]
                          "javac"
                          "compile"
                          "shadow-release"]
             :aot :all
             :uberjar-name "rems.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources" "target/uberjar/resources"]}

   :dev [:project/dev :profiles/dev]
   :test [:project/dev :project/test :profiles/test]

   :project/dev {:dependencies [[binaryage/devtools "1.0.7"]
                                [com.clojure-goes-fast/clj-async-profiler "1.2.0"] ; also check extra :jvm-opts https://github.com/clojure-goes-fast/clj-async-profiler?tab=readme-ov-file#tuning-for-better-accuracy
                                [com.clojure-goes-fast/clj-memory-meter "0.3.0"]
                                [criterium "0.4.6"]
                                [lambdaisland/kaocha-junit-xml "1.17.101"]
                                [etaoin "1.1.41"]
                                [ring/ring-mock "0.4.0" :exclusions [cheshire]]
                                [se.haleby/stub-http "0.2.14"]
                                [com.icegreen/greenmail "1.6.15"]
                                [macroz/tangle "0.2.2"]
                                [peridot "0.5.4"]]

                 :plugins [[lein-ancient "0.7.0"]]

                 :jvm-opts ["-Drems.config=dev-config.edn"
                            "-Djdk.attach.allowAttachSelf" ; needed by clj-memory-meter on Java 9+
                            "-XX:-OmitStackTraceInFastThrow"]
                 :source-paths ["env/dev/clj"]
                 :resource-paths ["env/dev/resources"]
                 :repl-options {:init-ns rems
                                :welcome (rems/repl-help)}}
   :project/test {:jvm-opts ["-Drems.config=test-config.edn"]
                  :resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})
