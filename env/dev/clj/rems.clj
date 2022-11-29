(ns rems
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :as repl]
            [kaocha.repl]
            [rems.main]
            [rems.repl-utils]))

(defn repl-help []
  (println "Welcome to REMS!")
  (println "Some useful commands:")
  (println "  Run the server: (start-app)")
  (println "  Reload changed code and restart the server: (refresh)")
  (println "  Reload all code and restart the server: (reload)")
  (println "  Pretty-print a transit payload from your clipboard: (pptransit)")
  (println "  Running tests: (kaocha :unit)")
  (println "                 (kaocha 'rems.api.test-catalogue-items)")
  (println "                 (kaocha 'rems.api.test-catalogue-items/change-form-test)"))

(defn start-app []
  (rems.main/start-app))

(defn stop-app []
  (rems.main/stop-app))

(defn reload []
  (rems.main/stop-app)
  (repl/refresh-all :after 'rems.main/start-app))

(defn refresh []
  (rems.main/stop-app)
  (repl/refresh :after 'rems.main/start-app))

(defn pptransit []
  (rems.repl-utils/pptransit))

(def kaocha kaocha.repl/run)
