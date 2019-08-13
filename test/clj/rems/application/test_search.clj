(ns ^:integration rems.application.test-search
  (:require [clojure.test :refer :all]
            [rems.application.search :as search]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-application-search
  ;; unrelated application - it's an error if any of the tests finds this
  (test-data/create-application! {:actor "developer"})
  (search/refresh!)

  (testing "find by applicant"
    (let [app-id (test-data/create-application! {:actor "alice"})]
      (is (= #{app-id} (search/find-applications "alice")))))

  (testing "find by member")

  (testing "find by title")

  (testing "find by resource")

  (testing "find by state")

  (testing "find by form content")

  (testing "invalid query"
    (is (= nil (search/find-applications "+")))))
