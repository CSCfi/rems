(ns ^:integration rems.db.test-workflow
  (:require [clojure.test :refer :all]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.workflow :as workflow]
            [rems.db.test-data-helpers :as test-helpers]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-get-all-workflow-roles
  (is (= nil (workflow/get-all-workflow-roles "anyone")))

  (testing "handler role"
    (test-helpers/create-user! {:eppn "handler-user"})
    (test-helpers/create-workflow! {:handlers ["handler-user"]})
    (is (= #{:handler} (workflow/get-all-workflow-roles "handler-user")))))
