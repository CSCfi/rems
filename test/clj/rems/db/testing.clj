(ns rems.db.testing
  (:require [clojure.java.jdbc :as jdbc]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]))

(defn test-db-fixture [f]
  (mount/start-with-args {:test true}
                         #'rems.config/env
                         #'rems.db.core/*db*)
  (db/assert-test-database!)
  (migrations/migrate ["reset"] {:database-url (:test-database-url env)})
  (f)
  (mount/stop))

(defn test-data-fixture [f]
  (test-data/create-test-data!)
  ;; XXX no teardown!
  (f))

(defn rollback-db-fixture [f]
  (conman/with-transaction [db/*db* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*db*)
    (f)))
