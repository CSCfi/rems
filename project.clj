(defproject rems "0.1.0-SNAPSHOT"

  :description "Resource Entitlement Management System is a tool for managing access rights to resources, such as research datasets."
  :url "https://github.com/CSCfi/rems"

  :dependencies [[buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-sign "3.4.333"]
                 [ch.qos.logback/logback-classic "1.2.11"]
                 [clj-commons/secretary "1.2.4"]
                 [clj-http "3.12.3"]
                 [cheshire "5.10.2" :exclusions [com.fasterxml.jackson.core/jackson-core]] ;; clj-http uses cheshire's json parsing
                 [clj-pdf "2.5.8"]
                 [clj-time "0.15.2"]
                 [cljs-ajax "0.8.4"]
                 [cljsjs/react "16.9.0-1"]
                 [cljsjs/react-dom "16.9.0-1"]
                 [cljsjs/react-dom-server "16.9.0-1"]
                 [cljsjs/react-select "2.4.4-0" :exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server]]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [com.attendify/schema-refined "0.3.0-alpha5"]
                 [com.draines/postal "2.0.5"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-joda "2.13.2"]
                 [com.stuartsierra/dependency "1.0.0"]
                 [com.rpl/specter "1.1.4"]
                 [com.taoensso/tempura "1.2.1"]
                 [compojure "1.6.2"]
                 [conman "0.8.4"] ;; 0.8.5 switches to next.jdbc, which breaks stuff and requires proper testing in production
                 [cprop "0.1.19"]
                 [funcool/promesa "8.0.450"]
                 [garden "1.3.10"]
                 [hiccup "1.0.5"]
                 [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
                 [lambdaisland/deep-diff "0.0-47"]
                 [luminus-jetty "0.2.3"]
                 [luminus-migrations "0.7.1"]
                 [luminus-nrepl "0.1.7"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [macroz/hiccup-find "0.6.1"]
                 [markdown-clj "1.10.9"]
                 [medley "1.3.0"]
                 [metosin/compojure-api "2.0.0-alpha30" :exclusions [cheshire com.fasterxml.jackson.core/jackson-core]]
                 [metosin/jsonista "0.3.5"]
                 [metosin/komponentit "0.3.11"]
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "4.5.0"]
                 [mount "0.1.16"]
                 [ns-tracker "0.4.0"]
                 [org.apache.lucene/lucene-core "9.1.0"]
                 [org.apache.lucene/lucene-queryparser "9.1.0"]
                 [org.clojure/clojure "1.11.0"]
                 [org.clojure/clojurescript "1.10.764" :exclusions [com.fasterxml.jackson.core/jackson-core]] ; later ones don't work with lein-cljsbuild 1.1.8
                 [org.clojure/core.cache "1.0.225"]
                 [org.clojure/core.memoize "1.0.257"]
                 [org.clojure/data.csv "1.0.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.cli "1.0.206"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.postgresql/postgresql "42.3.3"]
                 [org.webjars.bower/tether "1.4.4"]
                 [org.webjars.npm/axe-core "4.0.2"]
                 [org.webjars.npm/better-dateinput-polyfill "4.0.0-beta.2"]
                 [org.webjars.npm/better-dom "4.0.0"]
                 [org.webjars.npm/diff-match-patch "1.0.5"]
                 [org.webjars.npm/popper.js "1.16.1"]
                 [org.webjars/bootstrap "4.4.1-1"]
                 [org.webjars/font-awesome "6.1.0"]
                 [org.webjars/jquery "3.6.0"]
                 [prismatic/schema-generators "0.1.4"]
                 [re-frame "1.2.0"]
                 [reagent "1.1.1"]
                 [reagent-utils "0.3.4"]
                 [ring-cors "0.1.13"]
                 [ring-middleware-format "0.7.5"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.9.5"]
                 [ring/ring-defaults "0.3.3"]
                 [ring/ring-devel "1.9.5"]
                 [ring/ring-servlet "1.9.5"]
                 [venantius/accountant "0.2.5"]]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj" "src/cljc"]
  :java-source-paths ["src/java"]
  :javac-options ["-source" "8" "-target" "8"]
  :test-paths ["src/clj" "src/cljc" "test/clj" "test/cljc"] ;; also run tests from src files
  :resource-paths ["resources" "target/shadow"]
  :target-path "target/%s/"
  :main rems.standalone
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL" "postgresql://localhost/rems?user=rems")}

  :plugins [[lein-cljfmt "0.6.7"]
            [lein-cprop "1.0.3"]
            [lein-npm "0.6.2"]
            [lein-shell "0.5.0"]
            [migratus-lein "0.5.7"]]

  :cljfmt {:paths ["src/clj" "src/cljc" "src/cljs" "test/clj" "test/cljc" "test/cljs"] ;; need explicit paths to include cljs
           :remove-consecutive-blank-lines? false} ;; too many changes for now

  :clean-targets ["target"]

  ;; :figwheel {:http-server-root "public"
  ;;            :server-logfile "log/figwheel_server.log"
  ;;            :nrepl-port 7002
  ;;            :css-dirs ["target/resources/public/css/en" "target/resources/public/css/fi" "target/resources/public/css/sv"]
  ;;            :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}

  ;; :npm {:devDependencies [;; cljs testing
  ;;                         [karma "3.1.1"]
  ;;                         [karma-junit-reporter "2.0.1"]
  ;;                         [karma-cljs-test "0.1.0"]
  ;;                         [karma-chrome-launcher "2.2.0"]
  ;;                         ;; printing to pdf
  ;;                         [puppeteer "2.0.0"]]}

  ;; :doo {:build "test"
  ;;       :paths {:karma "node_modules/karma/bin/karma"}
  ;;       :alias {:default [:chrome-headless]}
  ;;       :karma {:config {"plugins" ["karma-junit-reporter"]
  ;;                        "reporters" ["progress" "junit"]
  ;;                        "junitReporter" {"outputDir" "target/test-results"}}}}

  :aliases {"shadow-build" ["shell" "sh" "-c" "npm install && npx shadow-cljs compile app"]
            "shadow-release" ["shell" "sh" "-c" "npm install && npx shadow-cljs release app"]
            "kaocha" ["with-profile" "test" "run" "-m" "kaocha.runner"]
            "browsertests" ["do" "shadow-build" ["kaocha" "browser"]]
            "cljtests" ["do" "shadow-build" ["kaocha"]]
            "cljtests-ci" ["do" "shadow-build" ["kaocha" "--reporter" "kaocha.report/documentation"]]
            "alltests" ["do" "shadow-build" ["kaocha"] #_["doo" "once"]]
            "test-ancient" ["do" "shadow-build" ["kaocha"] #_["doo" "once"]]} ; for lein ancient to work and run all tests

  :profiles
  {:uberjar {:omit-source true
             :prep-tasks [["shell" "sh" "-c" "mkdir -p target/uberjar/resources"]
                          ["shell" "sh" "-c" "git describe --tags --long --always --dirty=-custom > target/uberjar/resources/git-describe.txt"]
                          ["shell" "sh" "-c" "git rev-parse HEAD > target/uberjar/resources/git-revision.txt"]
                          "javac"
                          "compile"
                          "shadow-release"]
             #_{:builds {:min {:source-paths ["src/cljc" "src/cljs"]
                               :compiler {:output-dir "target/cljsbuild/public/js"
                                          :output-to "target/cljsbuild/public/js/app.js"
                                          :source-map "target/cljsbuild/public/js/app.js.map"
                                          :optimizations :advanced
                                          :pretty-print false
                                          :closure-warnings {:externs-validation :off
                                                             :non-standard-jsdoc :off}
                                          :warnings {:munged-namespace false} ;; for rems.actions.delete
                                          :infer-externs :true ;; for window.rems.hooks to work
                                          :externs ["react/externs/react.js"]}}}}
             :aot :all
             :uberjar-name "rems.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources" "target/uberjar/resources"]}

   :dev [:project/dev :profiles/dev]
   :test [:project/dev :project/test :profiles/test]

   :project/dev {:dependencies [[binaryage/devtools "1.0.5"]
                                [com.clojure-goes-fast/clj-memory-meter "0.1.3"]
                                [criterium "0.4.6"]
                                #_[doo "0.1.11"]
                                [lambdaisland/kaocha "1.64.1010"]
                                [lambdaisland/kaocha-junit-xml "0.0.76"]
                                [etaoin "0.4.6"]
                                [re-frisk "1.5.2"] ;; coupled to the reagent version
                                [ring/ring-mock "0.4.0" :exclusions [cheshire]]
                                [se.haleby/stub-http "0.2.12"]
                                [com.icegreen/greenmail "1.6.7"]]

                 :plugins [[lein-ancient "0.6.15"]
                           #_[lein-doo "0.1.11"]]

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
