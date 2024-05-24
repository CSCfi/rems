(ns rems.db.testing
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.application.search]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.category :as category]
            [rems.db.core :as db]
            [rems.db.events :as events]
            [rems.db.user-mappings :as user-mappings]
            [rems.db.user-settings]
            [rems.locales]
            [rems.service.dependencies :as dependencies]
            [rems.service.test-data :as test-data]))

(defn reset-db-fixture [f]
  (try
    (f)
    (finally
      (migrations/migrate ["reset"] {:database-url (:test-database-url env)}))))

(defn test-db-fixture [f]
  (mount/stop) ;; during interactive development, app might be running when tests start. we need to tear it down
  (mount/start-with-args {:test true}
                         #'rems.config/env
                         #'rems.locales/translations
                         #'rems.db.core/*db*)
  (db/assert-test-database!)

  ;; these are db level caches and tests use db rollback
  ;; it's best for us to start from scratch here
  (applications/empty-injections-cache!)

  (migrations/migrate ["migrate"] {:database-url (:test-database-url env)})
  ;; need DB to start these
  (mount/start #'rems.db.events/low-level-events-cache
               #'rems.db.user-settings/low-level-user-settings-cache)
  (f)
  (mount/stop))

(defn search-index-fixture [f]
  ;; no specific teardown. relies on the teardown of test-db-fixture.
  (mount/start #'rems.application.search/search-index)
  (f))

(defn reset-caches-fixture [f]
  (try
    (mount/start #'applications/all-applications-cache)
    (f)
    (finally
      (applications/reset-cache!)
      (catalogue/reset-cache!)
      (category/reset-cache!)
      (dependencies/reset-cache!)
      (user-mappings/reset-cache!)
      (events/empty-event-cache!))))
(def +test-api-key+ test-data/+test-api-key+) ;; re-exported for convenience

(defn owners-fixture [f]
  (test-data/create-owners!)
  (f))

(defn rollback-db-fixture [f]
  (conman/with-transaction [db/*db* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*db*)
    (events/empty-event-cache!) ; NB can't rollback this cache so reset
    (f)))
