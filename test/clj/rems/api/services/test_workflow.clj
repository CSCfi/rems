(ns ^:integration rems.api.services.test-workflow
  (:require [clojure.test :refer :all]
            [rems.api.services.workflow :as workflow]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [caches-fixture rollback-db-fixture test-db-fixture]]))

(use-fixtures :once test-db-fixture caches-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-create-workflow
  (test-data/create-user! {:eppn "user1" :commonName "User 1" :mail "user1@example.com"})
  (test-data/create-user! {:eppn "user2" :commonName "User 2" :mail "user2@example.com"})

  (testing "default workflow"
    (let [wf-id (:id (workflow/create-workflow! {:user-id "creator"
                                                 :organization "org"
                                                 :type :workflow/default
                                                 :title "the title"
                                                 :handlers ["user1" "user2"]}))]
      (is (= {:id wf-id
              :organization "org"
              :title "the title"
              :workflow {:type :workflow/default
                         :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}
                                    {:userid "user2" :name "User 2" :email "user2@example.com"}]}
              :licenses []
              :owneruserid "creator"
              :modifieruserid "creator"
              :enabled true
              :archived false}
             (workflow/get-workflow wf-id)))))

  (testing "decider workflow"
    (let [wf-id (:id (workflow/create-workflow! {:user-id "creator"
                                                 :organization "org"
                                                 :type :workflow/decider
                                                 :title "the title"
                                                 :handlers ["user1" "user2"]}))]
      (is (= {:id wf-id
              :organization "org"
              :title "the title"
              :workflow {:type :workflow/decider
                         :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}
                                    {:userid "user2" :name "User 2" :email "user2@example.com"}]}
              :licenses []
              :owneruserid "creator"
              :modifieruserid "creator"
              :enabled true
              :archived false}
             (workflow/get-workflow wf-id)))))

  (testing "master workflow"
    (let [wf-id (:id (workflow/create-workflow! {:user-id "creator"
                                                 :organization "org"
                                                 :type :workflow/master
                                                 :title "the title"
                                                 :handlers ["user1" "user2"]}))]
      (is (= {:id wf-id
              :organization "org"
              :title "the title"
              :workflow {:type :workflow/master
                         :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}
                                    {:userid "user2" :name "User 2" :email "user2@example.com"}]}
              :licenses []
              :owneruserid "creator"
              :modifieruserid "creator"
              :enabled true
              :archived false}
             (workflow/get-workflow wf-id))))))

(deftest test-edit-workflow
  (test-data/create-user! {:eppn "user1" :commonName "User 1" :mail "user1@example.com"})
  (test-data/create-user! {:eppn "user2" :commonName "User 2" :mail "user2@example.com"})

  (testing "change title"
    (let [wf-id (:id (workflow/create-workflow! {:user-id "creator"
                                                 :organization "org"
                                                 :type :workflow/master
                                                 :title "original title"
                                                 :handlers ["user1"]}))]
      (workflow/edit-workflow! {:id wf-id
                                :title "changed title"})
      (is (= {:id wf-id
              :title "changed title"
              :workflow {:type :workflow/master
                         :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}]}}
             (-> (workflow/get-workflow wf-id)
                 (select-keys [:id :title :workflow]))))))

  (testing "change handlers"
    (let [wf-id (:id (workflow/create-workflow! {:user-id "creator"
                                                 :organization "org"
                                                 :type :workflow/master
                                                 :title "original title"
                                                 :handlers ["user1"]}))]
      (workflow/edit-workflow! {:id wf-id
                                :handlers ["user2"]})
      (is (= {:id wf-id
              :title "original title"
              :workflow {:type :workflow/master
                         :handlers [{:userid "user2" :name "User 2" :email "user2@example.com"}]}}
             (-> (workflow/get-workflow wf-id)
                 (select-keys [:id :title :workflow])))))))


(deftest test-get-workflow
  (testing "not found"
    (is (nil? (workflow/get-workflow 123)))))

(deftest test-get-handlers
  (let [simplify #(map :userid %)
        wf1 (:id (workflow/create-workflow! {:user-id "owner"
                                             :organization ""
                                             :type :workflow/default
                                             :title "workflow2"
                                             :handlers ["handler1"
                                                        "handler2"]}))
        wf2 (:id (workflow/create-workflow! {:user-id "owner"
                                             :organization ""
                                             :type :workflow/default
                                             :title "workflow2"
                                             :handlers ["handler2"
                                                        "handler3"]}))]

    (testing "returns distinct handlers from all workflows"
      (is (= ["handler1" "handler2" "handler3"]
             (simplify (workflow/get-handlers)))))

    (testing "ignores disabled workflows"
      (workflow/set-workflow-enabled! {:id wf1 :enabled false})
      (is (= ["handler2" "handler3"]
             (simplify (workflow/get-handlers)))))

    (testing "ignores archived workflows"
      (workflow/set-workflow-archived! {:id wf2 :archived true})
      (is (= []
             (simplify (workflow/get-handlers)))))))
