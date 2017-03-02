(ns rems.test.db.core
  (:require [rems.db.core :as db]
            [rems.contents :as contents]
            [rems.env :refer [*db*]]
            [luminus-migrations.core :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [rems.config :refer [env]]
            [mount.core :as mount]))

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

(deftest ^:integration test-get-catalogue-items
  (is (empty? (get (contents/catalogue) 2)))
  (db/create-test-data!)
  (is (seq (get (contents/catalogue) 2)))
  (is (= ["A" "B"] (map #(last (nth % 1)) (get (rems.contents/catalogue) 2))))
  (is (= 2 (count (get (rems.contents/catalogue) 2))))
  (db/create-test-data!)
  (is (= 4 (count (get (rems.contents/catalogue) 2)))))
