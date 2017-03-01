(ns rems.test.db.core
  (:require [rems.db.core :refer [*db*] :as db]
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
      #'rems.db.core/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (db/create-test-data!)
    (f)
    (mount/stop)))

(deftest ^:integration test-get-catalogue-items
  (is (= ["A" "B"] (sort (map :title (db/get-catalogue-items))))))
