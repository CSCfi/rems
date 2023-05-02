(ns rems.main
  "Run REMS CLI functions including the embedded HTTP server. Available CLI functions
   are described in more detail by rems.main/-main docstring."
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [luminus-migrations.core :as migrations]
            [luminus.http-server :as http]
            [luminus.repl-server :as repl]
            [medley.core :refer [find-first]]
            [mount.core :as mount]
            [rems.service.ega :as ega]
            [rems.application.search :as search]
            [rems.common.git :as git]
            [rems.config :refer [env]]
            [rems.db.api-key :as api-key]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.service.fix-userid]
            [rems.db.roles :as roles]
            [rems.service.test-data :as test-data]
            [rems.db.users :as users]
            [rems.handler :as handler]
            [rems.json :as json]
            [rems.validate :as validate])
  (:import [sun.misc Signal SignalHandler]
           [org.eclipse.jetty.server.handler.gzip GzipHandler])
  (:refer-clojure :exclude [parse-opts])
  (:gen-class))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(defn- jetty-configurator [server]
  (let [pool (.getThreadPool server)]
    (.setName pool "jetty")
    (.setHandler server
                 (doto (GzipHandler.)
                   (.setIncludedMimeTypes (into-array ["text/css"
                                                       "text/plain"
                                                       "text/javascript"
                                                       "application/javascript"
                                                       "application/json"
                                                       "application/transit+json"
                                                       "image/x-icon"
                                                       "image/svg+xml"]))
                   (.setMinGzipSize 1024)
                   (.setHandler (.getHandler server))))

    server))

(mount/defstate
  ^{:on-reload :noop}
  http-server
  :start
  (http/start (merge {:handler handler/handler
                      :send-server-version? false
                      :port (:port env)
                      :configurator jetty-configurator}
                     (when-not (:port env)
                       {:http? false})
                     (when (:ssl-port env)
                       {:ssl? true
                        :ssl-port (:ssl-port env)
                        :keystore (:ssl-keystore env)
                        :key-password (:ssl-keystore-password env)})
                     (:jetty-extra-params env)))
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
     \"run\" -- start the REMS application
     \"help\" -- show this help
     \"migrate\" -- migrate database
     \"rollback\" -- roll back database migration
     \"reset\" -- empties database and runs migrations to empty db
     \"test-data\" -- insert test data into database
     \"demo-data\" -- insert data for demoing purposes into database
     \"validate\" -- validate data in db
     \"list-users\" -- list users and roles
     \"grant-role <role> <user>\" -- grant a role to a user
     \"remove-role <role> <user>\" -- remove a role from a user
     \"api-key get\" -- list all api keys
     \"api-key get <api-key>\" -- get details of api key
     \"api-key add <api-key> [<description>]\" -- add api key to db.
        <description> is an optional text comment.
        If a pre-existing <api-key> is given, update description for it.
     \"api-key delete <api-key>\" -- remove api key from db.
     \"api-key set-users <api-key> [<uid1> <uid2> ...]\" -- set allowed users for api key
        An empty set of users means all users are allowed.
        Adds the api key if it doesn't exist.
     \"api-key allow <api-key> <method> <regex>\" -- add an entry to the allowed method/path whitelist
        The special method `any` means any method.
        The regex is a (Java) regular expression that should match the whole path of the request.
        Example regex: /api/applications/[0-9]+/?
     \"api-key allow-all <api-key>\" -- clears the allowed method/path whitelist.
        An empty list means all methods and paths are allowed.
     \"ega api-key <userid> <username> <password> <config-id>\" -- generate a new API-Key for the user using EGA login
     \"rename-user <old-userid> <new-userid>\" -- change a user's identity from old to new"
  [& args]
  (exit-on-signals!)
  (log/info "REMS" git/+version+)
  (let [usage #(do
                 (println "Usage:")
                 (println (:doc (meta #'-main))))]
    (if (empty? args)
      ;; start app by default if no CLI command was supplied
      (apply start-app args)
      (case (first args)
        "run"
        (apply start-app args)

        "help"
        (do
          (usage)
          (System/exit 0))

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

        "api-key"
        (let [[_ command api-key & command-args] args]
          (mount/start #'rems.config/env #'rems.db.core/*db*)
          (case command
            "get" (do)
            "add" (api-key/update-api-key! api-key {:comment (str/join " " command-args)})
            "delete" (api-key/delete-api-key! api-key)
            "set-users" (api-key/update-api-key! api-key {:users command-args})
            "allow" (let [[method path] command-args
                          entry {:method method :path path}
                          old (:paths (api-key/get-api-key api-key))]
                      (api-key/update-api-key! api-key {:paths (conj old entry)}))
            "allow-all" (api-key/update-api-key! api-key {:paths nil})
            (do (usage)
                (System/exit 1)))
          (if api-key
            (prn (api-key/get-api-key api-key))
            (mapv prn (api-key/get-api-keys))))

        "ega"
        (let [[_ command userid username password config-id & _] args]
          (mount/start #'rems.config/env #'rems.db.core/*db*)
          (case command
            "api-key" (let [ega-config (->> (:entitlement-push env)
                                            (filter (comp #{:ega} :type))
                                            (find-first (comp #{config-id} :id)))]
                        (assert ega-config (str "Could not find :entitlement-push with :type :ega and :id " (pr-str config-id)))
                        (ega/generate-api-key-with-account {:userid userid :username username :password password :config ega-config}))
            (do (usage)
                (System/exit 1))))

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
            (do (usage)
                (System/exit 1))
            (do (mount/start #'rems.config/env #'rems.db.core/*db*)
                (roles/add-role! user (keyword role)))))

        "remove-role"
        (let [[_ role user] args]
          (if (not (and role user))
            (do (usage)
                (System/exit 1))
            (do (mount/start #'rems.config/env #'rems.db.core/*db*)
                (roles/remove-role! user (keyword role)))))

        "validate"
        (do
          (mount/start #'rems.config/env #'rems.db.core/*db*)
          (when-not (validate/validate)
            (System/exit 2)))

        "rename-user"
        (let [[_ old-userid new-userid] args]
          (if (not (and old-userid new-userid))
            (do (usage)
                (System/exit 1))
            (do (println "\n\n*** Renaming a user's identity can't easily be undone. ***\nType 'YES' to proceed or anything else to run a simulation only.")
                (let [simulate? (not= "YES" (read-line))]
                  (println (if simulate? "Simulating only..." "Renaming..."))
                  (mount/start #'rems.config/env #'rems.db.core/*db*)
                  (rems.service.fix-userid/fix-all old-userid new-userid simulate?)
                  (println "Finished.\n\nConsider rebooting the server process next to refresh all the caches, most importantly the application cache.")))))

        (do
          (println "Unrecognized argument:" (first args))
          (usage)
          (System/exit 1))))))
