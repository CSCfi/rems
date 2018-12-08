(ns ^:integration rems.test.db
  "Namespace for tests that use an actual database."
  (:require [cheshire.core :as cheshire]
            [clj-time.core :as time]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :refer [split-lines]]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.auth.NotAuthorizedException]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.db.workflow-actors :as actors]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [rems.util :refer [get-user-id]]
            [stub-http.core :as stub])
  (:import rems.auth.NotAuthorizedException))

(use-fixtures
  :once
  fake-tempura-fixture
  (fn [f]
    (mount/start
     #'rems.config/env
     #'rems.db.core/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (f)
    (mount/stop)))

(use-fixtures
  :each
  (fn [f]
    (conman/with-transaction [rems.db.core/*db* {:isolation :serializable}]
      (jdbc/db-set-rollback-only! rems.db.core/*db*)
      (f))))

(deftest test-get-catalogue-items
  (testing "without catalogue items"
    (is (empty? (db/get-catalogue-items))))

  (testing "with test database"
    (let [resid (:id (db/create-resource! {:resid "urn:nbn:fi:lb-201403262" :organization "nbn" :owneruserid 1 :modifieruserid 1}))]
      (db/create-catalogue-item! {:title "ELFA Corpus" :form nil :resid resid :wfid nil})
      (db/create-catalogue-item! {:title "B" :form nil :resid nil :wfid nil})
      (is (= ["B" "ELFA Corpus"] (sort (map :title (db/get-catalogue-items))))
          "should find two items")
      (let [item-from-list (second (db/get-catalogue-items))
            item-by-id (db/get-catalogue-item {:item (:id item-from-list)})]
        (is (= item-from-list item-by-id)
            "should find catalogue item by id")))))

(deftest test-form
  (binding [context/*user* {"eppn" "test-user"}
            context/*lang* :en]
    (let [uid (get-user-id)
          form (db/create-form! {:organization "abc" :title "internal-title" :user uid})
          wf (db/create-workflow! {:organization "abc" :modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 0})
          license (db/create-license! {:modifieruserid uid :owneruserid uid :title "non-localized license" :type "link" :textcontent "http://test.org"})
          _ (db/create-license-localization! {:licid (:id license) :langcode "fi" :title "Testi lisenssi" :textcontent "http://testi.fi"})
          _ (db/create-license-localization! {:licid (:id license) :langcode "en" :title "Test license" :textcontent "http://test.com"})
          _ (db/create-workflow-license! {:wfid (:id wf) :licid (:id license) :round 0})
          _ (db/set-workflow-license-validity! {:licid (:id license) :start (time/minus (time/now) (time/years 1)) :end nil})
          item (db/create-catalogue-item! {:title "item" :form (:id form) :resid nil :wfid (:id wf)})
          item-c (db/create-form-item!
                  {:type "text" :user uid :value 0})
          item-a (db/create-form-item!
                  {:type "text" :user uid :value 0})
          item-b (db/create-form-item!
                  {:type "text" :user uid :value 0})
          app-id (applications/create-new-draft (:id wf) uid)]
      (db/add-application-item! {:application app-id :item (:id item)})
      (db/link-form-item! {:form (:id form) :itemorder 2 :item (:id item-b) :user uid :optional false})
      (db/link-form-item! {:form (:id form) :itemorder 1 :item (:id item-a) :user uid :optional false})
      (db/link-form-item! {:form (:id form) :itemorder 3 :item (:id item-c) :user uid :optional false})
      (db/localize-form-item! {:item (:id item-a) :langcode "fi" :title "A-fi" :inputprompt "prompt"})
      (db/localize-form-item! {:item (:id item-b) :langcode "fi" :title "B-fi" :inputprompt "prompt"})
      (db/localize-form-item! {:item (:id item-c) :langcode "fi" :title "C-fi" :inputprompt "prompt"})
      (db/localize-form-item! {:item (:id item-a) :langcode "en" :title "A-en" :inputprompt "prompt"})
      (db/localize-form-item! {:item (:id item-b) :langcode "en" :title "B-en" :inputprompt "prompt"})
      (db/localize-form-item! {:item (:id item-c) :langcode "en" :title "C-en" :inputprompt "prompt"})

      (db/add-user! {:user uid :userattrs nil})
      (actors/add-approver! (:id wf) uid 0)
      (db/create-catalogue-item-localization! {:id (:id item) :langcode "en" :title "item-en"})
      (db/create-catalogue-item-localization! {:id (:id item) :langcode "fi" :title "item-fi"})

      (is (:id item) "sanity check")

      (testing "get form for catalogue item"
        (with-redefs [catalogue/cached
                      {:localizations (catalogue/load-catalogue-item-localizations!)}]
          (let [form (applications/get-form-for app-id)]
            (is (= "internal-title" (:title form)) "title")
            (is (= ["A-en" "B-en" "C-en"] (map #(get-in % [:localizations :en :title]) (:items form))) "items should be in order")
            (is (= ["A-fi" "B-fi" "C-fi"] (map #(get-in % [:localizations :fi :title]) (:items form))) "items should be in order")

            (is (= 1 (count (:licenses form))))
            (is (= {:title "non-localized license"
                    :textcontent "http://test.org"
                    :localizations {:fi {:title "Testi lisenssi" :textcontent "http://testi.fi"}
                                    :en {:title "Test license" :textcontent "http://test.com"}}}
                   (select-keys (first (:licenses form)) [:title :textcontent :localizations]))))))

      (testing "get partially filled form"
        (is app-id "sanity check")
        (db/save-field-value! {:application app-id
                               :form (:id form)
                               :item (:id item-b)
                               :user uid
                               :value "B"})
        (db/save-license-approval! {:catappid app-id
                                    :licid (:id license)
                                    :actoruserid uid
                                    :round 0
                                    :state "approved"})
        (let [f (applications/get-form-for app-id)]
          (is (= app-id (:id (:application f))))
          (is (= "draft" (:state (:application f))))
          (is (= ["" "B" ""] (map :value (:items f))))
          (is (= [true] (map :approved (:licenses f)))))

        (testing "license field"
          (db/save-license-approval! {:catappid app-id
                                      :licid (:id license)
                                      :actoruserid uid
                                      :round 0
                                      :state "approved"})
          (is (= 1 (count (db/get-application-license-approval {:catappid app-id
                                                                :licid (:id license)
                                                                :actoruserid uid})))
              "saving a license approval twice should only create one row")
          (db/delete-license-approval! {:catappid app-id
                                        :licid (:id license)
                                        :actoruserid uid})
          (is (empty? (db/get-application-license-approval {:catappid app-id
                                                            :licid (:id license)
                                                            :actoruserid uid}))
              "after deletion there should not be saved approvals")
          (let [f (applications/get-form-for app-id)]
            (is (= [false] (map :approved (:licenses f))))))
        (testing "reset field value"
          (db/clear-field-value! {:application app-id
                                  :form (:id form)
                                  :item (:id item-b)})
          (db/save-field-value! {:application app-id
                                 :form (:id form)
                                 :item (:id item-b)
                                 :user uid
                                 :value "X"})
          (let [f (applications/get-form-for app-id)]
            (is (= ["" "X" ""] (map :value (:items f)))))))

      (testing "get submitted form as approver"
        (actors/add-approver! (:id wf) "approver" 0)
        (db/save-license-approval! {:catappid app-id
                                    :licid (:id license)
                                    :actoruserid uid
                                    :round 0
                                    :state "approved"})
        (applications/submit-application uid app-id)
        (binding [context/*user* {"eppn" "approver"}]
          (let [form (applications/get-form-for app-id)]
            (is (= "applied" (get-in form [:application :state])))
            (is (= ["" "X" ""] (map :value (:items form))))
            (is (get-in form [:licenses 0 :approved])))))

      (testing "get approved form as applicant"
        (db/add-user! {:user "approver" :userattrs nil})
        (applications/approve-application "approver" app-id 0 "comment")
        (let [form (applications/get-form-for app-id)]
          (is (= "approved" (get-in form [:application :state])))
          (is (= ["" "X" ""] (map :value (:items form))))
          (is (= [nil "comment"]
                 (->> form :application :events (map :comment)))))))))

(deftest test-applications
  (let [uid "test-user"
        _ (db/add-user! {:user uid :userattrs nil})
        wf (:id (db/create-workflow! {:organization "abc" :owneruserid uid :modifieruserid uid :title "" :fnlround 0}))
        item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
        _ (is (empty? (applications/get-user-applications uid)))
        app (applications/create-new-draft wf uid)]
    (db/add-application-item! {:application app :item item})
    (actors/add-approver! wf uid 0)

    (is (= [[app "draft"]]
           (map (juxt :id :state)
                (applications/get-user-applications uid))))
    (applications/submit-application uid app)
    (is (= [[app "applied"]]
           (map (juxt :id :state)
                (applications/get-user-applications uid))))
    (is (empty? (applications/get-user-applications "someone else"))
        "should not show to another user")
    (applications/approve-application uid app 0 "comment")
    (is (= [[app "approved"]]
           (map (juxt :id :state)
                (applications/get-user-applications uid))))
    (testing "deleted application is not shown"
      (applications/close-application uid app 0 "c"))
    (is (empty? (applications/get-user-applications uid))))
  (testing "should not allow missing user"
    (is (thrown? AssertionError (applications/get-user-applications nil)))))

(deftest test-multi-applications
  (binding [context/*user* {"eppn" "test-user"}]
    (db/add-user! {:user "test-user" :userattrs nil})
    (let [uid (get-user-id)
          wf (:id (db/create-workflow! {:organization "abc" :owneruserid uid :modifieruserid uid :title "" :fnlround 0}))
          res1 (:id (db/create-resource! {:resid "resid111" :organization "abc" :owneruserid uid :modifieruserid uid}))
          res2 (:id (db/create-resource! {:resid "resid222" :organization "abc" :owneruserid uid :modifieruserid uid}))
          item1 (:id (db/create-catalogue-item! {:title "item" :form nil :resid res1 :wfid wf}))
          item2 (:id (db/create-catalogue-item! {:title "item" :form nil :resid res2 :wfid wf}))
          app (applications/create-new-draft wf uid)]
      ;; apply for two items at the same time
      (db/add-application-item! {:application app :item item1})
      (db/add-application-item! {:application app :item item2})
      (actors/add-approver! wf uid 0)

      (let [applications (applications/get-user-applications uid)]
        (is (= [{:id app :state "draft"}]
               (map #(select-keys % [:id :state]) applications)))
        (is (= [item1 item2] (sort (map :id (:catalogue-items (first applications)))))
            "includes both catalogue items"))

      (applications/submit-application uid app)
      (is (= [{:id app :state "applied"}]
             (map #(select-keys % [:id :state])
                  (applications/get-user-applications uid))))

      (applications/approve-application uid app 0 "comment")
      (is (= [{:id app :state "approved"}]
             (map #(select-keys % [:id :state])
                  (applications/get-user-applications uid))))

      (is (= ["resid111" "resid222"] (sort (map :resid (db/get-entitlements {:application app}))))
          "should create entitlements for both resources"))))

(deftest test-phases
  (binding [context/*user* {"eppn" "applicant"}]
    ;; TODO add review when reviewing is supported
    (db/add-user! {:user "approver1" :userattrs nil})
    (db/add-user! {:user "approver2" :userattrs nil})
    (db/add-user! {:user "applicant" :userattrs nil})
    (testing "approval flow"
      (let [wf (:id (db/create-workflow! {:organization "abc" :owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
            item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
            app (applications/create-new-draft wf "applicant")
            get-phases (fn [] (applications/get-application-phases (:state (applications/get-application-state app))))]
        (db/add-application-item! {:application app :item item})
        (actors/add-approver! wf "approver1" 0)
        (actors/add-approver! wf "approver2" 1)

        (testing "initially the application is in draft phase"
          (is (= [{:phase :apply :active? true :text :t.phases/apply}
                  {:phase :approve :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (applications/submit-application "applicant" app)

        (testing "after submission the application is in first approval round"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :active? true :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (applications/approve-application "approver1" app 0 "it's good")

        (testing "after first approval the application is in the second approval round"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :active? true :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (applications/approve-application "approver2" app 1 "it's good")

        (testing "after both approvals the application is in approved phase"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :completed? true :approved? true :text :t.phases/approve}
                  {:phase :result :completed? true :approved? true :text :t.phases/approved}]
                 (get-phases))))))

    (testing "return flow"
      (let [wf (:id (db/create-workflow! {:organization "abc" :owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
            item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
            app (applications/create-new-draft wf "applicant")
            get-phases (fn [] (applications/get-application-phases (:state (applications/get-application-state app))))]
        (db/add-application-item! {:application app :item item})
        (actors/add-approver! wf "approver1" 0)
        (actors/add-approver! wf "approver2" 1)

        (testing "initially the application is in draft phase"
          (is (= [{:phase :apply :active? true :text :t.phases/apply}
                  {:phase :approve :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (applications/submit-application "applicant" app)

        (testing "after submission the application is in first approval round"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :active? true :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (binding [context/*user* {"eppn" "approver1"}]
          (applications/return-application app 0 "it must be changed"))

        (testing "after return the application is in the draft phase again"
          (is (= [{:phase :apply :active? true :text :t.phases/apply}
                  {:phase :approve :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))))

    (testing "rejection flow"
      (let [wf (:id (db/create-workflow! {:organization "abc" :owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
            item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
            app (applications/create-new-draft wf "applicant")
            get-phases (fn [] (applications/get-application-phases (:state (applications/get-application-state app))))]
        (db/add-application-item! {:application app :item item})
        (actors/add-approver! wf "approver1" 0)
        (actors/add-approver! wf "approver2" 1)

        (testing "initially the application is in draft phase"
          (is (= [{:phase :apply :active? true :text :t.phases/apply}
                  {:phase :approve :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (applications/submit-application "applicant" app)

        (testing "after submission the application is in first approval round"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :active? true :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (applications/approve-application "approver1" app 0 "it's good")

        (testing "after first approval the application is in the second approval round"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :active? true :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (binding [context/*user* {"eppn" "approver2"}]
          (applications/reject-application app 1 "is no good"))

        (testing "after second round rejection the application is in rejected phase"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
                  {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]
                 (get-phases))))))))

(deftest test-actions
  (binding [context/*user* {"eppn" "test-user"}]
    (let [uid (get-user-id)
          uid2 "another-user"
          wfid1 (:id (db/create-workflow! {:organization "abc" :owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 0}))
          wfid2 (:id (db/create-workflow! {:organization "abc" :owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 1}))
          _ (actors/add-approver! wfid1 uid 0)
          _ (actors/add-approver! wfid2 uid2 0)
          _ (actors/add-approver! wfid2 uid 1)
          item1 (:id (db/create-catalogue-item! {:title "item1" :form nil :resid nil :wfid wfid1}))
          item2 (:id (db/create-catalogue-item! {:title "item2" :form nil :resid nil :wfid wfid2}))
          item3 (:id (db/create-catalogue-item! {:title "item3" :form nil :resid nil :wfid wfid1}))
          item4 (:id (db/create-catalogue-item! {:title "item4" :form nil :resid nil :wfid wfid2}))
          app1 (applications/create-new-draft wfid1 uid) ; should see as approver for round 0
          app2 (applications/create-new-draft wfid2 uid) ; should see as approver for round 1
          app3 (applications/create-new-draft wfid1 uid) ; should not see draft
          app4 (applications/create-new-draft wfid2 uid) ; should not see approved

          can-approve? #(#'applications/can-approve? %1 (applications/get-application-state %2))]

      (db/add-application-item! {:application app1 :item (:id item1)})
      (db/add-application-item! {:application app2 :item (:id item2)})
      (db/add-application-item! {:application app3 :item (:id item3)})
      (db/add-application-item! {:application app4 :item (:id item4)})

      (db/add-user! {:user uid :userattrs nil})
      (db/add-user! {:user uid2 :userattrs nil})

      (applications/submit-application uid app1)
      (applications/submit-application uid app2)
      (applications/submit-application uid app4)
      (applications/approve-application uid2 app4 0 "")
      (applications/approve-application uid app4 1 "")

      (is (= [{:id app1 :state "applied" :curround 0}]
             (map #(select-keys % [:id :state :catid :curround])
                  (applications/get-approvals)))
          "should only see app1")

      (is (= [{:id app4 :state "approved" :curround 1}]
             (map #(select-keys % [:id :state :curround])
                  (applications/get-handled-approvals)))
          "should only see app4 in handled approvals")

      (testing "applications/can-approve?"
        (is (can-approve? uid app1))
        (is (not (can-approve? uid app2)))
        (is (not (can-approve? uid app3)))
        (is (not (can-approve? uid app4)))
        (is (not (can-approve? uid2 app1)))
        (is (can-approve? uid2 app2)))

      (testing "applications/is-approver?"
        (is (#'applications/is-approver? uid app1))
        (is (#'applications/is-approver? uid app2))
        (is (#'applications/is-approver? uid app3))
        (is (#'applications/is-approver? uid app4))
        (is (not (#'applications/is-approver? uid2 app1)))
        (is (#'applications/is-approver? uid2 app2))
        (is (not (#'applications/is-approver? uid2 app3)))
        (is (#'applications/is-approver? uid2 app4)))

      ;; move app1 and app2 to round 1
      (applications/approve-application uid app1 0 "")
      (applications/approve-application uid2 app2 0 "")

      (is (= [{:id app1 :state "approved" :curround 0}
              {:id app4 :state "approved" :curround 1}]
             (map #(select-keys % [:id :state :curround])
                  (applications/get-handled-approvals)))
          "should see app1 and app4 in handled approvals")

      (is (= [{:id app2 :state "applied" :curround 1}]
             (map #(select-keys % [:id :state :catid :curround])
                  (applications/get-approvals)))
          "should only see app2")

      (testing "applications/can-approve? after changes"
        (is (not (can-approve? uid app1)))
        (is (can-approve? uid app2))
        (is (not (can-approve? uid app3)))
        (is (not (can-approve? uid app4)))
        (is (not (can-approve? uid2 app1)))
        (is (not (can-approve? uid2 app2)))))))

(deftest test-get-applications-to-review
  (binding [context/*user* {"eppn" "test-user"}]
    (let [uid (get-user-id)
          uid2 "another-user"
          wfid1 (:id (db/create-workflow! {:organization "abc" :owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 0}))
          wfid2 (:id (db/create-workflow! {:organization "abc" :modifieruserid "workflow-owner" :owneruserid "workflow-owner" :title "" :fnlround 1}))
          _ (actors/add-reviewer! wfid1 uid 0)
          _ (actors/add-reviewer! wfid2 uid2 0)
          _ (actors/add-reviewer! wfid2 uid 1)
          item1 (:id (db/create-catalogue-item! {:title "item1" :form nil :resid nil :wfid wfid1}))
          item2 (:id (db/create-catalogue-item! {:title "item2" :form nil :resid nil :wfid wfid2}))
          item3 (:id (db/create-catalogue-item! {:title "item3" :form nil :resid nil :wfid wfid1}))
          item4 (:id (db/create-catalogue-item! {:title "item4" :form nil :resid nil :wfid wfid2}))
          app1 (applications/create-new-draft wfid1 uid) ; should see as reviewer for round 0
          app2 (applications/create-new-draft wfid2 uid) ; should see as reviewer for round 1
          app3 (applications/create-new-draft wfid1 uid) ; should not see draft
          app4 (applications/create-new-draft wfid2 uid)

          can-review? #(#'applications/can-review? %1 (applications/get-application-state %2))]
      (db/add-application-item! {:application app1 :item item1})
      (db/add-application-item! {:application app2 :item item2})
      (db/add-application-item! {:application app3 :item item3})
      (db/add-application-item! {:application app4 :item item4})
      (db/add-user! {:user uid :userattrs nil})
      (db/add-user! {:user uid2 :userattrs nil})

      (applications/submit-application uid app1)
      (applications/submit-application uid app2)
      (applications/submit-application uid app4)
      (binding [context/*user* {"eppn" uid2}]
        (applications/review-application app4 0 ""))
      (applications/review-application app4 1 "")

      (is (= [{:id app1 :state "applied" :curround 0}]
             (map #(select-keys % [:id :state :curround])
                  (applications/get-applications-to-review)))
          "should only see app1")
      (is (= [{:id app4 :state "approved" :curround 1}]
             (map #(select-keys % [:id :state :curround])
                  (applications/get-handled-reviews)))
          "should only see app4 in handled reviews")

      (testing "applications/can-review?"
        (is (can-review? uid app1))
        (is (not (can-review? uid app2)))
        (is (not (can-review? uid app3)))
        (is (not (can-review? uid app4)))
        (is (not (can-review? uid2 app1)))
        (is (can-review? uid2 app2)))

      (testing "applications/is-reviewer?"
        (is (#'applications/is-reviewer? uid app1))
        (is (#'applications/is-reviewer? uid app2))
        (is (#'applications/is-reviewer? uid app3))
        (is (#'applications/is-reviewer? uid app4))
        (is (not (#'applications/is-reviewer? uid2 app1)))
        (is (#'applications/is-reviewer? uid2 app2))
        (is (not (#'applications/is-reviewer? uid2 app3)))
        (is (#'applications/is-reviewer? uid2 app4)))

      ;; move app1 and app2 to round 1
      (applications/review-application app1 0 "")
      (binding [context/*user* {"eppn" uid2}]
        (applications/review-application app2 0 "")
        (is (= [{:id app2 :state "applied" :curround 1}
                {:id app4 :state "approved" :curround 1}]
               (map #(select-keys % [:id :state :curround])
                    (applications/get-handled-reviews)))
            (str uid2 " should see app2 and app4 in handled reviews")))

      (is (= [{:id app1 :state "approved" :curround 0}
              {:id app4 :state "approved" :curround 1}]
             (map #(select-keys % [:id :state :curround])
                  (applications/get-handled-reviews)))
          (str uid " should see app1 and app4 in handled reviews"))

      (is (= [{:id app2 :state "applied" :curround 1}]
             (map #(select-keys % [:id :state :curround])
                  (applications/get-applications-to-review)))
          "should only see app2")

      (testing "applications/can-review? after changes"
        (is (not (can-review? uid app1)))
        (is (can-review? uid app2))
        (is (not (can-review? uid app3)))
        (is (not (can-review? uid app4)))
        (is (not (can-review? uid2 app1)))
        (is (not (can-review? uid2 app2)))))))

(deftest test-users
  (db/add-user! {:user "pekka", :userattrs nil})
  (db/add-user! {:user "simo", :userattrs nil})
  (is (= 2 (count (db/get-users))))
  (db/add-user! {:user "pekka", :userattrs (cheshire/generate-string {"key" "value"})})
  (db/add-user! {:user "simo", :userattrs nil})
  (is (= 2 (count (db/get-users))))
  (is (= {"key" "value"} (users/get-user-attributes "pekka"))))

(deftest test-roles
  (db/add-user! {:user "pekka", :userattrs nil})
  (db/add-user! {:user "simo", :userattrs nil})
  (roles/add-role! "pekka" :applicant)
  (roles/add-role! "pekka" :reviewer)
  (roles/add-role! "pekka" :reviewer) ;; add should be idempotent
  (roles/add-role! "simo" :approver)
  (is (= #{:applicant :reviewer} (roles/get-roles "pekka")))
  (is (= #{:approver} (roles/get-roles "simo")))
  (is (= #{:applicant} (roles/get-roles "juho"))) ;; default role
  (is (thrown? RuntimeException (roles/add-role! "pekka" :unknown-role))))

(deftest test-application-events
  (binding [context/*user* {"eppn" "event-test"}]
    (db/add-user! {:user "event-test", :userattrs nil})
    (db/add-user! {:user "event-test-approver", :userattrs nil})
    (db/add-user! {:user "event-test-reviewer", :userattrs nil})
    (let [uid (get-user-id)
          wf (:id (db/create-workflow! {:organization "abc" :modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 1}))
          item (:id (db/create-catalogue-item! {:title "A" :form nil :resid nil :wfid wf}))
          fetch (fn [app] (select-keys (applications/get-application-state app)
                                       [:state :curround]))]
      (actors/add-approver! wf uid 0)
      (actors/add-reviewer! wf "event-test-reviewer" 0)
      (actors/add-approver! wf "event-test-approver" 1)

      (testing "submitting, approving"
        (let [app (applications/create-new-draft wf uid)]
          (db/add-application-item! {:application app :item item})

          (is (= {:curround 0 :state "draft"} (fetch app)))

          (is (thrown? NotAuthorizedException (applications/approve-application uid app 0 ""))
              "Should not be able to approve draft")

          (is (thrown? NotAuthorizedException (applications/withdraw-application uid app 0 ""))
              "Should not be able to withdraw draft")

          (binding [context/*user* {"eppn" "event-test-approver"}]
            (is (thrown? NotAuthorizedException (applications/submit-application "event-test-approver" app))
                "Should not be able to submit when not applicant"))

          (applications/submit-application uid app)
          (is (= {:curround 0 :state "applied"} (fetch app)))

          (applications/try-autoapprove-application app)
          (is (= {:curround 0 :state "applied"} (fetch app))
              "Autoapprove should do nothing")

          (is (thrown? NotAuthorizedException (applications/submit-application uid app)) ; TODO Wrong exception?
              "Should not be able to submit twice")

          (is (thrown? NotAuthorizedException (applications/approve-application uid app 1 "")) ; TODO Wrong exception?
              "Should not be able to approve wrong round")

          (testing "withdrawing and resubmitting"
            (applications/withdraw-application uid app 0 "test withdraw")
            (is (= {:curround 0 :state "withdrawn"} (fetch app)))

            (applications/submit-application uid app)
            (is (= {:curround 0 :state "applied"} (fetch app))))

          (is (thrown? NotAuthorizedException (applications/review-application app 0 ""))
              "Should not be able to review as an approver")
          (applications/approve-application uid app 0 "c1")
          (is (= {:curround 1 :state "applied"} (fetch app)))

          (is (thrown? NotAuthorizedException (applications/approve-application uid app 1 ""))
              "Should not be able to approve if not approver")

          (is (thrown? NotAuthorizedException (applications/withdraw-application "event-test-approver" app 1 ""))
              "Should not be able to withdraw as approver")

          (is (empty? (db/get-entitlements)))

          (applications/approve-application "event-test-approver" app 1 "c2")
          (is (= {:curround 1 :state "approved"} (fetch app)))

          (is (= [{:catappid app :resid nil :userid uid}]
                 (map #(select-keys % [:catappid :resid :userid])
                      (db/get-entitlements))))

          (is (= (->> (applications/get-application-state app)
                      :events
                      (map #(select-keys % [:round :event :comment])))
                 [{:round 0 :event "apply" :comment nil}
                  {:round 0 :event "withdraw" :comment "test withdraw"}
                  {:round 0 :event "apply" :comment nil}
                  {:round 0 :event "approve" :comment "c1"}
                  {:round 1 :event "approve" :comment "c2"}]
                 (->> (applications/get-application-state app)
                      :events
                      (map #(select-keys % [:round :event :comment])))))))

      (testing "rejecting"
        (let [app (applications/create-new-draft wf uid)]
          (db/add-application-item! {:application app :item item})

          (is (= {:curround 0 :state "draft"} (fetch app)))
          (applications/submit-application uid app)
          (is (= {:curround 0 :state "applied"} (fetch app)))
          (applications/reject-application app 0 "comment")
          (is (= {:curround 0 :state "rejected"} (fetch app)))))

      (testing "returning, resubmitting"
        (let [app (applications/create-new-draft wf uid)]
          (db/add-application-item! {:application app :item item})

          (is (thrown? NotAuthorizedException (applications/return-application app 0 "comment"))
              "Should not be able to return before submitting")

          (applications/submit-application uid app)

          (binding [context/*user* {"eppn" "event-test-approver"}]
            (is (thrown? NotAuthorizedException (applications/return-application app 0 "comment"))
                "Should not be able to return when not approver"))

          (applications/return-application app 0 "comment")
          (is (= {:curround 0 :state "returned"} (fetch app)))

          (is (thrown? NotAuthorizedException (applications/return-application app 0 "comment"))
              "Should not be able to return twice")

          (binding [context/*user* {"eppn" "event-test-approver"}]
            (is (thrown? NotAuthorizedException (applications/submit-application "event-test-approver" app)) ; TODO wrong exception?
                "Should not be able to resubmit when not approver"))

          (applications/submit-application uid app)
          (is (= {:curround 0 :state "applied"} (fetch app)))))

      (testing "review"
        (let [rev-wf (:id (db/create-workflow! {:organization "abc" :owneruserid uid :modifieruserid uid :title "Review workflow" :fnlround 1}))
              rev-item (:id (db/create-catalogue-item! {:title "Review item" :resid nil :wfid rev-wf :form nil}))
              rev-app (applications/create-new-draft rev-wf uid)]
          (db/add-application-item! {:application rev-app :item rev-item})
          (actors/add-reviewer! rev-wf "event-test-reviewer" 0)
          (actors/add-approver! rev-wf uid 1)
          (is (= {:curround 0 :state "draft"} (fetch rev-app)))
          (binding [context/*user* {"eppn" "event-test-reviewer"}]
            (is (thrown? NotAuthorizedException (applications/review-application rev-app 0 ""))
                "Should not be able to review a draft"))
          (is (thrown? NotAuthorizedException (applications/review-application rev-app 0 ""))
              "Should not be able to review if not reviewer")
          (applications/submit-application uid rev-app)
          (is (= {:curround 0 :state "applied"} (fetch rev-app)))
          (binding [context/*user* {"eppn" "event-test-reviewer"}]
            (is (thrown? NotAuthorizedException (applications/review-application rev-app 1 ""))
                "Should not be able to review wrong round")
            (is (thrown? NotAuthorizedException (applications/approve-application "event-test-reviewer" rev-app 0 ""))
                "Should not be able to approve as reviewer")
            (applications/review-application rev-app 0 "looks good to me"))
          (is (= {:curround 1 :state "applied"} (fetch rev-app)))
          (applications/return-application rev-app 1 "comment")
          (is (= {:curround 0 :state "returned"} (fetch rev-app)))
          (binding [context/*user* {"eppn" "event-test-reviewer"}]
            (is (thrown? NotAuthorizedException (applications/review-application rev-app 0 ""))
                "Should not be able to review when returned"))
          (applications/submit-application uid rev-app)
          (applications/withdraw-application uid rev-app 0 "test withdraw")
          (is (= {:curround 0 :state "withdrawn"} (fetch rev-app)))
          (binding [context/*user* {"eppn" "event-test-reviewer"}]
            (is (thrown? NotAuthorizedException (applications/review-application rev-app 0 ""))
                "Should not be able to review when withdrawn"))))

      (testing "closing"
        (testing "a draft as the applicant"
          (let [app (applications/create-new-draft wf uid)]
            (db/add-application-item! {:application app :item item})
            (applications/close-application uid app 0 "closing draft")
            (is (= {:curround 0 :state "closed"} (fetch app)))))
        (testing "an applied application as the applicant"
          (let [app (applications/create-new-draft wf uid)]
            (db/add-application-item! {:application app :item item})
            (applications/submit-application uid app)
            (testing "as approver fails"
              (is (thrown? NotAuthorizedException (applications/close-application "event-test-approver" app 0 "closing applied"))))
            (applications/close-application uid app 0 "closing applied")
            (is (= {:curround 0 :state "closed"} (fetch app)))))
        (testing "an approved application as the applicant"
          (let [app (applications/create-new-draft wf uid)]
            (db/add-application-item! {:application app :item item})
            (applications/submit-application uid app)
            (applications/approve-application uid app 0 "c1")
            (applications/approve-application "event-test-approver" app 1 "c2")
            (applications/close-application uid app 1 "closing approved")
            (is (= {:curround 1 :state "closed"} (fetch app)))))
        (testing "an approved application as the approver"
          (let [app (applications/create-new-draft wf uid)]
            (db/add-application-item! {:application app :item item})
            (applications/submit-application uid app)
            (applications/approve-application uid app 0 "c1")
            (applications/approve-application "event-test-approver" app 1 "c2")
            (applications/close-application "event-test-approver" app 1 "closing approved")
            (is (= {:curround 1 :state "closed"} (fetch app))))))

      (testing "autoapprove"
        (let [res-abc (:id (db/create-resource! {:resid "ABC" :organization "abc" :owneruserid uid :modifieruserid uid}))
              auto-wf (:id (db/create-workflow! {:organization "abc" :modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 1}))
              auto-item (:id (db/create-catalogue-item! {:title "A" :form nil :resid res-abc :wfid auto-wf}))
              auto-app (applications/create-new-draft auto-wf uid)]
          (db/add-application-item! {:application auto-app :item auto-item})
          (is (= (fetch auto-app) {:curround 0 :state "draft"}))
          (applications/submit-application uid auto-app)
          (is (= (fetch auto-app) {:curround 1 :state "approved"}))
          (is (= (->> (applications/get-application-state auto-app)
                      :events
                      (map #(select-keys % [:round :event])))
                 [{:round 0 :event "apply"}
                  {:round 0 :event "autoapprove"}
                  {:round 1 :event "autoapprove"}]))
          (is (contains? (set (map #(select-keys % [:catappid :resid :userid])
                                   (db/get-entitlements)))
                         {:catappid auto-app :resid "ABC" :userid uid}))))
      (let [new-wf (:id (db/create-workflow! {:organization "abc" :modifieruserid uid :owneruserid uid :title "3rd party review workflow" :fnlround 0}))
            new-item (:id (db/create-catalogue-item! {:title "A" :form nil :resid nil :wfid new-wf}))]
        (actors/add-approver! new-wf uid 0)
        (db/add-user! {:user "third-party-reviewer", :userattrs (cheshire/generate-string {"eppn" "third-party-reviewer" "mail" ""})})
        (db/add-user! {:user "another-reviewer", :userattrs (cheshire/generate-string {"eppn" "another-reviewer" "mail" ""})})
        (testing "3rd party review"
          (let [new-app (applications/create-new-draft new-wf uid)]
            (db/add-application-item! {:application new-app :item new-item})
            (applications/submit-application uid new-app)
            (is (= #{:applicant} (roles/get-roles "third-party-reviewer"))) ;; default role
            (is (= #{:applicant} (roles/get-roles "another-reviewer"))) ;; default role
            (applications/send-review-request new-app 0 "review?" "third-party-reviewer")
            (is (= #{:reviewer} (roles/get-roles "third-party-reviewer")))
            ;; should not send twice to third-party-reviewer, but another-reviewer should still be added
            (applications/send-review-request new-app 0 "can you please review this?" ["third-party-reviewer" "another-reviewer"])
            (is (= #{:reviewer} (roles/get-roles "third-party-reviewer")))
            (is (= #{:reviewer} (roles/get-roles "another-reviewer")))
            (is (= (fetch new-app) {:curround 0 :state "applied"}))
            (binding [context/*user* {"eppn" "third-party-reviewer"}]
              (applications/perform-third-party-review new-app 0 "comment")
              (is (thrown? NotAuthorizedException (applications/review-application new-app 0 "another comment"))
                  "Should not be able to do normal review"))
            (is (= (fetch new-app) {:curround 0 :state "applied"}))
            (applications/approve-application uid new-app 0 "")
            (is (= (fetch new-app) {:curround 0 :state "approved"}))
            (binding [context/*user* {"eppn" "third-party-reviewer"}]
              (is (thrown? NotAuthorizedException (applications/perform-third-party-review new-app 0 "another comment"))
                  "Should not be able to review when approved"))
            (binding [context/*user* {"eppn" "other-reviewer"}]
              (is (thrown? NotAuthorizedException (applications/perform-third-party-review new-app 0 "too late comment"))
                  "Should not be able to review when approved"))
            (is (= (->> (applications/get-application-state new-app)
                        :events
                        (map #(select-keys % [:round :event :comment])))
                   [{:round 0 :event "apply" :comment nil}
                    {:round 0 :event "review-request" :comment "review?"}
                    {:round 0 :event "review-request" :comment "can you please review this?"}
                    {:round 0 :event "third-party-review" :comment "comment"}
                    {:round 0 :event "approve" :comment ""}]))))
        (testing "lazy 3rd party reviewer"
          (let [app-to-close (applications/create-new-draft new-wf uid)
                app-to-approve (applications/create-new-draft new-wf uid)
                app-to-reject (applications/create-new-draft new-wf uid)
                app-to-return (applications/create-new-draft new-wf uid)]
            (db/add-application-item! {:application app-to-close :item new-item})
            (db/add-application-item! {:application app-to-approve :item new-item})
            (db/add-application-item! {:application app-to-reject :item new-item})
            (db/add-application-item! {:application app-to-return :item new-item})
            (applications/submit-application uid app-to-close)
            (applications/submit-application uid app-to-approve)
            (applications/submit-application uid app-to-reject)
            (applications/submit-application uid app-to-return)
            (applications/send-review-request app-to-close 0 "can you please review this?" "third-party-reviewer")
            (applications/send-review-request app-to-approve 0 "can you please review this?" "third-party-reviewer")
            (applications/send-review-request app-to-reject 0 "can you please review this?" "third-party-reviewer")
            (applications/send-review-request app-to-return 0 "can you please review this?" "third-party-reviewer")
            (applications/close-application uid app-to-close 0 "closing")
            (is (= (fetch app-to-close) {:curround 0 :state "closed"}) "should be able to close application even without review")
            (applications/approve-application uid app-to-approve 0 "approving")
            (is (= (fetch app-to-approve) {:curround 0 :state "approved"}) "should be able to approve application even without review")
            (applications/reject-application app-to-reject 0 "rejecting")
            (is (= (fetch app-to-reject) {:curround 0 :state "rejected"}) "should be able to reject application even without review")
            (applications/return-application app-to-return 0 "returning")
            (is (= (fetch app-to-return) {:curround 0 :state "returned"}) "should be able to return application even without review")
            (binding [context/*user* {"eppn" "third-party-reviewer"}]
              (is (thrown? NotAuthorizedException (applications/perform-third-party-review app-to-close 0 "comment"))
                  "Should not be able to review when closed")
              (is (thrown? NotAuthorizedException (applications/perform-third-party-review app-to-approve 0 "comment"))
                  "Should not be able to review when approved")
              (is (thrown? NotAuthorizedException (applications/perform-third-party-review app-to-reject 0 "comment"))
                  "Should not be able to review when rejected")
              (is (thrown? NotAuthorizedException (applications/perform-third-party-review app-to-return 0 "another comment"))
                  "Should not be able to review when returned"))
            (is (= (->> (applications/get-application-state app-to-close)
                        :events
                        (map #(select-keys % [:round :event :comment])))
                   [{:round 0 :event "apply" :comment nil}
                    {:round 0 :event "review-request" :comment "can you please review this?"}
                    {:round 0 :event "close" :comment "closing"}]))
            (is (= (->> (applications/get-application-state app-to-approve)
                        :events
                        (map #(select-keys % [:round :event :comment])))
                   [{:round 0 :event "apply" :comment nil}
                    {:round 0 :event "review-request" :comment "can you please review this?"}
                    {:round 0 :event "approve" :comment "approving"}]))
            (is (= (->> (applications/get-application-state app-to-reject)
                        :events
                        (map #(select-keys % [:round :event :comment])))
                   [{:round 0 :event "apply" :comment nil}
                    {:round 0 :event "review-request" :comment "can you please review this?"}
                    {:round 0 :event "reject" :comment "rejecting"}]))
            (is (= (->> (applications/get-application-state app-to-return)
                        :events
                        (map #(select-keys % [:round :event :comment])))
                   [{:round 0 :event "apply" :comment nil}
                    {:round 0 :event "review-request" :comment "can you please review this?"}
                    {:round 0 :event "return" :comment "returning"}]))))))))

(deftest test-add-member
  (db/add-user! {:user "alice" :userattrs "{\"eppn\": \"alice\"}"})
  (db/add-user! {:user "bob" :userattrs "{\"eppn\": \"bob\"}"})
  (db/add-user! {:user "carl" :userattrs "{\"eppn\": \"carl\"}"})
  (db/add-user! {:user "david" :userattrs "{\"eppn\": \"david\"}"})
  (binding [context/*user* {"eppn" "alice"}]
    (let [uid (get-user-id)
          wf (:id (db/create-workflow! {:organization "abc" :modifieruserid "owner" :owneruserid "owner" :title "Test workflow" :fnlround 1}))
          item (:id (db/create-catalogue-item! {:title "A" :form nil :resid nil :wfid wf}))
          app (applications/create-new-draft wf uid)]
      (db/add-application-item! {:application app :item item})
      (testing "Adding two members"
        (applications/add-member app "bob")
        (applications/add-member app "carl")
        (is (= ["bob" "carl"] (:members (applications/get-application-state app)))))
      (testing "Can't add non-existing members"
        (is (thrown? AssertionError
                     (applications/add-member app "non-existent"))))
      (testing "Non-applicant can't add members"
        (binding [context/*user* {"eppn" "other-user"}]
          (is (thrown? NotAuthorizedException
                       (applications/add-member app "david")))))
      (applications/submit-application uid app)
      (testing "Can't add members to submitted application"
        (is (thrown? NotAuthorizedException
                     (applications/add-member app "david"))))
      (testing "Members persist after autoapprove"
        (let [state (applications/get-application-state app)]
          (is (= "approved" (:state state)))
          (is (= ["bob" "carl"] (:members state))))))))

(deftest test-get-entitlements-for-export
  (db/add-user! {:user "jack" :userattrs nil})
  (db/add-user! {:user "jill" :userattrs nil})
  (let [wf (:id (db/create-workflow! {:organization "abc" :modifieruserid "owner" :owneruserid "owner" :title "Test workflow" :fnlround 1}))
        res1 (:id (db/create-resource! {:resid "resource1" :organization "pre" :owneruserid "owner" :modifieruserid "owner"}))
        res2 (:id (db/create-resource! {:resid "resource2" :organization "pre" :owneruserid "owner" :modifieruserid "owner"}))
        item1 (:id (db/create-catalogue-item! {:title "item1" :form nil :resid res1 :wfid wf}))
        item2 (:id (db/create-catalogue-item! {:title "item2" :form nil :resid res2 :wfid wf}))
        jack-app (applications/create-new-draft wf "jack")
        jill-app (applications/create-new-draft wf "jill")]
    (db/add-application-item! {:application jack-app :item item1})
    (db/add-application-item! {:application jill-app :item item1})
    (db/add-application-item! {:application jill-app :item item2})
    (binding [context/*user* {"eppn" "jack"}]
      (applications/submit-application "jack" jack-app))
    (binding [context/*user* {"eppn" "jill"}]
      (applications/submit-application "jill" jill-app))
    ;; entitlements should now be added via autoapprove
    (binding [context/*roles* #{:approver}]
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
    (binding [context/*roles* #{:applicant :reviewer}]
      (is (thrown? NotAuthorizedException
                   (entitlements/get-entitlements-for-export))))))

(deftest test-entitlements-post
  (testing "application that is not approved should not result in entitlements"
    (with-redefs [rems.db.core/add-entitlement! #(throw (Error. "don't call me"))]
      (entitlements/update-entitlements-for {:id 3
                                             :state "applied"
                                             :applicantuserid "bob"})))
  (with-open [server (stub/start! {"/add" {:status 200}
                                   "/remove" {:status 200}})]
    (with-redefs [rems.config/env {:entitlements-target
                                   {:add (str (:uri server) "/add")
                                    :remove (str (:uri server) "/remove")}}]
      (let [uid "bob"
            admin "owner"
            organization "foo"
            wf (:id (db/create-workflow! {:organization "abc" :modifieruserid admin :owneruserid admin :title "Test workflow" :fnlround 1}))
            res1 (:id (db/create-resource! {:resid "resource1" :organization organization :owneruserid admin :modifieruserid admin}))
            res2 (:id (db/create-resource! {:resid "resource2" :organization organization :owneruserid admin :modifieruserid admin}))
            item1 (:id (db/create-catalogue-item! {:title "item1" :form nil :resid res1 :wfid wf}))
            item2 (:id (db/create-catalogue-item! {:title "item2" :form nil :resid res2 :wfid wf}))]
        (db/add-user! {:user uid :userattrs (cheshire/generate-string {"mail" "b@o.b"})})
        (binding [context/*user* {"eppn" uid}]
          (let [application (applications/create-new-draft wf uid)]
            (db/add-application-item! {:application application :item item1})
            (db/add-application-item! {:application application :item item2})
            ;; should get autoapproved, which calls update-entitlements-for
            (applications/submit-application uid application)
            (testing "application that is approved should result in POST"
              (let [data (first (stub/recorded-requests server))
                    target (:path data)
                    body (cheshire/parse-string (get-in data [:body "postData"]))]
                (is (= "/add" target))
                (is (= [{"resource" "resource1" "application" application "user" "bob" "mail" "b@o.b"}
                        {"resource" "resource2" "application" application "user" "bob" "mail" "b@o.b"}]
                       body))))
            (applications/close-application uid application 1 "close msg")
            (testing "closing application should result in new POST"
              (let [data (second (stub/recorded-requests server))
                    target (:path data)
                    body (cheshire/parse-string (get-in data [:body "postData"]))]
                (is (= "/remove" target))
                (is (= [{"resource" "resource1" "application" application "user" "bob" "mail" "b@o.b"}
                        {"resource" "resource2" "application" application "user" "bob" "mail" "b@o.b"}]
                       body))))))))))

(deftest test-dynamic-workflow
  (binding [context/*user* {"eppn" "alice"}]
    (db/add-user! {:user "alice" :userattrs "{}"})
    (db/add-user! {:user "bob" :userattrs "{}"})
    (db/add-user! {:user "handler" :userattrs "{}"})
    (let [workflow {:type :workflow/dynamic
                    :handlers ["handler"]}
          wfid (:id (db/create-workflow! {:organization "abc" :modifieruserid "owner" :owneruserid "owner" :title "dynamic" :fnlround -1 :workflow (cheshire/generate-string workflow)}))
          app-id (applications/create-new-draft wfid "alice")]
      (is (= {:applicantuserid "alice"
              :state :rems.workflow.dynamic/draft
              :workflow workflow}
             (select-keys (applications/get-dynamic-application-state app-id) [:applicantuserid :state :workflow])))
      (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/submit
                                                :actor "alice"
                                                :application-id app-id})))
      (is (= :rems.workflow.dynamic/submitted
             (:state (applications/get-dynamic-application-state app-id))))
      (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/add-member
                                                :actor "alice"
                                                :member "bob"
                                                :application-id app-id})))
      (is (= ["alice" "bob"]
             (:members (applications/get-dynamic-application-state app-id))))
      (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/approve
                                                :actor "handler"
                                                :application-id app-id})))
      (is (= :rems.workflow.dynamic/approved
             (:state (applications/get-dynamic-application-state app-id)))))))
