(ns ^:integration rems.test-migrations
  "Migration tests that use database."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [hugsql.core :as hugsql]
            [luminus-migrations.core]
            [luminus-migrations.util]
            [migratus.core]
            [rems.db.core]
            [rems.db.testing :refer [test-db-fixture reset-db-after-fixture rollback-db-fixture]]
            [rems.config]
            [rems.json :as json]
            [rems.migrations.event-public :as event-public]
            [rems.migrations.change-expiration-events]
            [rems.migrations.change-expiration-events-1]))

(use-fixtures
  :once
  test-db-fixture
  reset-db-after-fixture)

(use-fixtures
  :each
  rollback-db-fixture)

(defn- migratus-config []
  (let [db-url (:test-database-url rems.config/env)]
    {:store :database
     :db {:connection-uri (luminus-migrations.util/to-jdbc-uri db-url)}}))

(defn- rollback-until-just-before [migration-id]
  (let [config (migratus-config)]
    (migratus.core/migrate-until-just-before config migration-id)
    (migratus.core/rollback-until-just-after config migration-id)
    (migratus.core/rollback config)))

;; XXX: db-fn always returns seq unlike def-db-fns-from-string ?
(defn create-db-fn [sql]
  (partial (hugsql/db-fn sql) rems.db.core/*db*))

(deftest test-event-public
  (let [create-application!
        (create-db-fn
         "-- :name create-application! :insert
          INSERT INTO catalogue_item_application (id)
          VALUES (nextval ('catalogue_item_application_id_seq'))
          RETURNING id;
          ")
        add-application-event!
        (create-db-fn
         "-- :name add-application-event! :returning-execute :1
          INSERT INTO application_event (appId, eventData)
          VALUES (:application, :eventdata::jsonb)
          RETURNING id, eventData::TEXT;
          ")
        get-events #(->> (event-public/get-events rems.db.core/*db*)
                         (sort-by :id))]
    (testing "create test data"
      (rollback-until-just-before event-public/migration-id)
      (let [app (first (create-application!))
            _ (is (:id app))
            create-event #(do {:application (:id app) :eventdata (json/generate-string %)})]
        (is (:id (first (add-application-event! (create-event {:application/public true})))))
        (is (:id (first (add-application-event! (create-event {:application/public false})))))
        (is (:id (first (add-application-event! (create-event {:event/actor "alice"})))))
        (is (= [{:application/public true}
                {:application/public false}
                {:event/actor "alice"}]
               (->> (get-events)
                    (mapv (comp json/parse-string :eventdata)))))))
    (testing "migrate up"
      (event-public/migrate-up {:conn rems.db.core/*db*})
      (is (= [{:event/public true}
              {:event/public false}
              {:event/actor "alice"}]
             (->> (get-events)
                  (mapv (comp json/parse-string :eventdata))))))
    (testing "migrate down"
      (event-public/migrate-down {:conn rems.db.core/*db*})
      (is (= [{:application/public true}
              {:application/public false}
              {:event/actor "alice"}]
             (->> (get-events)
                  (mapv (comp json/parse-string :eventdata))))))))

(deftest test-change-expiration-events
  (let [create-application!
        (create-db-fn
         "-- :name create-application! :insert
          INSERT INTO catalogue_item_application (id)
          VALUES (nextval ('catalogue_item_application_id_seq'))
          RETURNING id;
          ")
        add-application-event!
        (create-db-fn
         "-- :name add-application-event! :returning-execute :1
          INSERT INTO application_event (appId, eventData)
          VALUES (:application, :eventdata::jsonb)
          RETURNING id, eventData::TEXT;
          ")
        get-events #(->> (rems.migrations.change-expiration-events/get-events rems.db.core/*db*)
                         (sort-by :id))
        previous-migration-id rems.migrations.change-expiration-events/migration-id]

    (testing "create test data"
      (rollback-until-just-before previous-migration-id)

      (let [app (first (create-application!))
            _ (is (:id app))
            create-event #(do {:application (:id app) :eventdata (json/generate-string %)})]
        (is (:id (first (add-application-event!
                         (create-event {:expires-on "2023-11-03T11:53:31.469Z"
                                        :event/time "2023-10-01T00:00:00.000Z"
                                        :last-activity "2023-10-27T11:53:31.469Z"})))))
        (is (:id (first (add-application-event!
                         (create-event {:expires-on "2100-01-01T00:00:00.000Z"
                                        :event/time "2099-10-01T00:00:00.000Z"})))))
        (is (:id (first (add-application-event!
                         (create-event {:event/actor "alice"})))))
        (is (= [{:expires-on "2023-11-03T11:53:31.469Z" :event/time "2023-10-01T00:00:00.000Z" :last-activity "2023-10-27T11:53:31.469Z"}
                {:expires-on "2100-01-01T00:00:00.000Z" :event/time "2099-10-01T00:00:00.000Z"}
                {:event/actor "alice"}]
               (->> (get-events)
                    (mapv (comp json/parse-string :eventdata)))))))

    (testing "migrate up"
      (rems.migrations.change-expiration-events/migrate-up {:conn rems.db.core/*db*}) ; does not change events but here for consistency
      (rems.migrations.change-expiration-events-1/migrate-up {:conn rems.db.core/*db*})
      (is (= [{:application/expires-on "2023-11-03T11:53:31.469Z" :event/time "2023-10-01T00:00:00.000Z"}
              {:application/expires-on "2100-01-01T00:00:00.000Z" :event/time "2099-10-01T00:00:00.000Z"}
              {:event/actor "alice"}]
             (->> (get-events)
                  (mapv (comp json/parse-string :eventdata))))))

    (testing "migrate down"
      (rems.migrations.change-expiration-events-1/migrate-down {:conn rems.db.core/*db*})
      (rems.migrations.change-expiration-events/migrate-down {:conn rems.db.core/*db*})
      (is (= [{:expires-on "2023-11-03T11:53:31.469Z" :event/time "2023-10-01T00:00:00.000Z" :last-activity "2023-10-01T00:00:00.000Z"}
              {:expires-on "2100-01-01T00:00:00.000Z" :event/time "2099-10-01T00:00:00.000Z" :last-activity "2099-10-01T00:00:00.000Z"}
              {:event/actor "alice"}]
             (->> (get-events)
                  (mapv (comp json/parse-string :eventdata))))))))

