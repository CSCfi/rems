(defproject rems "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                 [clj-http "3.9.0"]
                 [clj-time "0.14.4"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [cljs-ajax "0.7.3"]
                 [org.clojars.pntblnk/clj-ldap "0.0.16"]
                 [org.clojars.runa/conjure "2.2.0"]
                 [compojure "1.6.1"]
                 [com.taoensso/tempura "1.2.1"]
                 [conman "0.7.8" ]
                 [cprop "0.1.11"]
                 [garden "1.3.5"]
                 [haka-buddy "0.2.3" :exclusions [cheshire]]
                 [hiccup "1.0.5"]
                 [im.chit/hara.io.scheduler "2.5.10"]
                 [luminus-jetty "0.1.6"]
                 [luminus-migrations "0.5.0"]
                 [luminus-nrepl "0.1.4"]
                 [luminus/ring-ttl-session "0.3.2"]
                 [markdown-clj "1.0.2"]
                 [metosin/compojure-api "2.0.0-alpha18" :exclusions [cheshire com.fasterxml.jackson.core/jackson-core]]
                 [metosin/komponentit "0.3.5"]
                 [mount "0.1.12"]
                 [org.clojure/clojurescript "1.10.238" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/core.memoize "0.7.1"]
                 [org.clojure/tools.cli "0.3.7"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.postgresql/postgresql "42.2.2"]
                 [org.webjars.bower/tether "1.4.3"]
                 [org.webjars.npm/popper.js "1.14.3"]
                 [org.webjars/bootstrap "4.1.0"]
                 [org.webjars/font-awesome "5.0.13"]
                 [org.webjars/jquery "3.3.1-1"]
                 [com.draines/postal "2.0.2"]
                 [re-frame "0.10.5"]
                 [reagent "0.8.1"]
                 [reagent-utils "0.3.1"]
                 [ring-cors "0.1.12"]
                 [ring-middleware-format "0.7.2"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-defaults "0.3.1"]
                 [ring/ring-devel "1.6.3"]
                 [ring/ring-servlet "1.6.3"]
                 [secretary "1.2.3"]]

  :min-lein-version "2.0.0"

  :jvm-opts ["-server" "-Dconf=.lein-env"]
  :source-paths ["src/clj" "src/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main rems.standalone
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL")}

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-cprop "1.0.3"]
            [lein-uberwar "0.2.0"]
            [lein-shell "0.5.0"]
            [migratus-lein "0.5.7"]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    "resources/public/css/compiled"
                                    "target"]

  :figwheel
  {:http-server-root "public"
   :nrepl-port 7002
   :css-dirs ["resources/public/css"]
   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :uberwar {:handler rems.handler/app
            :init rems.handler/init
            :destroy rems.handler/destroy
            :web-xml "web.xml"
            :name "rems.war"}

  ;; flag tests that need a db with ^:integration
  :test-selectors {:default (complement :integration)
                   :all (constantly true)}
  :eftest {:multithread? false} ;; integration tests aren't safe to run in parallel

  :profiles
  {:uberjar {:omit-source true
             :prep-tasks [["shell" "sh" "-c" "mkdir -p target/uberjar/resources && git describe --always --dirty=-custom > target/uberjar/resources/git-describe.txt"]
                          "compile"
                          ["cljsbuild" "once" "min"]]
             :cljsbuild
             {:builds
              {:min
               {:source-paths ["src/cljc" "src/cljs"]
                :compiler
                {:output-dir "target/cljsbuild/public/js"
                 :output-to "target/cljsbuild/public/js/app.js"
                 :source-map "target/cljsbuild/public/js/app.js.map"
                 :optimizations :advanced
                 :pretty-print false
                 :closure-warnings
                 {:externs-validation :off :non-standard-jsdoc :off}
                 :externs ["react/externs/react.js"]}}}}
             :aot :all
             :jvm-opts ["-Dclojure.compiler.elide-meta=[:doc]"]
             :uberjar-name "rems.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources" "target/uberjar/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:dependencies [[pjstadig/humane-test-output "0.8.3"]
                                 [eftest "0.5.2"]
                                 [binaryage/devtools "0.9.10"]
                                 [com.cemerick/piggieback "0.2.2"]
                                 [figwheel-sidecar "0.5.16" :exclusions [org.clojure/tools.nrepl
                                                                         org.clojure/core.async
                                                                         com.fasterxml.jackson.core/jackson-core]]
                                 [ring/ring-mock "0.3.2" :exclusions [cheshire]]
                                 [se.haleby/stub-http "0.2.5"]
                                 [re-frisk "0.5.4"]
                                 ]
                  :plugins [[com.jakemccrary/lein-test-refresh "0.21.1"]
                            [lein-eftest "0.5.2"]
                            [lein-cloverage "1.0.10"]
                            [lein-figwheel "0.5.16"]]
                  :aot [rems.InvalidRequestException rems.auth.NotAuthorizedException]

                  :source-paths ["env/dev/clj" "test/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns rems.standalone
                                 :welcome (rems.standalone/repl-help)}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]

                  :cljsbuild
                  {:builds
                   {:dev
                    {:source-paths ["src/cljs" "src/cljc"]
                     :figwheel {:on-jsload "rems.spa/mount-components"}
                     :compiler
                     {:main "rems.app"
                      :asset-path "/js/out"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :source-map true
                      :optimizations :none
                      :pretty-print true
                      :preloads [devtools.preload re-frisk.preload]}}}}}
   :project/test {:resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})
