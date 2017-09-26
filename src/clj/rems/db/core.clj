
(ns rems.db.core
  (:require [clj-time.jdbc] ;; convert db timestamps to joda-time objects
            [conman.core :as conman]
            [rems.env :refer [*db*]]))

(conman/bind-connection *db* "sql/queries.sql")

(defn assert-test-database! []
  (assert (= {:current_database "rems_test"}
             (get-database-name))))
