(ns ^:integration rems.test-db-localization
  (:require [clojure.test :refer :all]
            [luminus-migrations.core :as migrations]
            [mount.extensions.namespace-deps :as mount-nsd]
            [mount.lite :as mount]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.test-locales :refer [loc-en]]
            [rems.testing-tempura :refer [with-fake-tempura]]
            [rems.text :refer [with-language localize-event localize-state]]
            [rems.workflow.dynamic :as dynamic]))

(use-fixtures
  :each
  (fn [f]
    (mount-nsd/start #'rems.locales/tempura-config)
    (f)
    (mount-nsd/stop)))

(defn- valid-localization? [str]
  (and (not (.contains str "Missing"))
       (not (.contains str "Unknown"))))

(deftest test-all-state-localizations
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
      (doseq [s dynamic/States]
        (testing s
          (is (valid-localization? (localize-state s))))))))

(deftest test-all-event-localizations
  (is (= (-> (:dynamic-events (:applications (:t loc-en)))
             (dissoc :unknown)
             (keys)
             (sort))
         (->> (dynamic/get-event-types)
              (map name)
              (map keyword)
              (sort))))
  (with-language :en
    (fn []
      (is (not (valid-localization? (localize-event "foobar"))))
      (doseq [event-type (dynamic/get-event-types)]
        (testing event-type
          (is (valid-localization? (localize-event event-type))))))))
