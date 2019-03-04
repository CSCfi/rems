(ns rems.migrations.example
  (:require [clojure.java.jdbc :as jdbc]
            [rems.db.core :refer [*db*]]
            [rems.db.workflow :as workflow]))

(defn migrate-up [config]
  (binding [*db* (:conn config)]
    (assert (= [{:test 1}] (jdbc/query *db* ["select 1 as test"])))
    (assert (workflow/get-workflows {}))))
