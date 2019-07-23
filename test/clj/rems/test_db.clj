(ns ^:integration rems.test-db
  "Namespace for tests that use an actual database."
  (:require [cheshire.core :as cheshire]
            [clj-time.core :as time]
            [clojure.string :refer [split-lines]]
            [clojure.test :refer :all]
            [rems.config]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.applications.legacy :as legacy]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [rems.db.form :as form]
            [rems.db.roles :as roles]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.db.users :as users]
            [rems.db.legacy-workflow-actors :as actors]
            [rems.poller.entitlements :as entitlements-poller]
            [rems.testing-tempura :refer [fake-tempura-fixture]]
            [rems.util :refer [get-user-id]])
  (:import (rems.auth ForbiddenException)))

(use-fixtures :once fake-tempura-fixture test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-get-catalogue-items
  (testing "without catalogue items"
    (is (empty? (db/get-catalogue-items))))

  (testing "with two items"
    (let [resid (:id (db/create-resource! {:resid "urn:nbn:fi:lb-201403262" :organization "nbn" :owneruserid 1 :modifieruserid 1}))]
      (db/create-catalogue-item! {:title "ELFA Corpus" :form nil :resid resid :wfid nil})
      (db/create-catalogue-item! {:title "B" :form nil :resid nil :wfid nil})
      (is (= ["B" "ELFA Corpus"] (sort (map :title (db/get-catalogue-items))))
          "should find the two items")
      (let [item-from-list (second (db/get-catalogue-items))
            item-by-id (first (db/get-catalogue-items {:ids [(:id item-from-list)]}))]
        (is (= item-from-list (dissoc item-by-id
                                      :resource-name
                                      :form-name
                                      :workflow-name))
            "should find same catalogue item by id")))))

(deftest test-multi-applications
  (db/add-user! {:user "test-user" :userattrs nil})
  (db/add-user! {:user "handler" :userattrs nil})
  (let [uid "test-user"
        workflow {:type :workflow/dynamic
                  :handlers ["handler"]}
        wfid (:id (db/create-workflow! {:organization "abc" :modifieruserid "owner" :owneruserid "owner" :title "dynamic" :fnlround -1 :workflow (cheshire/generate-string workflow)}))
        res1 (:id (db/create-resource! {:resid "resid111" :organization "abc" :owneruserid uid :modifieruserid uid}))
        res2 (:id (db/create-resource! {:resid "resid222" :organization "abc" :owneruserid uid :modifieruserid uid}))
        form-id (:id (form/create-form! "owner" {:form/organization "abc" :form/title "" :form/fields []}))
        item1 (:id (db/create-catalogue-item! {:title "item" :form form-id :resid res1 :wfid wfid}))
        item2 (:id (db/create-catalogue-item! {:title "item" :form form-id :resid res2 :wfid wfid}))
        app-id (legacy/create-new-draft uid wfid)]
    ;; apply for two items at the same time
    (db/add-application-item! {:application app-id :item item1})
    (db/add-application-item! {:application app-id :item item2})
    (applications/add-application-created-event!
     {:application-id app-id
      ;; These do nothing right now, but in the future we can get rid of add-application-item! in this test
      :catalogue-item-ids [item1 item2]
      :time (time/now)
      :actor uid})

    (is (nil? (applications/command! {:type :application.command/submit
                                      :actor uid
                                      :application-id app-id
                                      :time (time/now)})))
    (is (nil? (applications/command! {:type :application.command/approve
                                      :actor "handler"
                                      :application-id app-id
                                      :time (time/now)
                                      :comment ""})))
    (is (= :application.state/approved (:application/state (applications/get-application uid app-id))))

    (entitlements-poller/run)
    (is (= ["resid111" "resid222"] (sort (map :resid (db/get-entitlements {:application app-id}))))
        "should create entitlements for both resources")))

(deftest test-phases
  ;; TODO add review when reviewing is supported
  (db/add-user! {:user "approver1" :userattrs nil})
  (db/add-user! {:user "approver2" :userattrs nil})
  (db/add-user! {:user "applicant" :userattrs nil})
  (testing "approval flow"
    (let [wf (:id (db/create-workflow! {:organization "abc" :owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
          item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
          app (legacy/create-new-draft "applicant" wf)
          get-phases (fn [] (legacy/get-application-phases (:state (legacy/get-application-state app))))]
      (db/add-application-item! {:application app :item item})
      (actors/add-approver! wf "approver1" 0)
      (actors/add-approver! wf "approver2" 1)

      (testing "initially the application is in draft phase"
        (is (= [{:phase :apply :active? true :text :t.phases/apply}
                {:phase :approve :text :t.phases/approve}
                {:phase :result :text :t.phases/approved}]
               (get-phases))))

      (legacy/submit-application "applicant" app)

      (testing "after submission the application is in first approval round"
        (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                {:phase :approve :active? true :text :t.phases/approve}
                {:phase :result :text :t.phases/approved}]
               (get-phases))))

      (legacy/approve-application "approver1" app 0 "it's good")

      (testing "after first approval the application is in the second approval round"
        (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                {:phase :approve :active? true :text :t.phases/approve}
                {:phase :result :text :t.phases/approved}]
               (get-phases))))

      (legacy/approve-application "approver2" app 1 "it's good")

      (testing "after both approvals the application is in approved phase"
        (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                {:phase :approve :completed? true :approved? true :text :t.phases/approve}
                {:phase :result :completed? true :approved? true :text :t.phases/approved}]
               (get-phases))))))

  (testing "return flow"
    (let [wf (:id (db/create-workflow! {:organization "abc" :owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
          item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
          app (legacy/create-new-draft "applicant" wf)
          get-phases (fn [] (legacy/get-application-phases (:state (legacy/get-application-state app))))]
      (db/add-application-item! {:application app :item item})
      (actors/add-approver! wf "approver1" 0)
      (actors/add-approver! wf "approver2" 1)

      (testing "initially the application is in draft phase"
        (is (= [{:phase :apply :active? true :text :t.phases/apply}
                {:phase :approve :text :t.phases/approve}
                {:phase :result :text :t.phases/approved}]
               (get-phases))))

      (legacy/submit-application "applicant" app)

      (testing "after submission the application is in first approval round"
        (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                {:phase :approve :active? true :text :t.phases/approve}
                {:phase :result :text :t.phases/approved}]
               (get-phases))))

      (legacy/return-application "approver1" app 0 "it must be changed")

      (testing "after return the application is in the draft phase again"
        (is (= [{:phase :apply :active? true :text :t.phases/apply}
                {:phase :approve :text :t.phases/approve}
                {:phase :result :text :t.phases/approved}]
               (get-phases))))))

  (testing "rejection flow"
    (let [wf (:id (db/create-workflow! {:organization "abc" :owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
          item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
          app (legacy/create-new-draft "applicant" wf)
          get-phases (fn [] (legacy/get-application-phases (:state (legacy/get-application-state app))))]
      (db/add-application-item! {:application app :item item})
      (actors/add-approver! wf "approver1" 0)
      (actors/add-approver! wf "approver2" 1)

      (testing "initially the application is in draft phase"
        (is (= [{:phase :apply :active? true :text :t.phases/apply}
                {:phase :approve :text :t.phases/approve}
                {:phase :result :text :t.phases/approved}]
               (get-phases))))

      (legacy/submit-application "applicant" app)

      (testing "after submission the application is in first approval round"
        (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                {:phase :approve :active? true :text :t.phases/approve}
                {:phase :result :text :t.phases/approved}]
               (get-phases))))

      (legacy/approve-application "approver1" app 0 "it's good")

      (testing "after first approval the application is in the second approval round"
        (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                {:phase :approve :active? true :text :t.phases/approve}
                {:phase :result :text :t.phases/approved}]
               (get-phases))))

      (legacy/reject-application "approver2" app 1 "is no good")

      (testing "after second round rejection the application is in rejected phase"
        (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
                {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]
               (get-phases)))))))

(deftest test-users
  (db/add-user! {:user "pekka", :userattrs nil})
  (db/add-user! {:user "simo", :userattrs nil})
  (is (= 2 (count (db/get-users))))
  (db/add-user! {:user "pekka", :userattrs (cheshire/generate-string {:key "value"})})
  (db/add-user! {:user "simo", :userattrs nil})
  (is (= 2 (count (db/get-users))))
  (is (= {:key "value"} (users/get-user-attributes "pekka"))))

(deftest test-roles
  (db/add-user! {:user "pekka", :userattrs nil})
  (db/add-user! {:user "simo", :userattrs nil})
  (roles/add-role! "pekka" :owner)
  (roles/add-role! "pekka" :owner) ;; add should be idempotent
  (is (= #{:logged-in :owner} (roles/get-roles "pekka")))
  (is (= #{:logged-in} (roles/get-roles "simo")))
  (is (= #{:logged-in} (roles/get-roles "juho"))) ;; default role
  (is (thrown? RuntimeException (roles/add-role! "pekka" :unknown-role))))

(deftest test-get-entitlements-for-export
  (db/add-user! {:user "handler" :userattrs nil})
  (db/add-user! {:user "jack" :userattrs nil})
  (db/add-user! {:user "jill" :userattrs nil})
  (let [workflow {:type :workflow/dynamic :handlers ["handler"]}
        wf (:id (db/create-workflow! {:organization "abc" :modifieruserid "owner" :owneruserid "owner" :title "dynamic" :fnlround -1 :workflow (cheshire/generate-string workflow)}))
        form-id (:id (form/create-form! "owner" {:form/organization "abc" :form/title "" :form/fields []}))
        res1 (:id (db/create-resource! {:resid "resource1" :organization "pre" :owneruserid "owner" :modifieruserid "owner"}))
        res2 (:id (db/create-resource! {:resid "resource2" :organization "pre" :owneruserid "owner" :modifieruserid "owner"}))
        item1 (:id (db/create-catalogue-item! {:title "item1" :form form-id :resid res1 :wfid wf}))
        item2 (:id (db/create-catalogue-item! {:title "item2" :form form-id :resid res2 :wfid wf}))
        jack-app (:application-id (applications/create-application! "jack" [item1]))
        jill-app (:application-id (applications/create-application! "jill" [item1 item2]))]
    (is (nil? (applications/command! {:type :application.command/submit
                                      :time (time/now)
                                      :actor "jack"
                                      :application-id jack-app})))
    (is (nil? (applications/command! {:type :application.command/approve
                                      :time (time/now)
                                      :actor "handler"
                                      :application-id jack-app
                                      :comment ""})))
    (is (nil? (applications/command! {:type :application.command/submit
                                      :time (time/now)
                                      :actor "jill"
                                      :application-id jill-app})))
    (is (nil? (applications/command! {:type :application.command/approve
                                      :time (time/now)
                                      :actor "handler"
                                      :application-id jill-app
                                      :comment ""})))
    (entitlements-poller/run)

    (binding [context/*roles* #{:handler}]
      (let [lines (split-lines (entitlements/get-entitlements-for-export))]
        (is (= 4 (count lines))) ;; header + 3 resources
        (is (some #(and (.contains % "resource1")
                        (.contains % "jill")
                        (.contains % (str jill-app)))
                  lines))
        (is (some #(and (.contains % "resource2")
                        (.contains % "jill")
                        (.contains % (str jill-app)))
                  lines))
        (is (some #(and (.contains % "resource1")
                        (.contains % "jack")
                        (.contains % (str jack-app)))
                  lines))))
    (binding [context/*roles* #{:applicant}]
      (is (thrown? ForbiddenException
                   (entitlements/get-entitlements-for-export))))))

(deftest test-create-demo-data!
  ;; just a smoke test, check that create-demo-data doesn't fail
  (test-data/create-demo-data!)
  (is true))
