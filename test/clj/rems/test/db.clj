(ns ^:integration rems.test.db
  "Namespace for tests that use an actual database."
  (:require [rems.db.core :as db]
            [rems.context :as context]
            [rems.contents :as contents]
            [rems.db.applications :as applications]
            [rems.db.approvals :as approvals]
            [rems.db.roles :as roles]
            [rems.env :refer [*db*]]
            [rems.util :refer [get-user-id]]
            [luminus-migrations.core :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [rems.config :refer [env]]
            [mount.core :as mount]
            [conman.core :as conman]
            [cheshire.core :refer :all]))

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
    (conman/with-transaction [rems.env/*db*]
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
                  {:title "B" :type "text" :inputprompt "prompt" :user uid :value 0})]
      (db/link-form-meta! {:meta (:id meta) :form (:id form-en) :lang "en" :user uid})
      (db/link-form-meta! {:meta (:id meta) :form (:id form-fi) :lang "fi" :user uid})
      (db/link-form-item! {:form (:id form-en) :itemorder 2 :item (:id item-b) :user uid})
      (db/link-form-item! {:form (:id form-en) :itemorder 1 :item (:id item-a) :user uid})
      (db/link-form-item! {:form (:id form-en) :itemorder 3 :item (:id item-c) :user uid})
      (db/link-form-item! {:form (:id form-fi) :itemorder 1 :item (:id item-a) :user uid})

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
          (let [app-id (applications/create-new-draft (:id item))]
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
              (is (= app-id (:application f)))
              (is (= "draft" (:state f)))
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
              (is (= 0 (count (db/get-application-license-approval {:catappid app-id
                                                                    :licid (:id license)
                                                                    :actoruserid uid})))
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
                (is (= [nil "X" nil] (map :value (:items f))))))))))))

(deftest test-applications
  (binding [context/*user* {"eppn" "test-user"}]
    (let [uid (get-user-id)
          item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid nil}))
          app (applications/create-new-draft item)]
      (is (= app (applications/get-draft-id-for item)))
      (is (= [{:id app :state "draft" :catid item}]
             (map #(select-keys % [:id :state :catid])
                  (applications/get-applications))))
      (db/update-application-state! {:id app :user uid :state "applied" :curround 0})
      (is (nil? (applications/get-draft-id-for item)))
      (db/update-application-state! {:id app :user uid :state "approved" :curround 0})
      (is (nil? (applications/get-draft-id-for item)))
      (is (= [{:id app :state "approved" :catid item}]
             (map #(select-keys % [:id :state :catid])
                  (applications/get-applications)))))))

(deftest test-approvals
  (binding [context/*user* {"eppn" "test-user"}]
    (let [uid (get-user-id)
          uid2 "another-user"
          wfid1 (:id (db/create-workflow! {:owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 0}))
          wfid2 (:id (db/create-workflow! {:owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 1}))
          _ (db/create-workflow-approver! {:wfid wfid1 :appruserid uid :round 0})
          _ (db/create-workflow-approver! {:wfid wfid2 :appruserid uid :round 1})
          _ (db/create-workflow-approver! {:wfid wfid2 :appruserid uid2 :round 1})
          item1 (:id (db/create-catalogue-item! {:title "item1" :form nil :resid nil :wfid wfid1}))
          item2 (:id (db/create-catalogue-item! {:title "item2" :form nil :resid nil :wfid wfid2}))
          item3 (:id (db/create-catalogue-item! {:title "item3" :form nil :resid nil :wfid wfid1}))
          item4 (:id (db/create-catalogue-item! {:title "item4" :form nil :resid nil :wfid wfid2}))
          app1 (applications/create-new-draft item1) ; should see as approver for round 0
          app2 (applications/create-new-draft item2) ; should see as approver for round 1
          app3 (applications/create-new-draft item3) ; should not see draft
          app4 (applications/create-new-draft item4)] ; should not see approved
      (db/update-application-state! {:id app1 :user uid :state "applied" :curround 0})
      (db/update-application-state! {:id app2 :user uid :state "applied" :curround 0})
      (db/update-application-state! {:id app3 :user uid :state "draft" :curround 0})
      (db/update-application-state! {:id app4 :user uid :state "approved" :curround 0})
      (is (= [{:id app1 :state "applied" :catid item1 :curround 0}]
             (map #(select-keys % [:id :state :catid :curround])
                  (approvals/get-approvals)))
          "should only see app1")
      (testing "approvals/approver?"
        (is (approvals/approver? app1))
        (is (not (approvals/approver? app2)))
        (is (not (approvals/approver? app3)))
        (is (not (approvals/approver? app4)))
        (binding [context/*user* {"eppn" uid2}]
          (is (not (approvals/approver? app1)))
          (is (not (approvals/approver? app2)))))
      (db/update-application-state! {:id app1 :user uid :state "applied" :curround 1})
      (db/update-application-state! {:id app2 :user uid :state "applied" :curround 1})
      (db/update-application-state! {:id app3 :user uid :state "draft" :curround 1})
      (db/update-application-state! {:id app4 :user uid :state "approved" :curround 1})
      (is (= [{:id app2 :state "applied" :catid item2 :curround 1}]
             (map #(select-keys % [:id :state :catid :curround])
                  (approvals/get-approvals)))
          "should only see app2")
      (testing "approvals/approver?"
        (is (not (approvals/approver? app1)))
        (is (approvals/approver? app2))
        (is (not (approvals/approver? app3)))
        (is (not (approvals/approver? app4)))
        (binding [context/*user* {"eppn" uid2}]
          (is (not (approvals/approver? app1)))
          (is (approvals/approver? app2)))))))

(deftest test-approve
  (binding [context/*user* {"eppn" "tester"}]
    (db/create-resource! {:id 3 :resid "" :prefix "" :modifieruserid 1})
    (db/create-resource! {:id 5 :resid "" :prefix "" :modifieruserid 1})
    (let [uid (get-user-id)
          uid2 "pekka"
          wfid-a (:id (db/create-workflow! {:owneruserid "" :modifieruserid "" :title "" :fnlround 1}))
          wfid-b (:id (db/create-workflow! {:owneruserid "" :modifieruserid "" :title "" :fnlround 0}))
          item-a (:id (db/create-catalogue-item! {:title "" :form nil :resid 3 :wfid wfid-a}))
          item-b (:id (db/create-catalogue-item! {:title "" :form nil :resid 5 :wfid wfid-b}))
          app-a-1 (applications/create-new-draft item-a)
          app-a-2 (applications/create-new-draft item-a)
          draft (applications/create-new-draft item-a)
          app-b (applications/create-new-draft item-b)

          get (fn [app-id]
                (let [apps (db/get-applications {:id app-id})]
                  (is (= 1 (count apps)))
                  (select-keys (first apps) [:state :curround])))
          approvals (fn [app-id]
                      (sort-by :round
                               (map #(select-keys % [:catappid :appruserid :round :comment :state])
                                    (db/get-application-approvals {:id app-id}))))]
      (db/create-workflow-approver! {:wfid wfid-a :appruserid uid :round 0})
      (db/create-workflow-approver! {:wfid wfid-a :appruserid uid :round 1})
      (db/create-workflow-approver! {:wfid wfid-b :appruserid uid2 :round 0})
      (db/create-workflow-approver! {:wfid wfid-b :appruserid uid :round 1})

      (doseq [a [app-a-1 app-a-2 app-b]]
        (db/update-application-state! {:id a :user uid :state "applied" :curround 0}))

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

      (is (thrown? rems.auth.NotAuthorizedException
                   (approvals/approve app-b 0 "comment"))
          "shouldn't be able to approve when not approver")
      (is (thrown? rems.auth.NotAuthorizedException
                   (approvals/approve draft 0 "comment"))
          "shouldn't be able to approve draft"))))

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
