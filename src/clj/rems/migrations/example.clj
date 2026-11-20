(ns rems.migrations.example
  "This is an example boilerplate for code-based migrations with Migratus
   It's a starting point for when event schema migrations are needed.
   This is referred from the resources/migrations/20190301085026-example.edn file.
   See https://github.com/yogthos/migratus#code-based-migrations for more information."
  (:require [next.jdbc :as jdbc]
            [rems.db.core :refer [*db*]]))

(defn migrate-up [config]
  (binding [*db* (:conn config)]
    (assert (= [{:test 1}] (jdbc/execute! *db* ["select 1 as test"])))))
