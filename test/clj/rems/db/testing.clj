(ns rems.db.testing
  (:require [clj-time.core :as time]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.application.search]
            [rems.config :refer [env]]
            [rems.db.applications]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.locales])
  (:import [org.joda.time Duration ReadableInstant]))

(defn test-db-fixture [f]
  (mount/stop) ;; during interactive development, app might be running when tests start. we need to tear it down
  (mount/start-with-args {:test true}
                         #'rems.config/env
                         #'rems.locales/translations
                         #'rems.db.core/*db*)
  (db/assert-test-database!)
  (migrations/migrate ["reset"] {:database-url (:test-database-url env)})
  (f)
  (mount/stop))

(defn search-index-fixture [f]
  ;; no specific teardown. relies on the teardown of test-db-fixture.
  (mount/start #'rems.application.search/search-index)
  (f))

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

(defn get-database-time []
  (:now (db/get-database-time)))

(defn sync-with-database-time
  "When the database runs on a different machine or VM than
   the application (e.g. Docker for Mac), the database clock may be
   ahead of the application clock, in which case newly created entities
   may be incorrectly flagged as expired, because their creation time
   seems to be in the future. This helper method can be called after
   creating such entities in flaky tests to guarantee that the application
   clock has caught up with the database clock."
  []
  (let [db-time (get-database-time)
        app-time (time/now)
        diff (.getMillis (Duration. ^ReadableInstant app-time ^ReadableInstant db-time))]
    (when (pos? diff)
      (log/info "The application clock is" diff "ms behind the database")
      (Thread/sleep (+ 1 diff)))))
