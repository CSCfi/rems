(defproject rems "0.1.0-SNAPSHOT"

  :description "Resource Entitlement Management System is a tool for managing access rights to resources, such as research datasets."
  :url "https://github.com/CSCfi/rems"

  :dependencies [[buddy/buddy-sign "3.1.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [clj-commons/secretary "1.2.4"]
                 [clj-http "3.10.2"]
                 [cheshire "5.10.0"] ;; clj-http uses cheshire's json parsing
                 [clj-pdf "2.5.4"]
                 [clj-time "0.15.2"]
                 [cljs-ajax "0.8.0"]
                 [cljsjs/react "16.9.0-1"]
                 [cljsjs/react-dom "16.9.0-1"]
                 [cljsjs/react-dom-server "16.9.0-1"]
                 [cljsjs/react-select "2.4.4-0" :exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server]]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [com.attendify/schema-refined "0.3.0-alpha5"]
                 [com.auth0/java-jwt "3.10.3"]
                 [com.auth0/jwks-rsa "0.12.0"]
                 [com.draines/postal "2.0.3"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-joda "2.11.2"]
                 [com.rpl/specter "1.1.3"]
                 [com.taoensso/tempura "1.2.1"]
                 [compojure "1.6.2"]
                 [conman "0.8.4"] ;; 0.8.5 switches to next.jdbc, which breaks stuff and requires proper testing in production
                 [cprop "0.1.17"]
                 [funcool/promesa "5.1.0"]
                 [garden "1.3.10"]
                 [haka-buddy "0.2.3" :exclusions [cheshire]]
                 [hiccup "1.0.5"]
                 [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
                 [lambdaisland/deep-diff "0.0-47"]
                 [luminus-jetty "0.1.7"] ;; luminus-jetty 0.1.9 brings in websocket stuff that isn't compatible with the old tomcat versions we use
                 [luminus-migrations "0.6.7"]
                 [luminus-nrepl "0.1.6"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [macroz/hiccup-find "0.6.1"]
                 [markdown-clj "1.10.5"]
                 [medley "1.3.0"]
                 [metosin/compojure-api "2.0.0-alpha30" :exclusions [cheshire com.fasterxml.jackson.core/jackson-core]]
                 [metosin/jsonista "0.2.7"]
                 [metosin/komponentit "0.3.10"]
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "3.25.3"]
                 [mount "0.1.16"]
                 [ns-tracker "0.4.0"]
                 [org.apache.lucene/lucene-core "8.6.1"]
                 [org.apache.lucene/lucene-queryparser "8.6.1"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [org.clojure/core.cache "1.0.207"]
                 [org.clojure/core.memoize "1.0.236"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.postgresql/postgresql "42.2.16"]
                 [org.webjars.bower/tether "1.4.4"]
                 [org.webjars.npm/better-dateinput-polyfill "3.0.0"]
                 [org.webjars.npm/better-dom "4.0.0"]
                 [org.webjars.npm/diff-match-patch "1.0.4"]
                 [org.webjars.npm/popper.js "1.16.1"]
                 [org.webjars/bootstrap "4.4.1-1"]
                 [org.webjars/font-awesome "5.12.0"]
                 [org.webjars/jquery "3.5.1"]
                 [prismatic/schema-generators "0.1.3"]
                 [px0/beautify-web "0.1.1"]
                 [re-frame "0.11.0"]
                 [reagent "0.9.1"]
                 [reagent-utils "0.3.3"]
                 [ring-cors "0.1.13"]
                 [ring-middleware-format "0.7.4"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.8.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-devel "1.8.1"]
                 [ring/ring-servlet "1.8.1"]
                 [venantius/accountant "0.2.5"]]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj" "src/cljc"]
  :java-source-paths ["src/java"]
  :javac-options ["-source" "8" "-target" "8"]
  :test-paths ["src/clj" "src/cljc" "test/clj" "test/cljc"] ;; also run tests from src files
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main rems.standalone
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL" "postgresql://localhost/rems?user=rems")}

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-cprop "1.0.3"]
            [lein-npm "0.6.2"]
            [lein-shell "0.5.0"]
            [lein-uberwar "0.2.1"]
            [migratus-lein "0.5.7"]]

  :clean-targets ["target"]

  :figwheel {:http-server-root "public"
             :server-logfile "log/figwheel_server.log"
             :nrepl-port 7002
             :css-dirs ["target/resources/public/css"]
             :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}

  :uberwar {:handler rems.handler/handler
            :init rems.handler/init
            :destroy rems.handler/destroy
            :web-xml "web.xml"
            :name "rems.war"}


  :npm {:devDependencies [;; cljs testing
                          [karma "3.1.1"]
                          [karma-junit-reporter "2.0.1"]
                          [karma-cljs-test "0.1.0"]
                          [karma-chrome-launcher "2.2.0"]
                          ;; printing to pdf
                          [puppeteer "2.0.0"]]}

  :doo {:build "test"
        :paths {:karma "node_modules/karma/bin/karma"}
        :alias {:default [:chrome-headless]}
        :karma {:config {"plugins" ["karma-junit-reporter"]
                         "reporters" ["progress" "junit"]
                         "junitReporter" {"outputDir" "target/test-results"}}}}

  :aliases {"kaocha" ["with-profile" "test" "run" "-m" "kaocha.runner"]
            "browsertests" ["do" ["cljsbuild" "once"] ["kaocha" "browser"]]
            "cljtests" ["do" ["cljsbuild" "once"] ["kaocha"]]
            "cljtests-ci" ["do" ["cljsbuild" "once"] ["kaocha" "--reporter" "kaocha.report/documentation"]]
            "alltests" ["do" ["cljsbuild" "once"] ["kaocha"] ["doo" "once"]]
            "test-ancient" ["do" ["cljsbuild" "once"] ["kaocha"] ["doo" "once"]]} ; for lein ancient to work and run all tests

  :profiles
  {:uberjar {:omit-source true
             :prep-tasks [["shell" "sh" "-c" "mkdir -p target/uberjar/resources"]
                          ["shell" "sh" "-c" "git describe --tags --long --always --dirty=-custom > target/uberjar/resources/git-describe.txt"]
                          ["shell" "sh" "-c" "git rev-parse HEAD > target/uberjar/resources/git-revision.txt"]
                          "javac"
                          "compile"
                          ["cljsbuild" "once" "min"]]
             :cljsbuild {:builds {:min {:source-paths ["src/cljc" "src/cljs"]
                                        :compiler {:output-dir "target/cljsbuild/public/js"
                                                   :output-to "target/cljsbuild/public/js/app.js"
                                                   :source-map "target/cljsbuild/public/js/app.js.map"
                                                   :optimizations :advanced
                                                   :pretty-print false
                                                   :closure-warnings {:externs-validation :off
                                                                      :non-standard-jsdoc :off}
                                                   :infer-externs :true ;; for window.rems.hooks to work
                                                   :externs ["react/externs/react.js"]}}}}
             :aot :all
             :jvm-opts ["-Dclojure.compiler.elide-meta=[:doc]"]
             :uberjar-name "rems.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources" "target/uberjar/resources"]}

   :dev [:project/dev :profiles/dev]
   :test [:project/dev :project/test :profiles/test]

   :project/dev {:dependencies [[binaryage/devtools "1.0.0"]
                                [cider/piggieback "0.4.2"]
                                [com.clojure-goes-fast/clj-memory-meter "0.1.2"]
                                [criterium "0.4.5"]
                                [doo "0.1.11"]
                                [lambdaisland/kaocha "1.0-612"]
                                [lambdaisland/kaocha-junit-xml "0.0-70"]
                                [etaoin "0.3.6"]
                                [figwheel-sidecar "0.5.19" :exclusions [org.clojure/tools.nrepl com.fasterxml.jackson.core/jackson-core]]
                                [re-frisk "0.5.4.1"]
                                [ring/ring-mock "0.4.0" :exclusions [cheshire]]
                                [se.haleby/stub-http "0.2.8"]
                                [com.icegreen/greenmail "1.5.13"]]

                 :plugins [[lein-ancient "0.6.15"]
                           [lein-doo "0.1.11"]
                           [lein-figwheel "0.5.19"]]

                 :jvm-opts ["-Drems.config=dev-config.edn"
                            "-Djdk.attach.allowAttachSelf" ; needed by clj-memory-meter on Java 9+
                            "-XX:-OmitStackTraceInFastThrow"]
                 :source-paths ["env/dev/clj"]
                 :resource-paths ["env/dev/resources"]
                 :repl-options {:init-ns rems
                                :welcome (rems/repl-help)}

                 :cljsbuild {:builds {:dev {:source-paths ["src/cljs" "src/cljc"]
                                            :figwheel {:on-jsload "rems.spa/mount-components"}
                                            :compiler {:main "rems.app"
                                                       :asset-path "/js/out"
                                                       :output-to "target/cljsbuild/public/js/app.js"
                                                       :output-dir "target/cljsbuild/public/js/out"
                                                       :source-map true
                                                       :optimizations :none
                                                       :pretty-print true
                                                       :preloads [devtools.preload re-frisk.preload]}}
                                      :test {:source-paths ["src/cljs" "src/cljc" "test/cljs"]
                                             :compiler {:output-to "target/cljsbuild/test/test.js"
                                                        :output-dir "target/cljsbuild/test/out"
                                                        :main rems.cljs-tests
                                                        :optimizations :none}}}}}
   :project/test {:jvm-opts ["-Drems.config=test-config.edn"]
                  :resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})
