(ns rems.db.testing
  (:require [clojure.java.jdbc :as jdbc]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.applications]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]))

(defn test-db-fixture [f]
  (mount/stop) ;; during interactive development, app might be running when tests start. we need to tear it down
  (mount/start-with-args {:test true}
                         #'rems.config/env
                         #'rems.db.core/*db*)
  (db/assert-test-database!)
  (migrations/migrate ["reset"] {:database-url (:test-database-url env)})
  (f)
  (mount/stop))

(defn caches-fixture [f]
  ;; no specific teardown. relies on the teardown of test-db-fixture.
  (mount/start #'rems.db.applications/application-cache
               #'rems.db.applications/all-applications-cache)
  (f))

(defn test-data-fixture [f]
  ;; no specific teardown for test-data-fixture. tests rely on the
  ;; setup of test-db-fixture or the teardown of rollback-db-fixture
  ;; to keep a clean db
  (test-data/create-test-data!)
  (f))

(defn rollback-db-fixture [f]
  (conman/with-transaction [db/*db* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*db*)
    (f)))
