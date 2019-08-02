(ns rems.standalone
  "Run the REMS app in an embedded http server."
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [luminus-migrations.core :as migrations]
            [luminus.http-server :as http]
            [luminus.repl-server :as repl]
            [mount.core :as mount]
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
  ^{:on-reload :noop}
  http-server
  :start
  (http/start (assoc env :handler handler/handler))
  :stop
  (when http-server (http/stop http-server)))

(mount/defstate
  ^{:on-reload :noop}
  repl-server
  :start
  (when-let [nrepl-port (env :nrepl-port)]
    (repl/start {:port nrepl-port}))
  :stop
  (when repl-server
    (repl/stop repl-server)))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped")))

(defn start-app [& args]
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app))
  (validate/validate))

(defn -main
  "Arguments can be either arguments to mount/start-with-args, or one of
     \"migrate\" -- migrate database
     \"rollback\" -- roll back database migration
     \"reset\" -- empties database and runs migrations to empty db
     \"test-data\" -- insert test data into database
     \"demo-data\" -- insert data for demoing purposes into database
     \"validate\" -- validate data in db"
  [& args]
  (cond
    (#{"migrate" "rollback"} (first args))
    (do
      (mount/start #'rems.config/env)
      (migrations/migrate args (select-keys env [:database-url])))
    (= "reset" (first args))
    (do
      (println "\n\n*** Are you absolutely sure??? Reset empties the whole database and runs migrations to empty db.***\nType 'YES' to proceed")
      (when (= "YES" (read-line))
        (do
          (println "Running reset")
          (mount/start #'rems.config/env)
          (migrations/migrate args (select-keys env [:database-url])))))
    (= "test-data" (first args))
    (do
      (mount/start #'rems.config/env #'rems.db.core/*db*)
      (log/info "Creating test data")
      (test-data/create-test-data!)
      (test-data/create-performance-test-data!)
      (log/info "Test data created"))
    (= "demo-data" (first args))
    (do
      (mount/start #'rems.config/env #'rems.db.core/*db*)
      (test-data/create-demo-data!))
    (= "validate" (first args))
    (do
      (mount/start #'rems.config/env #'rems.db.core/*db*)
      (when-not (validate/validate)
        (System/exit 2)))
    :else
    (apply start-app args)))
