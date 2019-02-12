(ns ^:integration rems.test.db-localization
  (:require [clojure.test :refer :all]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.test.locales :refer [loc-en]]
            [rems.test.tempura :refer [with-fake-tempura]]
            [rems.text :refer [with-language localize-event localize-state]]
            [rems.workflow.dynamic :as dynamic]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'rems.config/env
     #'rems.db.core/*db*
     #'rems.locales/translations)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (f)
    (mount/stop)))

(defn- valid-localization? [str]
  (and (not (.contains str "Missing"))
       (not (.contains str "Unknown"))))

(deftest test-all-state-localizations
  (let [old-states (map :unnest (rems.db.core/get-application-states))]
    (is (= (-> (:states (:applications (:t loc-en)))
               (dissoc :unknown)
               (keys)
               (sort))
           (->> old-states
                (map keyword)
                (sort))))
    (is (= (-> (:dynamic-states (:applications (:t loc-en)))
               (dissoc :unknown)
               (keys)
               (sort))
           (->> dynamic/States
                (map name)
                (map keyword)
                (sort))))
    (with-language :en
      (fn []
        (is (not (valid-localization? (localize-state "foobar"))))
        (doseq [s (concat old-states dynamic/States)]
          (testing s
            (is (valid-localization? (localize-state s)))))))))

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
    (with-language :en
      (fn []
        (is (not (valid-localization? (localize-event "foobar"))))
        (doseq [e event-types]
          (testing e
            (is (valid-localization? (localize-event e)))))))))
