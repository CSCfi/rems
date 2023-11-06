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
            [medley.core :refer [distinct-by remove-vals]]
            [mount.core :refer [defstate] :as mount]
            [rems.config :refer [env]]
            [rems.context]
            [rems.common.util :refer [conj-vec]]))

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

(def db-access-log (atom nil))

(defn rems-stacktrace []
  (->> (Exception. "capture REMS stacktrace")
       clojure.stacktrace/print-stack-trace
       with-out-str
       str/split-lines
       (filter #(str/includes? % "rems."))))

(defn wrap-query [id query]
  (fn [& args]
    (let [request (when (bound? #'rems.context/*request*)
                    rems.context/*request*)]
      (swap! db-access-log
             conj-vec
             (remove-vals nil?
                          {:request-id (:request-id request)
                           :db-fn id
                           :request-method (:request-method request)
                           :url (str (:uri request) "?" (:query-string request))
                           :params (:params request)
                           :trace (rems-stacktrace)})))
    (apply query args)))

(defmacro bind-connection* [conn & filenames]
  `(let [{snips# :snips fns# :fns :as queries#} (conman.core/load-queries ~@filenames)]
     (doseq [[id# {fn# :fn meta# :meta}] snips#]
       (conman.core/intern-fn *ns* id# meta# fn#))
     (doseq [[id# {query# :fn meta# :meta}] fns#
             :let [wrapped-query# (wrap-query id# query#)]]
       (conman.core/intern-fn *ns* id# meta#
                              (fn f#
                                ([] (wrapped-query# ~conn {}))
                                ([params#] (wrapped-query# ~conn params#))
                                ([conn# params# & args#] (apply wrapped-query# conn# params# args#)))))
     queries#))

;;(bind-connection* *db* "sql/queries.sql")
(conman/bind-connection *db* "sql/queries.sql")

(comment
  {:requests [(count (distinct-by :request-id @db-access-log)) (mapv (juxt :request-method :url) (distinct-by :request-id @db-access-log))]
   :apis [(count (distinct-by :url @db-access-log)) (mapv :url (distinct-by :url @db-access-log))]
   :queries [(count @db-access-log) (frequencies (map :db-fn @db-access-log))]
   :state @db-access-log})

(defn assert-test-database! []
  (assert (= {:current_database "rems_test"}
             (get-database-name))))
