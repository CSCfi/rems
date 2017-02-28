(ns rems.test.db.core
  (:require [rems.db.core :as db]
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
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (f)))

(deftest ^:integration test-get-catalogue-items
  (db/create-test-data!)
  (is (= ["A" "B"] (sort (map :title (db/get-catalogue-items))))))
