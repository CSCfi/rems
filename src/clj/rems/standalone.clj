(ns rems.standalone
  "Run the REMS app in an embedded http server."
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [luminus-migrations.core :as migrations]
            [luminus.http-server :as http]
            [luminus.repl-server :as repl]
            [mount.core :as mount]
            [rems.application.search :as search]
            [rems.config :refer [env]]
            [rems.db.api-key :as api-key]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.test-data :as test-data]
            [rems.db.users :as users]
            [rems.handler :as handler]
            [rems.json :as json]
            [rems.validate :as validate])
  (:import [sun.misc Signal SignalHandler])
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

(defn- refresh-caches []
  (log/info "Refreshing caches")
  (applications/refresh-all-applications-cache!)
  (search/refresh!)
  (log/info "Caches refreshed"))

(defn start-app [& args]
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app))
  (validate/validate)
  (refresh-caches))

;; The default of the JVM is to exit with code 128+signal. However, we
;; shut down gracefully on SIGINT and SIGTERM due to the exit hooks
;; mount has installed. Thus exit code 0 is the right choice. This
;; also makes REMS standalone work nicely with systemd: by default it
;; uses SIGTERM to stop services and expects a succesful exit.
(defn exit-on-signals! []
  (let [exit (proxy [SignalHandler] []
               (handle [sig]
                 (log/info "Shutting down due to signal" (.getName sig))
                 (System/exit 0)))]
    (Signal/handle (Signal. "INT") exit) ;; e.g. ^C from terminal
    (Signal/handle (Signal. "TERM") exit) ;; default kill signal of systemd
    nil))

(defn -main
  "Arguments can be either arguments to mount/start-with-args, or one of
     \"migrate\" -- migrate database
     \"rollback\" -- roll back database migration
     \"reset\" -- empties database and runs migrations to empty db
     \"test-data\" -- insert test data into database
     \"demo-data\" -- insert data for demoing purposes into database
     \"validate\" -- validate data in db
     \"list-users\" -- list users and roles
     \"grant-role <role> <user>\" -- grant a role to a user
     \"add-api-key <api-key> [<description>]\" -- add api key to db.
        <description> is an optional text comment.
        If a pre-existing <api-key> is given, update description for it."
  [& args]
  (exit-on-signals!)
  (case (first args)
    ("migrate" "rollback")
    (do
      (mount/start #'rems.config/env)
      (migrations/migrate args (select-keys env [:database-url])))

    "reset"
    (do
      (println "\n\n*** Are you absolutely sure??? Reset empties the whole database and runs migrations to empty db.***\nType 'YES' to proceed")
      (when (= "YES" (read-line))
        (do
          (println "Running reset")
          (mount/start #'rems.config/env)
          (migrations/migrate args (select-keys env [:database-url])))))

    "test-data"
    (do
      (mount/start #'rems.config/env
                   #'rems.db.core/*db*
                   #'rems.locales/translations)
      (log/info "Creating test data")
      (test-data/create-test-data!)
      (test-data/create-performance-test-data!)
      (log/info "Test data created"))

    "demo-data"
    (do
      (mount/start #'rems.config/env
                   #'rems.db.core/*db*
                   #'rems.locales/translations)
      (test-data/create-demo-data!))

    "add-api-key"
    (let [[_ key comment] args]
      (mount/start #'rems.config/env #'rems.db.core/*db*)
      (api-key/add-api-key! key comment)
      (log/info "Api key added"))

    "list-users"
    (do
      (mount/start #'rems.config/env #'rems.db.core/*db*)
      (doseq [u (users/get-all-users)]
        (-> u
            (assoc :roles (roles/get-roles (:userid u)))
            json/generate-string
            println)))

    "grant-role"
    (let [[_ role user] args]
      (if (not (and role user))
        (println "Usage: grant-role <role> <user>")
        (do (mount/start #'rems.config/env #'rems.db.core/*db*)
            (roles/add-role! user (keyword role)))))

    "validate"
    (do
      (mount/start #'rems.config/env #'rems.db.core/*db*)
      (when-not (validate/validate)
        (System/exit 2)))

    ;; default
    (apply start-app args)))
