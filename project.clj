(defproject rems "0.1.0-SNAPSHOT"

  :description "Resource Entitlement Management System is a tool for managing access rights to resources, such as research datasets."
  :url "https://github.com/CSCfi/rems"

  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                 [clj-commons/secretary "1.2.4"]
                 [clj-http "3.9.1"]
                 [clj-pdf "2.3.1"]
                 [clj-time "0.15.1"]
                 [cljs-ajax "0.8.0"]
                 [cljsjs/react "16.8.3-0"] ; overrides the old version used by Reagent
                 [cljsjs/react-dom "16.8.3-0"]
                 [cljsjs/react-dom-server "16.8.3-0"]
                 [cljsjs/react-select "2.4.4-0"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [com.attendify/schema-refined "0.3.0-alpha4"]
                 [com.draines/postal "2.0.3"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-joda "2.9.7"]
                 [com.taoensso/tempura "1.2.1"]
                 [compojure "1.6.1"]
                 [conman "0.8.3"]
                 [cprop "0.1.13"]
                 [haka-buddy "0.2.3" :exclusions [cheshire]]
                 [hiccup "1.0.5"]
                 [javax.xml.bind/jaxb-api "2.3.1"]
                 [lambdaisland/deep-diff "0.0-25"]
                 [luminus-jetty "0.1.7"]
                 [luminus-migrations "0.6.3"]
                 [luminus-nrepl "0.1.5"]
                 [luminus/ring-ttl-session "0.3.2"]
                 [macroz/hiccup-find "0.6.1"]
                 [markdown-clj "1.0.7"]
                 [medley "1.2.0"]
                 [metosin/compojure-api "2.0.0-alpha28" :exclusions [cheshire com.fasterxml.jackson.core/jackson-core]]
                 [metosin/jsonista "0.2.2"]
                 [metosin/komponentit "0.3.7"]
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "3.20.1"]
                 [mount "0.1.15"]
                 [org.clojars.luontola/garden "1.3.6-patch1"] ;; TODO: waiting for a new release with https://github.com/noprompt/garden/pull/172
                 [org.clojars.luontola/ns-tracker "0.3.1-patch3"] ;; TODO: waiting for a new release with https://github.com/weavejester/ns-tracker/pull/24
                 [org.clojars.pntblnk/clj-ldap "0.0.16"]
                 [org.clojars.runa/conjure "2.2.0"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [org.clojure/core.cache "0.7.2"]
                 [org.clojure/core.memoize "0.7.1"]
                 [org.clojure/tools.cli "0.4.1"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.postgresql/postgresql "42.2.5"]
                 [org.webjars.bower/tether "1.4.4"]
                 [org.webjars.npm/better-dateinput-polyfill "3.0.0"]
                 [org.webjars.npm/better-dom "4.0.0"]
                 [org.webjars.npm/diff-match-patch "1.0.4"]
                 [org.webjars.npm/popper.js "1.14.6"]
                 [org.webjars/bootstrap "4.2.1"]
                 [org.webjars/font-awesome "5.6.3"]
                 [org.webjars/jquery "3.3.1-1"]
                 [prismatic/schema-generators "0.1.2"]
                 [re-frame "0.10.6"]
                 [reagent "0.8.1"]
                 [reagent-utils "0.3.2"]
                 [ring-cors "0.1.12"]
                 [ring-middleware-format "0.7.3"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-devel "1.7.1" :exclusions [ns-tracker]]
                 [ring/ring-servlet "1.7.1"]]

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
             :nrepl-port 7002
             :css-dirs ["target/resources/public/css"]
             :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}

  :uberwar {:handler rems.handler/handler
            :init rems.handler/init
            :destroy rems.handler/destroy
            :web-xml "web.xml"
            :name "rems.war"}

  ;; cljs testing
  :npm {:devDependencies [[karma "3.1.1"]
                          [karma-cljs-test "0.1.0"]
                          [karma-chrome-launcher "2.2.0"]]}
  :doo {:build "test"
        :paths {:karma "node_modules/karma/bin/karma"}
        :alias {:default [:chrome-headless]}}

  :aliases {"kaocha" ["with-profile" "test" "run" "-m" "kaocha.runner"]
            "browsertests" ["do" ["cljsbuild" "once"] ["kaocha" "browser"]]
            "cljtests" ["do" ["cljsbuild" "once"] ["kaocha"]]
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

   :project/dev {:dependencies [[binaryage/devtools "0.9.10"]
                                [cider/piggieback "0.3.10"]
                                [com.clojure-goes-fast/clj-memory-meter "0.1.2"]
                                [criterium "0.4.4"]
                                [doo "0.1.11"]
                                [lambdaisland/kaocha "0.0-418"]
                                [etaoin "0.3.1"]
                                [figwheel-sidecar "0.5.18" :exclusions [org.clojure/tools.nrepl org.clojure/core.async com.fasterxml.jackson.core/jackson-core]]
                                [org.clojure/core.rrb-vector "0.0.14"] ;; the version doo pulls in is broken on fresh cljs
                                [re-frisk "0.5.4"]
                                [ring/ring-mock "0.3.2" :exclusions [cheshire]]
                                [se.haleby/stub-http "0.2.5"]]

                 :plugins [[lein-ancient "0.6.15"]
                           [lein-doo "0.1.11"]
                           [lein-figwheel "0.5.18"]]

                 :jvm-opts ["-Drems.config=dev-config.edn"
                            "-Djdk.attach.allowAttachSelf"] ; needed by clj-memory-meter on Java 9+
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
