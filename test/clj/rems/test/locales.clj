(ns ^:integration rems.test.locales
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.core :as db]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'rems.config/env
     #'rems.env/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (f)
    (mount/stop)))

(def loc-fi (read-string (slurp (io/resource "translations/en-GB.edn"))))

(def loc-en (read-string (slurp (io/resource "translations/fi.edn"))))

(defn map-structure
  "Recurse into map m and replace all leaves with true."
  [m]
  (let [transform (fn [v] (if (map? v) (map-structure v) true))]
    (reduce-kv (fn [m k v] (assoc m k (transform v))) {} m)))

(deftest test-all-languages-defined
  (is (= (map-structure loc-en)
         (map-structure loc-fi))))

(deftest test-all-state-localizations
  (is (= (-> (:states (:applications (:t loc-en)))
             (dissoc :unknown)
             (keys)
             (sort))
         (->> (rems.db.core/get-application-states)
              (map :unnest)
              (map keyword)
              (sort)))))

(deftest test-all-event-localizations
  (is (= (-> (:events (:applications (:t loc-en)))
             (dissoc :unknown)
             (keys)
             (sort))
         (->> (rems.db.core/get-application-event-types)
              (map :unnest)
              (map keyword)
              (sort)))))
