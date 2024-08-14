(ns rems.db.testing
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.application.search]
            [rems.config :refer [env]]
            [rems.db.applications]
            [rems.db.catalogue]
            [rems.db.category]
            [rems.db.core :as db]
            [rems.db.events]
            [rems.db.user-mappings]
            [rems.db.user-settings]
            [rems.locales]
            [rems.service.caches]
            [rems.service.dependencies]
            [rems.service.test-data :as test-data]))

(defn- reset-caches! []
  (rems.service.caches/reset-all-caches!)
  (rems.db.applications/reset-cache!)
  (rems.db.applications/empty-injections-cache!)
  (rems.db.catalogue/reset-cache!)
  (rems.db.category/reset-cache!)
  (rems.service.dependencies/reset-cache!)
  (rems.db.user-mappings/reset-cache!)
  (rems.db.events/empty-event-cache!))

(defn reset-db-after-fixture [f]
  (reset-caches!)
  (f)
  (migrations/migrate ["reset"] {:database-url (:test-database-url env)}))

(defn test-db-fixture [f]
  (mount/stop) ; during interactive development, app might be running when tests start. we need to tear it down
  (mount/start-with-args {:test true}
                         #'rems.config/env
                         #'rems.locales/translations
                         #'rems.db.core/*db*)
  (db/assert-test-database!)
  (migrations/migrate ["migrate"] {:database-url (:test-database-url env)})
  (mount/start #'rems.db.events/low-level-events-cache
               #'rems.db.user-settings/low-level-user-settings-cache
               #'rems.db.applications/all-applications-cache)
  (f))

(defn search-index-fixture [f]
  (mount/start #'rems.application.search/search-index)
  (f)
  (mount/stop #'rems.application.search/search-index))

(def +test-api-key+ test-data/+test-api-key+) ; re-exported for convenience

(defn owners-fixture [f]
  (test-data/create-owners!)
  (f))

(defn rollback-db-fixture [f]
  (reset-caches!)
  (conman/with-transaction [db/*db* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*db*)
    (f)))
