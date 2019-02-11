(ns ^:integration rems.test.db-localization
  (:require [clojure.test :refer :all]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.test.locales :refer [loc-en]]
            [rems.test.tempura :refer [with-fake-tempura]]
            [rems.workflow.dynamic :as dynamic]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'rems.config/env
     #'rems.db.core/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (f)
    (mount/stop)))

(deftest test-all-state-localizations
  (is (= (-> (:states (:applications (:t loc-en)))
             (dissoc :unknown)
             (keys)
             (sort))
         (->> (rems.db.core/get-application-states)
              (map :unnest)
              (map keyword)
              (sort))))
  (with-fake-tempura
    (is (not (contains? (set (map rems.text/localize-state (map :unnest (rems.db.core/get-application-states))))
                        :t.applications.states/unknown)))))

(deftest test-all-event-localizations
  (is (= (-> (:events (:applications (:t loc-en)))
             (dissoc :unknown)
             (keys)
             (sort))
         (->> (applications/get-event-types)
              (map keyword)
              (sort))))
  (is (= (-> (:dynamic-events (:applications (:t loc-en)))
             (dissoc :unknown)
             (keys)
             (sort))
         (->> (dynamic/get-event-types)
              (map name)
              (map keyword)
              (sort))))
  (let [event-types (concat (applications/get-event-types)
                            (map name (dynamic/get-event-types)))]
    (with-fake-tempura
      (is (not (contains? (set (map rems.text/localize-event event-types))
                          :t.applications.events/unknown))))))
