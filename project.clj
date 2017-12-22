(defproject rems "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                 [clj-http "3.7.0"]
                 [clj-time "0.14.2"]
                 [org.clojars.pntblnk/clj-ldap "0.0.15"]
                 [org.clojars.runa/conjure "2.2.0"]
                 [compojure "1.6.0"]
                 [com.taoensso/tempura "1.1.2"]
                 [conman "0.7.4" :exclusions [org.clojure/tools.reader]]
                 [cprop "0.1.11"]
                 [garden "1.3.2"]
                 [haka-buddy "0.2.2" :exclusions [cheshire]]
                 [hiccup "1.0.5"]
                 [hickory "0.7.1" :exclusions [org.clojure/tools.reader]]
                 [im.chit/hara.io.scheduler "2.5.10"]
                 [luminus-jetty "0.1.5" :exclusions [org.clojure/tools.reader]]
                 [luminus-migrations "0.4.3"]
                 [luminus-nrepl "0.1.4"]
                 [luminus/ring-ttl-session "0.3.2"]
                 [macroz/hiccup-find "0.6.1" :exclusions [org.clojure/tools.reader]]
                 [markdown-clj "1.0.1"]
                 [metosin/compojure-api "1.1.11" :exclusions [cheshire
                                                              com.google.code.findbugs/jsr305]]
                 [mount "0.1.11"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.memoize "0.5.9"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.postgresql/postgresql "9.4.1212"]
                 [org.webjars.bower/tether "1.4.0"]
                 [org.webjars/bootstrap "4.0.0-alpha.6"]
                 [org.webjars/font-awesome "4.7.0"]
                 [org.webjars/jquery "3.2.1"]
                 [com.draines/postal "2.0.2"]
                 [prone "1.1.4"]
                 [ring-middleware-format "0.7.2"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-defaults "0.3.1"]
                 [ring/ring-devel "1.6.3"]
                 [ring/ring-servlet "1.6.3"]]

  :min-lein-version "2.0.0"

  :jvm-opts ["-server" "-Dconf=.lein-env"]
  :source-paths ["src/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main rems.standalone
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL")}

  :plugins [[lein-cprop "1.0.3"]
            [lein-uberwar "0.2.0"]
            [migratus-lein "0.5.2"]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                  "resources/public/css/compiled"
                                  "target"]

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
             :aot :all
             :jvm-opts ["-Dclojure.compiler.elide-meta=[:doc]"]
             :uberjar-name "rems.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:dependencies [[clj-webdriver/clj-webdriver "0.7.2" :exclusions [commons-logging]]
                                 [directory-naming/naming-java "0.8"]
                                 [org.seleniumhq.selenium/selenium-server "3.0.1" :exclusions [com.google.code.gson/gson
                                                                                               commons-logging]]
                                 [pjstadig/humane-test-output "0.8.3"]
                                 [ring/ring-mock "0.3.2" :exclusions [cheshire]]
                                 [se.haleby/stub-http "0.2.4"]]
                  :plugins [[com.jakemccrary/lein-test-refresh "0.21.1"]
                            [lein-cloverage "1.0.10"]]

                  :aot [rems.InvalidRequestException rems.auth.NotAuthorizedException]

                  :source-paths ["env/dev/clj" "test/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns rems.standalone
                                 :welcome (rems.standalone/repl-help)}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})
