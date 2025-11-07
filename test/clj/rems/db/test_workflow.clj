(ns ^:integration rems.db.test-workflow
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.workflow]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.cache :as cache]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-get-all-workflow-roles
  (is (= #{} (rems.db.workflow/get-all-workflow-roles "anyone")))

  (testing "handler role"
    (test-helpers/create-user! {:userid "handler-user"})
    (test-helpers/create-workflow! {:handlers ["handler-user"]})
    (is (= #{:handler} (rems.db.workflow/get-all-workflow-roles "handler-user")))))

(deftest test-crud-workflow
  (let [expected-id (atom nil)]

    (testing "creating"
      (let [id (rems.db.workflow/create-workflow! {:userid "owner"
                                                   :organization {:organization/id "abc"}
                                                   :type :workflow/default
                                                   :title "test-crud-workflow-title"
                                                   :handlers ["bob" "handler"]
                                                   :forms []
                                                   :licenses []})
            _ (reset! expected-id id)]
        (is (number? id))
        (is (= {:archived false
                :organization {:organization/id "abc"}
                :title "test-crud-workflow-title"
                :workflow {:type :workflow/default
                           :handlers [{:userid "bob"}
                                      {:userid "handler"}]
                           :forms []
                           :licenses []}
                :id id
                :enabled true}
               (rems.db.workflow/get-workflow id)))))

    (testing "cache reload works"
      (cache/set-uninitialized! rems.db.workflow/workflow-cache)
      (is (= {@expected-id {:archived false
                            :organization {:organization/id "abc"}
                            :title "test-crud-workflow-title"
                            :workflow {:type :workflow/default
                                       :handlers [{:userid "bob"}
                                                  {:userid "handler"}]
                                       :forms []
                                       :licenses []}
                            :id @expected-id
                            :enabled true}}
             (into {} (cache/entries! rems.db.workflow/workflow-cache)))
          "cache contains the workflow"))

    (testing "editing"
      (rems.db.workflow/edit-workflow! {:id @expected-id
                                        :handlers ["bob" "handler" "alice"]})
      (is (= {:archived false
              :organization {:organization/id "abc"}
              :title "test-crud-workflow-title"
              :workflow {:type :workflow/default
                         :handlers [{:userid "bob"}
                                    {:userid "handler"}
                                    {:userid "alice"}]
                         :forms []
                         :licenses []}
              :id @expected-id
              :enabled true}
             (rems.db.workflow/get-workflow @expected-id)))

      (testing "archive status"
        (rems.db.workflow/set-archived! @expected-id true)
        (is (:archived (rems.db.workflow/get-workflow @expected-id))))

      (testing "enabled status"
        (rems.db.workflow/set-enabled! @expected-id false)
        (is (not (:enabled (rems.db.workflow/get-workflow @expected-id))))))))
