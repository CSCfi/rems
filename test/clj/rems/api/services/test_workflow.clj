(ns ^:integration rems.api.services.test-workflow
  (:require [clojure.test :refer :all]
            [rems.api.services.workflow :as workflow]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [caches-fixture rollback-db-fixture test-db-fixture]]
            [rems.testing-util :refer [with-user]]))

(use-fixtures :once test-db-fixture caches-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- create-users []
  (test-data/create-user! {:eppn "user1" :commonName "User 1" :mail "user1@example.com"})
  (test-data/create-user! {:eppn "user2" :commonName "User 2" :mail "user2@example.com"})
  (test-data/create-user! {:eppn "owner" :commonName "owner" :mail "owner@example.com"} :owner))

(deftest test-create-workflow
  (create-users)

  (with-user "owner"
    (test-data/create-organization! {:organization/id "abc" :organization/name "ABC"})
    (testing "default workflow with forms"
      (let [form-id (test-data/create-form! {:form/title "workflow form"
                                             :form/fields [{:field/type :text
                                                            :field/title {:fi "fi" :sv "sv" :en "en"}
                                                            :field/optional true}]})
            wf-id (test-data/create-workflow! {:organization {:organization/id "abc"}
                                               :type :workflow/default
                                               :title "the title"
                                               :handlers ["user1" "user2"]
                                               :forms [{:form/id form-id}]})]
        (is (= {:id wf-id
                :organization {:organization/id "abc" :organization/name "ABC"}
                :title "the title"
                :workflow {:type :workflow/default
                           :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}
                                      {:userid "user2" :name "User 2" :email "user2@example.com"}]
                           :forms [{:form/id form-id :form/title "workflow form"}]}
                :licenses []
                :owneruserid "owner"
                :modifieruserid "owner"
                :enabled true
                :archived false}
               (workflow/get-workflow wf-id)))))

    (testing "decider workflow"
      (let [wf-id (test-data/create-workflow! {:organization {:organization/id "abc"}
                                               :type :workflow/decider
                                               :title "the title"
                                               :handlers ["user1" "user2"]})]
        (is (= {:id wf-id
                :organization {:organization/id "abc" :organization/name "ABC"}
                :title "the title"
                :workflow {:type :workflow/decider
                           :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}
                                      {:userid "user2" :name "User 2" :email "user2@example.com"}]
                           :forms []}
                :licenses []
                :owneruserid "owner"
                :modifieruserid "owner"
                :enabled true
                :archived false}
               (workflow/get-workflow wf-id)))))

    (testing "master workflow"
      (let [wf-id (test-data/create-workflow! {:organization {:organization/id "abc"}
                                               :type :workflow/master
                                               :title "the title"
                                               :handlers ["user1" "user2"]})]
        (is (= {:id wf-id
                :organization {:organization/id "abc" :organization/name "ABC"}
                :title "the title"
                :workflow {:type :workflow/master
                           :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}
                                      {:userid "user2" :name "User 2" :email "user2@example.com"}]
                           :forms []}
                :licenses []
                :owneruserid "owner"
                :modifieruserid "owner"
                :enabled true
                :archived false}
               (workflow/get-workflow wf-id)))))))

(deftest test-edit-workflow
  (create-users)

  (with-user "owner"
    (test-data/create-organization! {:organization/id "abc" :organization/name "ABC"})
    (testing "change title"
      (let [wf-id (test-data/create-workflow! {:organization {:organization/id "abc"}
                                               :type :workflow/master
                                               :title "original title"
                                               :handlers ["user1"]})]
        (workflow/edit-workflow! {:id wf-id
                                  :title "changed title"})
        (is (= {:id wf-id
                :title "changed title"
                :workflow {:type :workflow/master
                           :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}]
                           :forms []}}
               (-> (workflow/get-workflow wf-id)
                   (select-keys [:id :title :workflow]))))))

    (testing "change handlers"
      (let [wf-id (test-data/create-workflow! {:organization {:organization/id "abc"}
                                               :type :workflow/master
                                               :title "original title"
                                               :handlers ["user1"]})]
        (workflow/edit-workflow! {:id wf-id
                                  :handlers ["user2"]})
        (is (= {:id wf-id
                :title "original title"
                :workflow {:type :workflow/master
                           :handlers [{:userid "user2" :name "User 2" :email "user2@example.com"}]
                           :forms []}}
               (-> (workflow/get-workflow wf-id)
                   (select-keys [:id :title :workflow]))))))))


(deftest test-get-workflow
  (create-users)
  (with-user "owner"
    (testing "not found"
      (is (nil? (workflow/get-workflow 123))))))

(deftest test-get-handlers
  (create-users)
  (with-user "owner"
    (let [simplify #(map :userid %)
          wf1 (test-data/create-workflow! {:type :workflow/default
                                           :title "workflow2"
                                           :handlers ["handler1"
                                                      "handler2"]})
          wf2 (test-data/create-workflow! {:type :workflow/default
                                           :title "workflow2"
                                           :handlers ["handler2"
                                                      "handler3"]})]

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
               (simplify (workflow/get-handlers))))))))
