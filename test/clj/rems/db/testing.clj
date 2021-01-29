(ns rems.db.testing
  (:require [clj-time.core :as time]
            [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.application.search]
            [rems.config :refer [env]]
            [rems.db.api-key :as api-key]
            [rems.db.applications]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
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
  (rems.db.applications/empty-injections-cache!)
  (migrations/migrate ["migrate"] {:database-url (:test-database-url env)})
  (f)
  (mount/stop))

(defn search-index-fixture [f]
  ;; no specific teardown. relies on the teardown of test-db-fixture.
  (mount/start #'rems.application.search/search-index)
  (f))

(defn caches-fixture [f]
  ;; no specific teardown. relies on the teardown of test-db-fixture.
  (mount/start #'rems.db.applications/all-applications-cache)
  (f))

(def +test-api-key+ test-data/+test-api-key+) ;; re-exported for convenience

(defn owners-fixture [f]
  (test-data/create-owners!)
  (f))

(defn rollback-db-fixture [f]
  (conman/with-transaction [db/*db* {:isolation :serializable}]
    (jdbc/db-set-rollback-only! db/*db*)
    (f)))
