(ns ^:integration rems.test.db
  "Namespace for tests that use an actual database."
  (:require [cheshire.core :refer :all]
            [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
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
  (binding [context/*user* {"eppn" "test-user"}]
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

      (is (:id item) "sanity check")

      (testing "get form for catalogue item"
        (let [form-fi (binding [context/*lang* :fi]
                        (applications/get-form-for (:id item)))
              form-en (binding [context/*lang* :en]
                        (applications/get-form-for (:id item)))
              form-ru (binding [context/*lang* :ru]
                        (applications/get-form-for (:id item)))]
          (is (= "entitle" (:title form-en)) "title")
          (is (= ["A" "B" "C"] (map :title (:items form-en))) "items should be in order")
          (is (= "fititle" (:title form-fi)) "title")
          (is (= ["A"] (map :title (:items form-fi))) "there should be only one item")
          (is (= ["Testi lisenssi"] (map :title (:licenses form-fi))) "there should only be one license in Finnish")
          (is (= "http://testi.fi" (:textcontent (first (:licenses form-fi)))) "link should point to Finnish site")
          (is (= "Test license" (:title (first (:licenses form-en)))) "title should be in English")
          (is (= "http://test.com" (:textcontent (first (:licenses form-en)))) "link should point to English site")
          (is (= "non-localized license" (:title (first (:licenses form-ru)))) "default title used when no localization is found")
          (is (= "http://test.org" (:textcontent (first (:licenses form-ru)))) "link should point to default license site")))

      (testing "get partially filled form"
        (binding [context/*lang* :en]
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
          (db/add-application-approval! {:id app-id
                                         :user uid
                                         :comment "comment"
                                         :round 0
                                         :state "rejected"})
          (let [f (applications/get-form-for (:id item) app-id)]
            (is (= app-id (:id (:application f))))
            (is (= "draft" (:state (:application f))))
            (is (= [nil "B" nil] (map :value (:items f))))
            (is (= [true] (map :approved (:licenses f))))
            (is (= [{:comment "comment" :round 0 :state "rejected"}]
                   (:comments f))))

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
              (is (= [nil "X" nil] (map :value (:items f))))))))

      (testing "get submitted form as approver"
        (db/create-workflow-approver! {:wfid (:id wf) :appruserid "approver" :round 0})
        (db/save-license-approval! {:catappid app-id
                                    :licid (:id license)
                                    :actoruserid uid
                                    :round 0
                                    :state "approved"})
        (applications/new-submit-application app-id)
        (binding [context/*user* {"eppn" "approver"}
                  context/*lang* :en]
          (let [form (applications/get-form-for (:id item) app-id)]
            (is (= "applied" (get-in form [:application :state])))
            (is (= [nil "X" nil] (map :value (:items form))))
            (is (get-in form [:licenses 0 :approved]))))))))

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
      (applications/new-submit-application app)
      (is (nil? (applications/get-draft-id-for item)))
      (applications/new-approve-application app 0 "comment")
      (is (nil? (applications/get-draft-id-for item)))
      (is (= [{:id app :state "approved" :catid item}]
             (map #(select-keys % [:id :state :catid])
                  (applications/get-applications)))))))

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

      (applications/new-submit-application app1)
      (applications/new-submit-application app2)

      (applications/new-submit-application app4)
      (binding [context/*user* {"eppn" uid2}]
        (applications/new-approve-application app4 0 ""))
      (applications/new-approve-application app4 1 "")

      (is (= [{:id app1 :state "applied" :catid item1 :curround 0}]
             (map #(select-keys % [:id :state :catid :curround])
                  (applications/get-approvals)))
          "should only see app1")
      (testing "applications/approver?"
        (is (applications/approver? app1))
        (is (not (applications/approver? app2)))
        (is (not (applications/approver? app3)))
        (is (not (applications/approver? app4)))
        (binding [context/*user* {"eppn" uid2}]
          (is (not (applications/approver? app1)))
          (is (applications/approver? app2))))

      ;; move app1 and app2 to round 1
      (applications/new-approve-application app1 0 "")
      (binding [context/*user* {"eppn" uid2}]
        (applications/new-approve-application app2 0 ""))

      (is (= [{:id app2 :state "applied" :catid item2 :curround 1}]
             (map #(select-keys % [:id :state :catid :curround])
                  (applications/get-approvals)))
          "should only see app2")
      (testing "approvals/approver?"
        (is (not (applications/approver? app1)))
        (is (applications/approver? app2))
        (is (not (applications/approver? app3)))
        (is (not (applications/approver? app4)))
        (binding [context/*user* {"eppn" uid2}]
          (is (not (applications/approver? app1)))
          (is (not (applications/approver? app2))))))))

#_
(deftest test-approve
  (binding [context/*user* {"eppn" "tester"}]
    (db/create-resource! {:id 3 :resid "" :prefix "" :modifieruserid 1})
    (db/create-resource! {:id 5 :resid "" :prefix "" :modifieruserid 1})
    (db/create-resource! {:id 7 :resid "" :prefix "" :modifieruserid 1})
    (let [uid (get-user-id)
          uid2 "pekka"
          wfid-a (:id (db/create-workflow! {:owneruserid "" :modifieruserid "" :title "" :fnlround 1}))
          wfid-b (:id (db/create-workflow! {:owneruserid "" :modifieruserid "" :title "" :fnlround 0}))
          wfid-c (:id (db/create-workflow! {:owneruserid "" :modifieruserid "" :title "" :fnlround 1}))
          item-a (:id (db/create-catalogue-item! {:title "" :form nil :resid 3 :wfid wfid-a}))
          item-b (:id (db/create-catalogue-item! {:title "" :form nil :resid 5 :wfid wfid-b}))
          item-c (:id (db/create-catalogue-item! {:title "" :form nil :resid 7 :wfid wfid-c}))
          app-a-1 (applications/create-new-draft item-a)
          app-a-2 (applications/create-new-draft item-a)
          draft (applications/create-new-draft item-a)
          app-b (applications/create-new-draft item-b)
          app-c (applications/create-new-draft item-c)

          get (fn [app-id]
                (let [apps (db/get-applications {:id app-id})]
                  (is (= 1 (count apps)))
                  (select-keys (first apps) [:state :curround])))
          approvals (fn [app-id]
                      (sort-by :round
                               (map #(select-keys % [:catappid :appruserid :round :comment :state])
                                    (db/get-application-approvals {:application app-id}))))]

      (db/create-workflow-approver! {:wfid wfid-a :appruserid uid :round 0})
      (db/create-workflow-approver! {:wfid wfid-a :appruserid uid :round 1})
      (db/create-workflow-approver! {:wfid wfid-b :appruserid uid2 :round 0})
      (db/create-workflow-approver! {:wfid wfid-b :appruserid uid :round 1})

      (doseq [a [app-a-1 app-a-2 app-b]]
        (applications/submit-application a))

      (approvals/approve app-a-1 0 "comment")
      (is (= {:state "applied" :curround 1} (get app-a-1)))
      (is (= [{:catappid app-a-1 :appruserid uid :round 0 :comment "comment" :state "approved"}]
             (approvals app-a-1)))
      (is (= {:state "applied" :curround 0} (get app-a-2)))
      (is (empty? (approvals app-a-2)))
      (is (empty? (db/get-entitlements)))

      (is (thrown? Exception
                   (approvals/approve 0 "comment3"))
          "shouldn't be able to approve same round again")

      (approvals/approve app-a-1 1 "comment2")
      (is (= {:state "approved" :curround 1} (get app-a-1)))
      (is (= [{:catappid app-a-1 :appruserid uid :round 0 :comment "comment" :state "approved"}
              {:catappid app-a-1 :appruserid uid :round 1 :comment "comment2" :state "approved"}]
             (approvals app-a-1)))
      (is (= [{:resid 3 :catappid app-a-1 :userid uid}] (db/get-entitlements)))
      (is (= {:state "applied" :curround 0} (get app-a-2)))
      (is (empty? (approvals app-a-2)))

      (approvals/reject app-a-2 0 "comment4")
      (is (= {:state "rejected" :curround 0} (get app-a-2)))
      (is (= [{:catappid app-a-2 :appruserid uid :round 0 :comment "comment4" :state "rejected"}]
             (approvals app-a-2)))

      (is (thrown? rems.auth.NotAuthorizedException
                   (approvals/approve app-b 0 "comment"))
          "shouldn't be able to approve when not approver")
      (is (thrown? rems.auth.NotAuthorizedException
                   (approvals/approve draft 0 "comment"))
          "shouldn't be able to approve draft")

      (testing "workflow without approvers"
        (applications/submit-application app-c)
        (is (= {:state "approved" :curround 1} (get app-c)))
        (is (= [{:resid 7 :catappid app-c :userid uid}]
               (filter #(= 7 (:resid %)) (db/get-entitlements))))))))

(deftest test-users
  (db/add-user! {:user "pekka", :userattrs nil})
  (db/add-user! {:user "simo", :userattrs nil})
  (is (= 2 (count (db/get-users))))
  (db/add-user! {:user "pekka", :userattrs (generate-string {"key" "value"})})
  (db/add-user! {:user "simo", :userattrs nil})
  (is (= 2 (count (db/get-users))))
  (is (= {"key" "value"} (parse-string (:userattrs(db/get-user-attributes {:user "pekka"}))))))

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
          app1 (applications/create-new-draft item)
          app2 (applications/create-new-draft item)
          get-base (fn [app] (first (db/get-applications {:id app})))
          check (fn [app round state]
                  (is (= (assoc (get-base app) :state state :curround round)
                         (applications/get-application-state app))))]
      (db/create-workflow-approver! {:wfid wf :appruserid uid :round 0})
      (db/create-workflow-approver! {:wfid wf :appruserid "event-test-approver" :round 1})

      (check app1 0 "draft")

      (is (thrown? Exception (applications/new-approve-application app1 0 ""))
          "Should not be able to approve draft")

      (applications/new-submit-application app1)
      (check app1 0 "applied")

      (is (thrown? Exception (applications/new-approve-application app1 1 ""))
          "Should not be able to approve wrong round")

      (applications/new-approve-application app1 0 "")
      (check app1 1 "applied")

      (is (thrown? Exception (applications/new-approve-application app1 1 ""))
          "Should not be able to approve if not approver")

      (binding [context/*user* {"eppn" "event-test-approver"}]
        (applications/new-approve-application app1 1 ""))
      (check app1 1 "approved")

      (check app2 0 "draft")
      (applications/new-submit-application app2)
      (check app2 0 "applied")
      (applications/new-reject-application app2 0 "comment")
      (check app2 0 "rejected"))))
