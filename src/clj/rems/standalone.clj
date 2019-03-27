(ns rems.standalone
  "Run the REMS app in an embedded http server."
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [luminus-migrations.core :as migrations]
            [rems.migrations.convert-to-dynamic-applications :as convert-to-dynamic]
            [luminus.http-server :as http]
            [luminus.repl-server :as repl]
            [mount.extensions.namespace-deps :as mount-nsd]
            [mount.lite :as mount]
            [rems.config :refer [env]]
            [rems.db.test-data :as test-data]
            [rems.handler :as handler]
            [rems.validate :as validate])
  (:refer-clojure :exclude [parse-opts])
  (:gen-class))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(mount/defstate
  http-server
  :start
  (http/start (assoc @env :handler @handler/handler))
  :stop
  (when @http-server (http/stop @http-server)))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped")))

(defn start-app []
  (doseq [component (mount-nsd/start)]
    (log/info component "started"))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app))
  (binding [rems.db.core/*db* @rems.db.core/db-connection]
    (validate/validate)))

(defn repl-help []
  (println "Welcome to REMS!")
  (println "Some useful commands:")
  (println "  Run the server  (start-app)")
  (println "  Run all tests   (user/run-all-tests)")
  (println "  Run some tests  (user/run-tests 'rems.test.api.applications 'rems.test.api.actions)")
  (println "  Pretty-print a transit payload from your clipboard  (user/pptransit)"))

(defn -main
  "Arguments can be either arguments to mount/start-with-args, or one of
     \"migrate\" -- migrate database
     \"rollback\" -- roll back database migration
     \"reset\" -- empties database and runs migrations to empty db
     \"convert-to-dynamic\" -- convert legacy applications to use dynamic workflow
     \"test-data\" -- insert test data into database
     \"demo-data\" -- insert data for demoing purposes into database
     \"validate\" -- validate data in db"
  [& args]
  (cond
    (#{"migrate" "rollback"} (first args))
    (do
      (mount-nsd/start #'rems.db.core/db-connection)
      (binding [rems.db.core/*db* @rems.db.core/db-connection]
        (migrations/migrate args (select-keys @env [:database-url]))))
    (= "reset" (first args))
    (do
      (println "\n\n*** Are you absolutely sure??? Reset empties the whole database and runs migrations to empty db.***\nType 'YES' to proceed")
      (when (= "YES" (read-line))
        (println "Running reset")
        (mount-nsd/start #'rems.db.core/db-connection)
        (binding [rems.db.core/*db* @rems.db.core/db-connection]
          (migrations/migrate args (select-keys @env [:database-url])))))
    (= "convert-to-dynamic" (first args))
    (do
      (when-not (= 2 (count args))
        (println "Usage: convert-to-dynamic <new-workflow-id>")
        (System/exit 1))
      (let [new-workflow-id (Long/parseLong (second args))]
        (mount-nsd/start #'rems.db.core/db-connection)
        (binding [rems.db.core/*db* @rems.db.core/db-connection]
          (convert-to-dynamic/migrate-all-applications! new-workflow-id))))
    (= "test-data" (first args))
    (do
      (mount-nsd/start #'test-data/the-test-data)
      (binding [rems.db.core/*db* @rems.db.core/db-connection]
        (test-data/create-test-data!)))
    (= "demo-data" (first args))
    (do
      (mount-nsd/start #'test-data/the-test-data)
      (binding [rems.db.core/*db* @rems.db.core/db-connection]
        (test-data/create-demo-data!)))
    (= "validate" (first args))
    (do
      (mount-nsd/start #'rems.db.core/db-connection)
      (binding [rems.db.core/*db* @rems.db.core/db-connection]
        (when-not (validate/validate)
          (System/exit 2))))
    :else (start-app)))
