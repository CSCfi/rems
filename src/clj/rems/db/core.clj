(ns rems.db.core
  "Database connections. Exposes queries defined in resources/sql/queries.sql as functions using hugsql.
  That is, something like ':name get-catalogue-items' queries.sql, there will be a function
  rems.db.core/get-catalogue-items.

  See also: docs/architecture/012-layers.md"
  {:ns-tracker/resource-deps ["sql/queries.sql"]}
  (:require [clj-time.jdbc] ;; convert db timestamps to joda-time objects
            [clojure.java.jdbc]
            [clojure.string :as str]
            [conman.core :as conman]
            [mount.core :refer [defstate] :as mount]
            [rems.config :refer [env]]))

;; See docs/architecture/010-transactions.md
(defn- hikaricp-settings []
  ;; HikariConfig wants an actual String, no support for parameterized queries...
  (merge {:connection-init-sql
          (str/join [(when (:database-lock-timeout env)
                       (str "SET lock_timeout TO '" (:database-lock-timeout env) "';"))
                     (when (:database-idle-in-transaction-session-timeout env)
                       (str "SET idle_in_transaction_session_timeout TO '" (:database-idle-in-transaction-session-timeout env) "';"))])}
         (:hikaricp-extra-params env)))

(defstate ^:dynamic *db*
  :start (try (let [db (cond (:test (mount/args)) (conman/connect! (merge (hikaricp-settings)
                                                                          {:jdbc-url (:test-database-url env)}))
                             (:database-url env) (conman/connect! (merge (hikaricp-settings)
                                                                         {:jdbc-url (:database-url env)}))
                             ;; the jndi codepath doesn't use hikari, so we can't use +hikaricp-settings+
                             ;; TODO figure out another way to set lock_timeout in this case
                             (:database-jndi-name env) {:name (:database-jndi-name env)}
                             :else (throw (IllegalArgumentException. ":database-url or :database-jndi-name must be configured")))]
                ;; get a connection from the pool to get errors earlier
                (with-open [_ (clojure.java.jdbc/get-connection db)]
                  db))
              (catch Exception e
                (throw (IllegalArgumentException.
                        (str "Can not connect to database. "
                             "Check the :database-name and :database-jndi-name config variables. "
                             "The database might also be unreachable. ")
                        e))))
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(defn assert-test-database! []
  (assert (= {:current_database "rems_test"}
             (get-database-name))))
