(ns rems.standalone
  "Run the REMS app in an embedded http server."
  (:require [rems.handler :as handler]
            [rems.db.core :as db]
            [luminus.repl-server :as repl]
            [luminus.http-server :as http]
            [luminus-migrations.core :as migrations]
            [rems.config :refer [env]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [mount.core :as mount])
  (:gen-class))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(mount/defstate ^{:on-reload :noop}
                http-server
                :start
                (http/start
                  (-> env
                      (assoc :handler handler/app)
                      (update :port #(or (-> env :options :port) %))))
                :stop
                (when http-server (http/stop http-server)))

(mount/defstate ^{:on-reload :noop}
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
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn repl-help []
  (println "Welcome to REMS!")
  (println "You can run the server with (start-app)"))

(defn -main
  "Arguments can be either arguments to mount/start-with-args, or one of
     \"migrate\" -- migrate database
     \"rollback\" -- roll back database migration
     \"populate\" -- insert test data into database"
  [& args]
  (cond
    (#{"migrate" "rollback"} (first args))
    (do
      (mount/start #'rems.config/env)
      (migrations/migrate args (select-keys env [:database-url])))
    (= "populate" (first args))
    (do
      (mount/start #'rems.config/env #'rems.env/*db*)
      (db/create-test-data!))
    :else
    (apply start-app args)))
