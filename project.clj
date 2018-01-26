(defproject rems "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                 [clj-http "3.7.0"]
                 [clj-time "0.14.2"]
                 [cljs-ajax "0.7.3"]
                 [org.clojars.pntblnk/clj-ldap "0.0.15"]
                 [org.clojars.runa/conjure "2.2.0"]
                 [compojure "1.6.0"]
                 [com.taoensso/tempura "1.1.2"]
                 [conman "0.7.5" :exclusions [org.clojure/tools.reader]]
                 [cprop "0.1.11"]
                 [garden "1.3.3"]
                 [haka-buddy "0.2.2" :exclusions [cheshire]]
                 [hiccup "1.0.5"]
                 [hickory "0.7.1" :exclusions [org.clojure/tools.reader]]
                 [im.chit/hara.io.scheduler "2.5.10"]
                 [luminus-jetty "0.1.6" :exclusions [org.clojure/tools.reader]]
                 [luminus-migrations "0.4.5" :exclusions [org.clojure/java.jdbc]]
                 [luminus-nrepl "0.1.4"]
                 [luminus/ring-ttl-session "0.3.2"]
                 [macroz/hiccup-find "0.6.1" :exclusions [org.clojure/tools.reader]]
                 [markdown-clj "1.0.2"]
                 [metosin/compojure-api "1.1.11" :exclusions [cheshire
                                                              com.google.code.findbugs/jsr305]]
                 [mount "0.1.11"]
                 [org.clojure/clojurescript "1.9.946" :scope "provided"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/core.memoize "0.5.9"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.postgresql/postgresql "42.1.4"]
                 [org.webjars.bower/tether "1.4.0"]
                 [org.webjars.npm/popper.js "1.13.0"]
                 [org.webjars/bootstrap "4.0.0-beta.2"]
                 [org.webjars/font-awesome "4.7.0"]
                 [org.webjars/jquery "3.2.1"]
                 [com.draines/postal "2.0.2"]
                 [prone "1.3.0"]
                 [re-frame "0.10.2"]
                 [reagent "0.7.0"]
                 [reagent-utils "0.2.1"]
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
            [migratus-lein "0.5.2"]]

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

  :profiles
  {:uberjar {:omit-source true
             :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
             :aot :all
             :jvm-opts ["-Dclojure.compiler.elide-meta=[:doc]"]
             :uberjar-name "rems.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]
             :cljsbuild
             {:builds
              {:min
               {:source-paths ["src/cljc" "src/cljs" "env/prod/cljs"]
                :compiler
                {:output-to "target/cljsbuild/public/js/app.js"
                 :optimizations :advanced
                 :pretty-print false
                 :closure-warnings
                 {:externs-validation :off :non-standard-jsdoc :off}
                 :externs ["react/externs/react.js"]}}}}}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:dependencies [[clj-webdriver/clj-webdriver "0.7.2" :exclusions [commons-logging]]
                                 [directory-naming/naming-java "0.8"]
                                 [org.seleniumhq.selenium/selenium-server "3.0.1" :exclusions [com.google.code.gson/gson
                                                                                               commons-logging]]
                                 [pjstadig/humane-test-output "0.8.3"]
                                 [binaryage/devtools "0.9.8"]
                                 [com.cemerick/piggieback "0.2.2"]
                                 [figwheel-sidecar "0.5.14"]
                                 [ring/ring-mock "0.3.2" :exclusions [cheshire]]
                                 [se.haleby/stub-http "0.2.4"]
                                 [re-frisk "0.5.3"]]
                  :plugins [[com.jakemccrary/lein-test-refresh "0.21.1"]
                            [lein-cloverage "1.0.10"]
                            [lein-figwheel "0.5.14"]
                            [org.clojure/clojurescript "1.9.946"]]

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
                    {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                     :figwheel {:on-jsload "rems.spa/mount-components"}
                     :compiler
                     {:main "rems.app"
                      :asset-path "/js/out"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :source-map true
                      :optimizations :none
                      :pretty-print true
                      :preloads [re-frisk.preload]}}}}}
   :project/test {:resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})
