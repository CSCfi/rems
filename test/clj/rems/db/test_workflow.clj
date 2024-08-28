(ns ^:integration rems.db.test-workflow
  (:require [clojure.test :refer :all]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.db.workflow]
            [rems.db.test-data-helpers :as test-helpers]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-get-all-workflow-roles
  (is (= #{} (rems.db.workflow/get-all-workflow-roles "anyone")))

  (testing "handler role"
    (test-helpers/create-user! {:userid "handler-user"})
    (test-helpers/create-workflow! {:handlers ["handler-user"]})
    (is (= #{:handler} (rems.db.workflow/get-all-workflow-roles "handler-user")))))

(deftest test-crud-workflow
  (testing "creating"
    (let [id (rems.db.workflow/create-workflow! {:userid "owner"
                                                 :organization {:organization/id "abc"}
                                                 :type :workflow/default
                                                 :title "test-crud-workflow-title"
                                                 :handlers ["bob" "handler"]
                                                 :forms []
                                                 :licenses []})]
      (is (number? id))
      (is (= {:archived false
              :organization {:organization/id "abc"}
              :title "test-crud-workflow-title"
              :workflow {:type :workflow/default
                         :handlers [{:userid "bob" :name nil :email nil}
                                    {:userid "handler" :name nil :email nil}]
                         :forms []
                         :licenses []}
              :id id
              :enabled true}
             (rems.db.workflow/get-workflow id)))

      (testing "editing"
        (rems.db.workflow/edit-workflow! {:id id
                                          :handlers ["bob" "handler" "alice"]})
        (is (= {:archived false
                :organization {:organization/id "abc"}
                :title "test-crud-workflow-title"
                :workflow {:type :workflow/default
                           :handlers [{:userid "bob" :name nil :email nil}
                                      {:userid "handler" :name nil :email nil}
                                      {:userid "alice" :name nil :email nil}]
                           :forms []
                           :licenses []}
                :id id
                :enabled true}
               (rems.db.workflow/get-workflow id)))))))
