(defproject rems "2.39"
  :description "Resource Entitlement Management System is a tool for managing access rights to resources, such as research datasets."
  :url "https://github.com/CSCfi/rems"

  :dependencies [[better-cond "2.1.5"]
                 [binaryage/devtools "1.0.7"]
                 [buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-sign "3.6.1-359"]
                 [ch.qos.logback/logback-classic "1.5.32"]
                 [clj-http "3.13.1"]
                 [clj-pdf "2.7.4"]
                 [clj-time "0.15.2"]
                 ^{:antq/exclude ["0.8.5" "0.8.x" "0.9"]} [conman "0.8.4"] ; 0.8.5 switches to next.jdbc, which breaks stuff and requires proper testing in production
                 [com.attendify/schema-refined "0.3.0-alpha5"]
                 [com.clojure-goes-fast/clj-async-profiler "1.7.0"] ; also check extra :jvm-opts https://github.com/clojure-goes-fast/clj-async-profiler?tab=readme-ov-file#tuning-for-better-accuracy
                 [com.clojure-goes-fast/clj-memory-meter "0.4.0"]
                 [com.cognitect/transit-clj "1.1.347"]
                 [com.draines/postal "2.0.5"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-joda "2.21.1"]
                 ^{:antq/exclude "2"} [com.icegreen/greenmail "1.6.15"]
                 [com.lambdaisland/garden "1.9.606"]
                 [com.nextjournal/beholder "1.0.3"]
                 [com.rpl/specter "1.1.6"]
                 [com.stuartsierra/dependency "1.0.0"]
                 [com.taoensso/tempura "1.5.4"]
                 [compojure "1.7.2"]
                 [cprop "0.1.21"]
                 [criterium "0.4.6"]
                 [dev.weavejester/medley "1.9.0"]
                 [etaoin "1.1.43"]
                 [hiccup "2.0.0"]
                 [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
                 [lambdaisland/kaocha "1.91.1392"]
                 [lambdaisland/kaocha-junit-xml "1.17.101"]
                 [luminus-jetty "0.2.3"]
                 [luminus-nrepl "0.1.7"]
                 ^{:antq/exclude ["0.7.3" "0.7.x"]} [luminus-migrations "0.7.2"] ; 0.7.3 switches to next.jdbc, 0.7.5 fails: No such var: prepare/statement
                 [luminus/ring-ttl-session "0.3.3"]
                 [macroz/hiccup-find "0.6.1"]
                 [macroz/tangle "0.2.2"]
                 [markdown-clj "1.12.7"]
                 [metosin/compojure-api "2.0.0-alpha33"]
                 [metosin/jsonista "0.3.14"]
                 [metosin/ring-swagger "1.0.0"]
                 [metosin/ring-swagger-ui "5.31.0"]
                 [mount "0.1.23"]
                 [nano-id "1.1.0"]
                 [ns-tracker "1.0.0"]
                 ^{:antq/exclude "10"} [org.apache.lucene/lucene-core "9.12.3"] ; Next major release 10.4.x available but multiple tests throw a java.lang.IllegalArgumentException: No matching method doc found taking 1 args for class org.apache.lucene.search.IndexSearcher
                 ^{:antq/exclude "10"} [org.apache.lucene/lucene-queryparser "9.12.3"] ;... java.lang.IllegalArgumentException: No matching method doc ...
                 [org.babashka/sci "0.12.51"]
                 [org.clojure/clojure "1.12.4"]
                 [org.clojure/core.cache "1.2.263"]
                 [org.clojure/core.memoize "1.2.281"]
                 [org.clojure/data.csv "1.1.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.cli "1.3.250"]
                 [org.clojure/tools.logging "1.3.1"]
                 [org.postgresql/postgresql "42.7.10"]
                 ^{:antq/exclude "2"} [org.webjars.bower/tether "1.4.7"] ; doesn't work with "2.0.0-beta.5", error serving the file
                 [org.webjars.npm/axe-core "4.6.3"]
                 [org.webjars.npm/better-dateinput-polyfill "4.0.0-beta.2"]
                 [org.webjars.npm/popper.js "1.16.1"]
                 ^{:antq/exclude "5"} [org.webjars/bootstrap "4.6.2"] ; latest before 5.x series
                 ^{:antq/exclude ["6.2.x" "6.x" "7"]} [org.webjars/font-awesome "6.1.0"] ; icons don't work with "6.2.0"
                 ^{:antq/exclude "4"} [org.webjars/jquery "3.7.1"] ; bootstrap 4 only supports jquery 3 https://github.com/twbs/bootstrap/blob/v4.6.2/package.json#L122
                 [peridot "0.5.4"]
                 [prismatic/schema-generators "0.1.5"]
                 [ring-cors "0.1.13"]
                 [ring-webjars "0.3.1"]
                 [ring/ring-core "1.15.3"]
                 [ring/ring-defaults "0.7.0"]
                 [ring/ring-devel "1.15.3"]
                 [ring/ring-mock "0.6.2"]
                 [se.haleby/stub-http "0.2.14"]]
  :managed-dependencies [[prismatic/schema "1.4.1"]]

  :min-lein-version "2.9.8"

  :source-paths ["src/clj" "src/cljc" "test/clj" "test/cljc"] ; also include tests in classpath
  :java-source-paths ["src/java"]
  :javac-options ["--release" "8"]
  :test-paths ["src/clj" "src/cljc" "test/clj" "test/cljc"] ; also run tests from src files
  :resource-paths ["resources" "target/shadow"]
  :target-path "target/%s/"
  :main rems.main
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL" "postgresql://localhost/rems?user=rems")}

  :plugins [[dev.weavejester/lein-cljfmt "0.16.1"]
            [lein-shell "0.5.0"]
            [migratus-lein "0.7.3"]
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

   :project/dev {:plugins [[lein-ancient "0.7.0"]]

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
