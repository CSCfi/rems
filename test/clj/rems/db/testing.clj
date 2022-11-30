(ns rems.db.testing
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.service.dependencies :as dependencies]
            [rems.application.search]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.category :as category]
            [rems.db.core :as db]
            [rems.service.test-data :as test-data]
            [rems.db.user-mappings :as user-mappings]
            [rems.locales])
  (:import [org.joda.time Duration ReadableInstant]))

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
  (applications/empty-injections-cache!)
  (migrations/migrate ["migrate"] {:database-url (:test-database-url env)})
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
      (user-mappings/reset-cache!))))

(def +test-api-key+ test-data/+test-api-key+) ;; re-exported for convenience

(defn owners-fixture [f]
  (test-data/create-owners!)
  (f))

(defn rollback-db-fixture [f]
  (conman/with-transaction [db/*db* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*db*)
    (f)))
