(ns ^:integration rems.service.test-workflow
  (:require [clojure.test :refer :all]
            [rems.db.applications :as applications]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.service.workflow :as workflow]
            [rems.testing-util :refer [with-user]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- create-users []
  (test-helpers/create-user! {:userid "alice" :name "Alice Applicant" :email "alice@example.com"})
  (test-helpers/create-user! {:userid "user1" :name "User 1" :email "user1@example.com"})
  (test-helpers/create-user! {:userid "user2" :name "User 2" :email "user2@example.com"})
  (test-helpers/create-user! {:userid "user3" :name "User 3" :email "user3@example.com"})
  (test-helpers/create-user! {:userid "owner" :name "owner" :email "owner@example.com"} :owner))

(deftest test-create-workflow
  (create-users)

  (with-user "owner"
    (test-helpers/create-organization! {:organization/id "abc" :organization/name {:en "ABC"} :organization/short-name {:en "ABC"}})
    (testing "default workflow with forms"
      (let [form-id (test-helpers/create-form! {:form/internal-name "workflow form"
                                                :form/external-title {:en "Workflow Form EN"
                                                                      :fi "Workflow Form FI"
                                                                      :sv "Workflow Form SV"}
                                                :form/fields [{:field/type :text
                                                               :field/title {:fi "fi" :sv "sv" :en "en"}
                                                               :field/optional true}]})
            wf-id (test-helpers/create-workflow! {:organization {:organization/id "abc"}
                                                  :type :workflow/default
                                                  :title "the title"
                                                  :handlers ["user1" "user2"]
                                                  :forms [{:form/id form-id}]})]
        (is (= {:id wf-id
                :organization {:organization/id "abc" :organization/name {:en "ABC"} :organization/short-name {:en "ABC"}}
                :title "the title"
                :workflow {:type :workflow/default
                           :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}
                                      {:userid "user2" :name "User 2" :email "user2@example.com"}]
                           :forms [{:form/id form-id
                                    :form/internal-name "workflow form"
                                    :form/external-title {:en "Workflow Form EN"
                                                          :fi "Workflow Form FI"
                                                          :sv "Workflow Form SV"}}]
                           :licenses []}
                :enabled true
                :archived false}
               (workflow/get-workflow wf-id)))))

    (testing "decider workflow"
      (let [wf-id (test-helpers/create-workflow! {:organization {:organization/id "abc"}
                                                  :type :workflow/decider
                                                  :title "the title"
                                                  :handlers ["user1" "user2"]})]
        (is (= {:id wf-id
                :organization {:organization/id "abc" :organization/name {:en "ABC"} :organization/short-name {:en "ABC"}}
                :title "the title"
                :workflow {:type :workflow/decider
                           :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}
                                      {:userid "user2" :name "User 2" :email "user2@example.com"}]
                           :forms []
                           :licenses []}
                :enabled true
                :archived false}
               (workflow/get-workflow wf-id)))))

    (testing "master workflow"
      (let [wf-id (test-helpers/create-workflow! {:organization {:organization/id "abc"}
                                                  :type :workflow/master
                                                  :title "the title"
                                                  :handlers ["user1" "user2"]})]
        (is (= {:id wf-id
                :organization {:organization/id "abc" :organization/name {:en "ABC"} :organization/short-name {:en "ABC"}}
                :title "the title"
                :workflow {:type :workflow/master
                           :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}
                                      {:userid "user2" :name "User 2" :email "user2@example.com"}]
                           :forms []
                           :licenses []}
                :enabled true
                :archived false}
               (workflow/get-workflow wf-id)))))))

(deftest test-edit-workflow
  (create-users)

  (with-user "owner"
    (test-helpers/create-organization! {:organization/id "abc" :organization/name {:en "ABC"} :organization/short-name {:en "ABC"}})
    (testing "change title"
      (let [licid (test-helpers/create-license! {:organization {:organization/id "abc"}})
            wf-id (test-helpers/create-workflow! {:organization {:organization/id "abc"}
                                                  :type :workflow/master
                                                  :title "original title"
                                                  :handlers ["user1"]
                                                  :licenses [licid]})]
        (workflow/edit-workflow! {:id wf-id
                                  :title "changed title"})
        (is (= {:id wf-id
                :title "changed title"
                :workflow {:type :workflow/master
                           :handlers [{:userid "user1" :name "User 1" :email "user1@example.com"}]
                           :forms []
                           :licenses [{:license/id licid}]}}
               (-> (workflow/get-workflow wf-id)
                   (select-keys [:id :title :workflow])
                   (update-in [:workflow :licenses]
                              (partial map #(select-keys % [:license/id]))))))))

    (testing "change handlers"
      (let [wf-id (test-helpers/create-workflow! {:organization {:organization/id "abc"}
                                                  :type :workflow/master
                                                  :title "original title"
                                                  :handlers ["user1"]})
            cat-id (test-helpers/create-catalogue-item! {:actor "owner" :workflow-id wf-id})
            app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id] :actor "alice"})]

        (let [app (applications/get-application app-id)]
          (is (= {"alice" #{:applicant} "user1" #{:handler}} (:application/user-roles app)))
          (is (= [{:userid "user1" :name "User 1" :email "user1@example.com"}] (get-in app [:application/workflow :workflow.dynamic/handlers]))))

        (testing "before changing handlers"
          (is (= [] (mapv :application/id (applications/get-all-applications "user1"))) "handler can't see draft")
          (is (= [] (applications/get-all-applications "user2")))

          (test-helpers/submit-application {:application-id app-id :actor "alice"})

          (is (= [app-id] (mapv :application/id (applications/get-all-applications "user1"))))
          (is (= [] (applications/get-all-applications "user2"))))

        (workflow/edit-workflow! {:id wf-id
                                  :handlers ["user2"]})

        (testing "handlers should have changed"
          (let [app (applications/get-application app-id)]
            (is (= {"alice" #{:applicant} "user2" #{:handler}} (:application/user-roles app)))
            (is (= [{:userid "user2" :name "User 2" :email "user2@example.com"}] (get-in app [:application/workflow :workflow.dynamic/handlers]))))

          (is (= [] (applications/get-all-applications "user1")))
          (is (= [app-id] (mapv :application/id (applications/get-all-applications "user2")))))

        (is (= {:id wf-id
                :title "original title"
                :workflow {:type :workflow/master
                           :handlers [{:userid "user2" :name "User 2" :email "user2@example.com"}]
                           :forms []
                           :licenses []}}
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
          wf1 (test-helpers/create-workflow! {:type :workflow/default
                                              :title "workflow2"
                                              :handlers ["user1"
                                                         "user2"]})
          wf2 (test-helpers/create-workflow! {:type :workflow/default
                                              :title "workflow2"
                                              :handlers ["user2"
                                                         "user3"]})]

      (testing "returns distinct handlers from all workflows"
        (is (= ["user1" "user2" "user3"]
               (simplify (workflow/get-handlers)))))

      (testing "ignores disabled workflows"
        (workflow/set-workflow-enabled! {:id wf1 :enabled false})
        (is (= ["user2" "user3"]
               (simplify (workflow/get-handlers)))))

      (testing "ignores archived workflows"
        (workflow/set-workflow-archived! {:id wf2 :archived true})
        (is (= []
               (simplify (workflow/get-handlers))))))))
