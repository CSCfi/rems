(ns ^:integration rems.test.db
  "Namespace for tests that use an actual database."
  (:require [cheshire.core :refer :all]
            [clojure.core.memoize :as memo]
            [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.util :refer [get-user-id]]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'rems.config/env
     #'rems.env/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (f)
    (mount/stop)))

(use-fixtures :each
  (fn [f]
    (conman/with-transaction [rems.env/*db* {:isolation :serializable}]
      (jdbc/db-set-rollback-only! rems.env/*db*)
      (f))))

(deftest test-get-catalogue-items
  (testing "without catalogue items"
    (is (empty? (db/get-catalogue-items))))

  (testing "with test database"
    (db/create-resource! {:id 1 :resid "http://urn.fi/urn:nbn:fi:lb-201403262" :prefix "nbn" :modifieruserid 1})
    (db/create-catalogue-item! {:title "ELFA Corpus" :form nil :resid 1 :wfid nil})
    (db/create-catalogue-item! {:title "B" :form nil :resid nil :wfid nil})
    (is (= ["B" "ELFA Corpus"] (sort (map :title (db/get-catalogue-items))))
        "should find two items")
    (let [item-from-list (second (db/get-catalogue-items))
          item-by-id (db/get-catalogue-item {:id (:id item-from-list)})]
      (is (= (select-keys item-from-list [:id :title])
             (select-keys item-by-id [:id :title]))
          "should find catalogue item by id"))))

(deftest test-form
  (binding [context/*user* {"eppn" "test-user"}
            context/*lang* :en]
    (let [uid (get-user-id)
          meta (db/create-form-meta! {:title "metatitle" :user uid})
          wf (db/create-workflow! {:modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 0})
          license (db/create-license! {:modifieruserid uid :owneruserid uid :title "non-localized license" :type "link" :textcontent "http://test.org"})
          license-fi (db/create-license-localization! {:licid (:id license) :langcode "fi" :title "Testi lisenssi" :textcontent "http://testi.fi"})
          license-en (db/create-license-localization! {:licid (:id license) :langcode "en" :title "Test license" :textcontent "http://test.com"})
          wf-license (db/create-workflow-license! {:wfid (:id wf) :licid (:id license) :round 0})
          item (db/create-catalogue-item! {:title "item" :form (:id meta) :resid nil :wfid (:id wf)})
          form-en (db/create-form! {:title "entitle" :user uid})
          form-fi (db/create-form! {:title "fititle" :user uid})
          item-c (db/create-form-item!
                  {:title "C" :type "text" :inputprompt "prompt" :user uid :value 0})
          item-a (db/create-form-item!
                  {:title "A" :type "text" :inputprompt "prompt" :user uid :value 0})
          item-b (db/create-form-item!
                  {:title "B" :type "text" :inputprompt "prompt" :user uid :value 0})
          app-id (applications/create-new-draft (:id item))]
      (db/link-form-meta! {:meta (:id meta) :form (:id form-en) :lang "en" :user uid})
      (db/link-form-meta! {:meta (:id meta) :form (:id form-fi) :lang "fi" :user uid})
      (db/link-form-item! {:form (:id form-en) :itemorder 2 :item (:id item-b) :user uid :optional false})
      (db/link-form-item! {:form (:id form-en) :itemorder 1 :item (:id item-a) :user uid :optional false})
      (db/link-form-item! {:form (:id form-en) :itemorder 3 :item (:id item-c) :user uid :optional false})
      (db/link-form-item! {:form (:id form-fi) :itemorder 1 :item (:id item-a) :user uid :optional false})

      (db/add-user! {:user uid :userattrs nil})
      (db/create-workflow-approver! {:wfid (:id wf) :appruserid uid :round 0})
      (db/create-catalogue-item-localization! {:id (:id item) :langcode "en" :title "item-en"})
      (db/create-catalogue-item-localization! {:id (:id item) :langcode "fi" :title "item-fi"})

      (is (:id item) "sanity check")

      (testing "get form for catalogue item"
        (with-redefs [catalogue/cached
                      {:localizations (catalogue/load-catalogue-item-localizations!)}]
          (let [form-fi (binding [context/*lang* :fi]
                          (applications/get-form-for (:id item)))
                form-en (binding [context/*lang* :en]
                          (applications/get-form-for (:id item)))
                form-ru (binding [context/*lang* :ru]
                          (applications/get-form-for (:id item)))]
            (is (= "item-en" (:title form-en)) "title")
            (is (= ["A" "B" "C"] (map :title (:items form-en))) "items should be in order")
            (is (= "item-fi" (:title form-fi)) "title")
            (is (= ["A"] (map :title (:items form-fi))) "there should be only one item")
            (is (= ["Testi lisenssi"] (map :title (:licenses form-fi))) "there should only be one license in Finnish")
            (is (= "http://testi.fi" (:textcontent (first (:licenses form-fi)))) "link should point to Finnish site")
            (is (= "Test license" (:title (first (:licenses form-en)))) "title should be in English")
            (is (= "http://test.com" (:textcontent (first (:licenses form-en)))) "link should point to English site")
            (is (= "non-localized license" (:title (first (:licenses form-ru)))) "default title used when no localization is found")
            (is (= "http://test.org" (:textcontent (first (:licenses form-ru)))) "link should point to default license site"))))

      (testing "get partially filled form"
        (is app-id "sanity check")
        (db/save-field-value! {:application app-id
                               :form (:id form-en)
                               :item (:id item-b)
                               :user uid
                               :value "B"})
        (db/save-license-approval! {:catappid app-id
                                    :licid (:id license)
                                    :actoruserid uid
                                    :round 0
                                    :state "approved"})
        (let [f (applications/get-form-for (:id item) app-id)]
          (is (= app-id (:id (:application f))))
          (is (= "draft" (:state (:application f))))
          (is (= [nil "B" nil] (map :value (:items f))))
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
          (let [f (applications/get-form-for (:id item) app-id)]
            (is (= [false] (map :approved (:licenses f))))))
        (testing "reset field value"
          (db/clear-field-value! {:application app-id
                                  :form (:id form-en)
                                  :item (:id item-b)})
          (db/save-field-value! {:application app-id
                                 :form (:id form-en)
                                 :item (:id item-b)
                                 :user uid
                                 :value "X"})
          (let [f (applications/get-form-for (:id item) app-id)]
            (is (= [nil "X" nil] (map :value (:items f)))))))

      (testing "get submitted form as approver"
        (db/create-workflow-approver! {:wfid (:id wf) :appruserid "approver" :round 0})
        (db/save-license-approval! {:catappid app-id
                                    :licid (:id license)
                                    :actoruserid uid
                                    :round 0
                                    :state "approved"})
        (applications/submit-application app-id)
        (binding [context/*user* {"eppn" "approver"}]
          (let [form (applications/get-form-for (:id item) app-id)]
            (is (= "applied" (get-in form [:application :state])))
            (is (= [nil "X" nil] (map :value (:items form))))
            (is (get-in form [:licenses 0 :approved])))))

      (testing "get approved form as applicant"
        (db/add-user! {:user "approver" :userattrs nil})
        (binding [context/*user* {"eppn" "approver"}]
          (applications/approve-application app-id 0 "comment"))
        (let [form (applications/get-form-for (:id item) app-id)]
          (is (= "approved" (get-in form [:application :state])))
          (is (= [nil "X" nil] (map :value (:items form))))
          (is (= [nil "comment"]
                 (->> form :application :events (map :comment)))))))))

(deftest test-applications
  (binding [context/*user* {"eppn" "test-user"}]
    (db/add-user! {:user "test-user" :userattrs nil})
    (let [uid (get-user-id)
          wf (:id (db/create-workflow! {:owneruserid uid :modifieruserid uid :title "" :fnlround 0}))
          item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
          app (applications/create-new-draft item)]
      (db/create-workflow-approver! {:wfid wf :appruserid uid :round 0})

      (is (= app (applications/get-draft-id-for item)))
      (is (= [{:id app :state "draft" :catid item}]
             (map #(select-keys % [:id :state :catid])
                  (applications/get-applications))))
      (applications/submit-application app)
      (is (nil? (applications/get-draft-id-for item)))
      (applications/approve-application app 0 "comment")
      (is (nil? (applications/get-draft-id-for item)))
      (is (= [{:id app :state "approved" :catid item}]
             (map #(select-keys % [:id :state :catid])
                  (applications/get-applications)))))))

(deftest test-phases
  (binding [context/*user* {"eppn" "applicant"}]
    ;; TODO add review when reviewing is supported
    (db/add-user! {:user "approver1" :userattrs nil})
    (db/add-user! {:user "approver2" :userattrs nil})
    (db/add-user! {:user "applicant" :userattrs nil})
    (testing "approval flow"
      (let [wf (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
            item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
            app (applications/create-new-draft item)
            get-phases (fn [] (applications/get-application-phases (:state (applications/get-application-state app))))]
        (db/create-workflow-approver! {:wfid wf :appruserid "approver1" :round 0})
        (db/create-workflow-approver! {:wfid wf :appruserid "approver2" :round 1})

        (testing "initially the application is in draft phase"
          (is (= [{:phase :apply :active? true :text :t.phases/apply}
                  {:phase :approve :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (applications/submit-application app)

        (testing "after submission the application is in first approval round"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :active? true :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (binding [context/*user* {"eppn" "approver1"}]
          (applications/approve-application app 0 "it's good"))

        (testing "after first approval the application is in the second approval round"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :active? true :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (binding [context/*user* {"eppn" "approver2"}]
          (applications/approve-application app 1 "it's good"))

        (testing "after both approvals the application is in approved phase"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :completed? true :approved? true :text :t.phases/approve}
                  {:phase :result :completed? true :approved? true :text :t.phases/approved}]
                 (get-phases))))
        ))

    (testing "return flow"
      (let [wf (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
            item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
            app (applications/create-new-draft item)
            get-phases (fn [] (applications/get-application-phases (:state (applications/get-application-state app))))]
        (db/create-workflow-approver! {:wfid wf :appruserid "approver1" :round 0})
        (db/create-workflow-approver! {:wfid wf :appruserid "approver2" :round 1})

        (testing "initially the application is in draft phase"
          (is (= [{:phase :apply :active? true :text :t.phases/apply}
                  {:phase :approve :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (applications/submit-application app)

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
                 (get-phases))))
        ))

    (testing "rejection flow"
      (let [wf (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
            item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
            app (applications/create-new-draft item)
            get-phases (fn [] (applications/get-application-phases (:state (applications/get-application-state app))))]
        (db/create-workflow-approver! {:wfid wf :appruserid "approver1" :round 0})
        (db/create-workflow-approver! {:wfid wf :appruserid "approver2" :round 1})

        (testing "initially the application is in draft phase"
          (is (= [{:phase :apply :active? true :text :t.phases/apply}
                  {:phase :approve :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (applications/submit-application app)

        (testing "after submission the application is in first approval round"
          (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                  {:phase :approve :active? true :text :t.phases/approve}
                  {:phase :result :text :t.phases/approved}]
                 (get-phases))))

        (binding [context/*user* {"eppn" "approver1"}]
          (applications/approve-application app 0 "it's good"))

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
                 (get-phases))))
        ))
    ))

(deftest test-get-approvals
  (binding [context/*user* {"eppn" "test-user"}]
    (let [uid (get-user-id)
          uid2 "another-user"
          wfid1 (:id (db/create-workflow! {:owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 0}))
          wfid2 (:id (db/create-workflow! {:owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 1}))
          _ (db/create-workflow-approver! {:wfid wfid1 :appruserid uid :round 0})
          _ (db/create-workflow-approver! {:wfid wfid2 :appruserid uid2 :round 0})
          _ (db/create-workflow-approver! {:wfid wfid2 :appruserid uid :round 1})
          item1 (:id (db/create-catalogue-item! {:title "item1" :form nil :resid nil :wfid wfid1}))
          item2 (:id (db/create-catalogue-item! {:title "item2" :form nil :resid nil :wfid wfid2}))
          item3 (:id (db/create-catalogue-item! {:title "item3" :form nil :resid nil :wfid wfid1}))
          item4 (:id (db/create-catalogue-item! {:title "item4" :form nil :resid nil :wfid wfid2}))
          app1 (applications/create-new-draft item1) ; should see as approver for round 0
          app2 (applications/create-new-draft item2) ; should see as approver for round 1
          app3 (applications/create-new-draft item3) ; should not see draft
          app4 (applications/create-new-draft item4)] ; should not see approved
      (db/add-user! {:user uid :userattrs nil})
      (db/add-user! {:user uid2 :userattrs nil})

      (applications/submit-application app1)
      (applications/submit-application app2)

      (applications/submit-application app4)
      (binding [context/*user* {"eppn" uid2}]
        (applications/approve-application app4 0 ""))
      (applications/approve-application app4 1 "")

      (is (= [{:id app1 :state "applied" :catid item1 :curround 0}]
             (map #(select-keys % [:id :state :catid :curround])
                  (applications/get-approvals)))
          "should only see app1")
      (testing "applications/can-approve?"
        (is (applications/can-approve? app1))
        (is (not (applications/can-approve? app2)))
        (is (not (applications/can-approve? app3)))
        (is (not (applications/can-approve? app4)))
        (binding [context/*user* {"eppn" uid2}]
          (is (not (applications/can-approve? app1)))
          (is (applications/can-approve? app2))))

      ;; move app1 and app2 to round 1
      (applications/approve-application app1 0 "")
      (binding [context/*user* {"eppn" uid2}]
        (applications/approve-application app2 0 ""))

      (is (= [{:id app2 :state "applied" :catid item2 :curround 1}]
             (map #(select-keys % [:id :state :catid :curround])
                  (applications/get-approvals)))
          "should only see app2")
      (testing "applications/can-approve?"
        (is (not (applications/can-approve? app1)))
        (is (applications/can-approve? app2))
        (is (not (applications/can-approve? app3)))
        (is (not (applications/can-approve? app4)))
        (binding [context/*user* {"eppn" uid2}]
          (is (not (applications/can-approve? app1)))
          (is (not (applications/can-approve? app2))))))))

;; TODO test handled approvals

(deftest test-users
  (db/add-user! {:user "pekka", :userattrs nil})
  (db/add-user! {:user "simo", :userattrs nil})
  (is (= 2 (count (db/get-users))))
  (db/add-user! {:user "pekka", :userattrs (generate-string {"key" "value"})})
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
  (is (empty? (roles/get-roles "juho")))
  (is (thrown? RuntimeException (roles/add-role! "pekka" :unknown-role)))

  (is (= :applicant (roles/get-active-role "pekka")) "applicant is the default active role")
  (roles/set-active-role! "pekka" :applicant)
  (is (= :applicant (roles/get-active-role "pekka")))
  (roles/set-active-role! "pekka" :reviewer)
  (is (= :reviewer (roles/get-active-role "pekka")))
  ;; a sql constraint violation causes the current transaction to go
  ;; to aborted state, thus we test this last
  (is (thrown? Exception (roles/set-active-role! "pekka" :approver))))

(deftest test-application-events
  (binding [context/*user* {"eppn" "event-test"}]
    (db/add-user! {:user "event-test", :userattrs nil})
    (db/add-user! {:user "event-test-approver", :userattrs nil})
    (let [uid (get-user-id)
          wf (:id (db/create-workflow! {:modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 1}))
          item (:id (db/create-catalogue-item! {:title "A" :form nil :resid nil :wfid wf}))
          fetch (fn [app] (select-keys (applications/get-application-state app)
                                       [:state :curround]))]
      (db/create-workflow-approver! {:wfid wf :appruserid uid :round 0})
      (db/create-workflow-approver! {:wfid wf :appruserid "event-test-approver" :round 1})

      (testing "submitting, approving"
        (let [app (applications/create-new-draft item)]
          (is (= (fetch app) {:curround 0 :state "draft"}))

          (is (thrown? Exception (applications/approve-application app 0 ""))
              "Should not be able to approve draft")

          (binding [context/*user* {"eppn" "event-test-approver"}]
            (is (thrown? Exception (applications/submit-application app))
                "Should not be able to submit when not applicant"))

          (applications/submit-application app)
          (is (= (fetch app) {:curround 0 :state "applied"}))

          (applications/try-autoapprove-application app)
          (is (= (fetch app) {:curround 0 :state "applied"})
              "Autoapprove should do nothing")

          (is (thrown? Exception (applications/submit-application app))
              "Should not be able to submit twice")

          (is (thrown? Exception (applications/approve-application app 1 ""))
              "Should not be able to approve wrong round")

          (applications/approve-application app 0 "c1")
          (is (= (fetch app) {:curround 1 :state "applied"}))

          (is (thrown? Exception (applications/approve-application app 1 ""))
              "Should not be able to approve if not approver")

          (binding [context/*user* {"eppn" "event-test-approver"}]
            (applications/approve-application app 1 "c2"))
          (is (= (fetch app) {:curround 1 :state "approved"}))

          (is (= (->> (applications/get-application-state app)
                      :events
                      (map #(select-keys % [:round :event :comment])))
                 [{:round 0 :event "apply" :comment nil}
                  {:round 0 :event "approve" :comment "c1"}
                  {:round 1 :event "approve" :comment "c2"}]))))

      (testing "rejecting"
        (let [app (applications/create-new-draft item)]
          (is (= (fetch app) {:curround 0 :state "draft"}))
          (applications/submit-application app)
          (is (= (fetch app) {:curround 0 :state "applied"}))
          (applications/reject-application app 0 "comment")
          (is (= (fetch app) {:curround 0 :state "rejected"}))))

      (testing "returning, resubmitting"
        (let [app (applications/create-new-draft item)]
          (applications/submit-application app)

          (binding [context/*user* {"eppn" "event-test-approver"}]
            (is (thrown? Exception (applications/return-application app 0 "comment"))
                "Should not be able to return when not approver"))

          (applications/return-application app 0 "comment")
          (is (= (fetch app) {:curround 0 :state "returned"}))

          (binding [context/*user* {"eppn" "event-test-approver"}]
            (is (thrown? Exception (applications/submit-application app))
                "Should not be able to resubmit when not approver"))

          (applications/submit-application app)
          (is (= (fetch app) {:curround 0 :state "applied"}))))

      (testing "autoapprove"
        (let [auto-wf (:id (db/create-workflow! {:modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 1}))
              auto-item (:id (db/create-catalogue-item! {:title "A" :form nil :resid nil :wfid auto-wf}))
              auto-app (applications/create-new-draft auto-item)]
          (is (= (fetch auto-app) {:curround 0 :state "draft"}))
          (applications/submit-application auto-app)
          (is (= (fetch auto-app) {:curround 1 :state "approved"}))
          (is (= (->> (applications/get-application-state auto-app)
                      :events
                      (map #(select-keys % [:round :event])))
                 [{:round 0 :event "apply"}
                  {:round 0 :event "autoapprove"}
                  {:round 1 :event "autoapprove"}])))))))
