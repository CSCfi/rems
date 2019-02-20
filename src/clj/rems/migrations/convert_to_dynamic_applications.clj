(ns rems.migrations.convert-to-dynamic-applications
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [rems.db.core :as db :refer [*db*]]
            [rems.db.workflow :as workflow]))

(defn- use-db [jdbc-url f]
  (binding [*db* (conman/connect! {:jdbc-url jdbc-url})]
    (try
      (f)
      (finally
        (conman/disconnect! *db*)))))

(defn migrate-up [config]
  (prn 'migrate-up config)
  (use-db (get-in config [:db :connection-uri])
          (fn []
            (println "workflows:" (count (workflow/get-workflows {})))
            (jdbc/with-db-connection [conn *db*]
              (println "applications:" (:count (first (jdbc/query conn ["select count(1) from catalogue_item_application"]))))))))
