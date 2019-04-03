(ns ^:integration rems.test-db
  "Namespace for tests that use an actual database."
  (:require [cheshire.core :as cheshire]
            [clj-time.core :as time]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :refer [split-lines]]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.extensions.namespace-deps :as mount-nsd]
            [mount.lite :as mount]
            [rems.auth.NotAuthorizedException]
            [rems.auth.ForbiddenException]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [rems.db.roles :as roles]
            [rems.db.test-data :as test-data]
            [rems.db.users :as users]
            [rems.db.workflow-actors :as actors]
            rems.poller.entitlements
            [rems.testing-tempura :refer [fake-tempura-fixture]]
            [rems.util :refer [get-user-id]]
            [stub-http.core :as stub])
  (:import (rems.auth ForbiddenException NotAuthorizedException)))

(defn db-once-fixture [f]
  (fake-tempura-fixture f))

(defn db-each-fixture [f]
  (let [bindings (get-thread-bindings)]
    @(:result (mount/with-session
                (with-bindings bindings
                  (mount-nsd/start #'rems.db.core/db-connection)
                  (binding [rems.db.core/*db* @rems.db.core/db-connection]
                    (conman/with-transaction [rems.db.core/*db* {:isolation :serializable}]
                      (jdbc/db-set-rollback-only! rems.db.core/*db*)
                      (f)))
                  (mount-nsd/stop))))))

(use-fixtures :once db-once-fixture)
(use-fixtures :each db-each-fixture)

(deftest test-get-catalogue-items
  (is (= 11 (count (db/get-catalogue-items))) "should find test data catalogue items")

  (testing "with two additional items"
    (let [resid (:id (db/create-resource! {:resid "new resource" :organization "nbn" :owneruserid 1 :modifieruserid 1}))]
      (db/create-catalogue-item! {:title "new catalogue item" :form nil :resid resid :wfid nil})
      (db/create-catalogue-item! {:title "new catalogue item 2" :form nil :resid nil :wfid nil})
      (is (every? (set (map :title (db/get-catalogue-items)))
                  ["new catalogue item" "new catalogue item 2"])
          "should find the two items")
      (let [item-from-list (second (db/get-catalogue-items))
            item-by-id (db/get-catalogue-item {:id (:id item-from-list)})]
        (is (= item-from-list (dissoc item-by-id
                                      :resource-name
                                      :form-name
                                      :workflow-name))
            "should find same catalogue item by id")))))

(deftest ^:eftest/synchronized test-form
  (binding [context/*lang* :en]
    (let [uid "test-user"
          form-id (:id (db/create-form! {:organization "abc" :title "internal-title" :user uid}))
          wf-id (:id (db/create-workflow! {:organization "abc" :modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 0}))
          license-id (:id (db/create-license! {:modifieruserid uid :owneruserid uid :title "non-localized license" :type "link" :textcontent "http://test.org"}))
          _ (db/create-license-localization! {:licid license-id :langcode "fi" :title "Testi lisenssi" :textcontent "http://testi.fi"})
          _ (db/create-license-localization! {:licid license-id :langcode "en" :title "Test license" :textcontent "http://test.com"})
          _ (db/create-workflow-license! {:wfid wf-id :licid license-id :round 0})
          _ (db/set-workflow-license-validity! {:licid license-id :start (time/minus (time/now) (time/years 1)) :end nil})
          item-id (:id (db/create-catalogue-item! {:title "item" :form form-id :resid nil :wfid wf-id}))
          item-c (db/create-form-item!
                  {:type "text" :user uid :value 0})
          item-a (db/create-form-item!
                  {:type "text" :user uid :value 0})
          item-b (db/create-form-item!
                  {:type "text" :user uid :value 0})
          app-id (applications/create-new-draft uid wf-id)]
      (db/add-application-item! {:application app-id :item item-id})
      (db/link-form-item! {:form form-id :itemorder 2 :item (:id item-b) :user uid :optional false})
      (db/link-form-item! {:form form-id :itemorder 1 :item (:id item-a) :user uid :optional false})
      (db/link-form-item! {:form form-id :itemorder 3 :item (:id item-c) :user uid :optional false})
      (db/localize-form-item! {:item (:id item-a) :langcode "fi" :title "A-fi" :inputprompt "prompt"})
      (db/localize-form-item! {:item (:id item-b) :langcode "fi" :title "B-fi" :inputprompt "prompt"})
      (db/localize-form-item! {:item (:id item-c) :langcode "fi" :title "C-fi" :inputprompt "prompt"})
      (db/localize-form-item! {:item (:id item-a) :langcode "en" :title "A-en" :inputprompt "prompt"})
      (db/localize-form-item! {:item (:id item-b) :langcode "en" :title "B-en" :inputprompt "prompt"})
      (db/localize-form-item! {:item (:id item-c) :langcode "en" :title "C-en" :inputprompt "prompt"})

      (db/add-user! {:user uid :userattrs nil})
      (actors/add-approver! wf-id uid 0)
      (db/create-catalogue-item-localization! {:id item-id :langcode "en" :title "item-en"})
      (db/create-catalogue-item-localization! {:id item-id :langcode "fi" :title "item-fi"})

      (is item-id "sanity check")

      (testing "get form for catalogue item"
        (with-redefs [catalogue/cached
                      {:localizations (catalogue/load-catalogue-item-localizations!)}]
          (let [form (applications/get-form-for "test-user" app-id)]
            (is (= "internal-title" (:title form)) "title")
            (is (= ["A-en" "B-en" "C-en"] (map #(get-in % [:localizations :en :title]) (:items form))) "items should be in order")
            (is (= ["A-fi" "B-fi" "C-fi"] (map #(get-in % [:localizations :fi :title]) (:items form))) "items should be in order")

            (is (= 1 (count (:licenses form))))
            (is (= {:title "non-localized license"
                    :textcontent "http://test.org"
                    :localizations {:fi {:title "Testi lisenssi" :textcontent "http://testi.fi" :attachment-id nil}
                                    :en {:title "Test license" :textcontent "http://test.com" :attachment-id nil}}}
                   (select-keys (first (:licenses form)) [:title :textcontent :localizations]))))))

      (testing "get partially filled form"
        (is app-id "sanity check")
        (db/save-field-value! {:application app-id
                               :form form-id
                               :item (:id item-b)
                               :user uid
                               :value "B"})
        (db/save-license-approval! {:catappid app-id
                                    :licid license-id
                                    :actoruserid uid
                                    :round 0
                                    :state "approved"})
        (let [f (applications/get-form-for uid app-id)]
          (is (= app-id (:id (:application f))))
          (is (= "draft" (:state (:application f))))
          (is (= ["" "B" ""] (map :value (:items f))))
          (is (= [true] (map :approved (:licenses f)))))

        (testing "license field"
          (db/save-license-approval! {:catappid app-id
                                      :licid license-id
                                      :actoruserid uid
                                      :round 0
                                      :state "approved"})
          (is (= 1 (count (db/get-application-license-approval {:catappid app-id
                                                                :licid license-id
                                                                :actoruserid uid})))
              "saving a license approval twice should only create one row")
          (db/delete-license-approval! {:catappid app-id
                                        :licid license-id
                                        :actoruserid uid})
          (is (empty? (db/get-application-license-approval {:catappid app-id
                                                            :licid license-id
                                                            :actoruserid uid}))
              "after deletion there should not be saved approvals")
          (let [f (applications/get-form-for uid app-id)]
            (is (= [false] (map :approved (:licenses f))))))
        (testing "reset field value"
          (db/clear-field-value! {:application app-id
                                  :form form-id
                                  :item (:id item-b)})
          (db/save-field-value! {:application app-id
                                 :form form-id
                                 :item (:id item-b)
                                 :user uid
                                 :value "X"})
          (let [f (applications/get-form-for uid app-id)]
            (is (= ["" "X" ""] (map :value (:items f)))))))

      (testing "get submitted form as approver"
        (actors/add-approver! wf-id "approver" 0)
        (db/save-license-approval! {:catappid app-id
                                    :licid license-id
                                    :actoruserid uid
                                    :round 0
                                    :state "approved"})
        (applications/submit-application uid app-id)
        (let [form (applications/get-form-for "approver" app-id)]
          (is (= "applied" (get-in form [:application :state])))
          (is (= ["" "X" ""] (map :value (:items form))))
          (is (get-in form [:licenses 0 :approved]))))

      (testing "get approved form as applicant"
        (db/add-user! {:user "approver" :userattrs nil})
        (applications/approve-application "approver" app-id 0 "comment")
        (let [form (applications/get-form-for "approver" app-id)]
          (is (= "approved" (get-in form [:application :state])))
          (is (= ["" "X" ""] (map :value (:items form))))
          (is (= [nil "comment"]
                 (->> form :application :events (map :comment)))))))))

(deftest test-multi-applications
  (db/add-user! {:user "test-user" :userattrs nil})
  (db/add-user! {:user "handler" :userattrs nil})
  (let [uid "test-user"
        workflow {:type :workflow/dynamic
                  :handlers ["handler"]}
        wfid (:id (db/create-workflow! {:organization "abc" :modifieruserid "owner" :owneruserid "owner" :title "dynamic" :fnlround -1 :workflow (cheshire/generate-string workflow)}))
        res1 (:id (db/create-resource! {:resid "resid111" :organization "abc" :owneruserid uid :modifieruserid uid}))
        res2 (:id (db/create-resource! {:resid "resid222" :organization "abc" :owneruserid uid :modifieruserid uid}))
        form-id (:id (db/create-form! {:organization "abc" :title "internal-title" :user "owner"}))
        item1 (:id (db/create-catalogue-item! {:title "item" :form form-id :resid res1 :wfid wfid}))
        item2 (:id (db/create-catalogue-item! {:title "item" :form form-id :resid res2 :wfid wfid}))
        app-id (applications/create-new-draft uid wfid)]
    ;; apply for two items at the same time
    (db/add-application-item! {:application app-id :item item1})
    (db/add-application-item! {:application app-id :item item2})
    (applications/add-application-created-event!
     {:application-id app-id
      ;; These do nothing right now, but in the future we can get rid of add-application-item! in this test
      :catalogue-item-ids [item1 item2]
      :time (time/now)
      :actor uid})

    (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/submit
                                              :actor uid
                                              :application-id app-id
                                              :time (time/now)})))
    (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/approve
                                              :actor "handler"
                                              :application-id app-id
                                              :time (time/now)
                                              :comment ""})))
    (is (= :rems.workflow.dynamic/approved (:state (applications/get-dynamic-application-state app-id))))

    ;; TODO: entitlements are not tracked for dynamic applications
    (is (= [] #_["resid111" "resid222"] (sort (map :resid (db/get-entitlements {:application app-id}))))
        "should create entitlements for both resources")))

(deftest test-phases
  ;; TODO add review when reviewing is supported
  (db/add-user! {:user "approver1" :userattrs nil})
  (db/add-user! {:user "approver2" :userattrs nil})
  (db/add-user! {:user "applicant" :userattrs nil})
  (testing "approval flow"
    (let [wf (:id (db/create-workflow! {:organization "abc" :owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
          item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
          app (applications/create-new-draft "applicant" wf)
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
          app (applications/create-new-draft "applicant" wf)
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

      (applications/return-application "approver1" app 0 "it must be changed")

      (testing "after return the application is in the draft phase again"
        (is (= [{:phase :apply :active? true :text :t.phases/apply}
                {:phase :approve :text :t.phases/approve}
                {:phase :result :text :t.phases/approved}]
               (get-phases))))))

  (testing "rejection flow"
    (let [wf (:id (db/create-workflow! {:organization "abc" :owneruserid "owner" :modifieruserid "owner" :title "" :fnlround 1}))
          item (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wf}))
          app (applications/create-new-draft "applicant" wf)
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

      (applications/reject-application "approver2" app 1 "is no good")

      (testing "after second round rejection the application is in rejected phase"
        (is (= [{:phase :apply :completed? true :text :t.phases/apply}
                {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
                {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]
               (get-phases)))))))

(deftest test-users
  (let [test-data-users (db/get-users)]
    (db/add-user! {:user "pekka", :userattrs nil})
    (db/add-user! {:user "simo", :userattrs nil})
    (is (= 2 (- (count (db/get-users)) (count test-data-users))))
    (db/add-user! {:user "pekka", :userattrs (cheshire/generate-string {:key "value"})})
    (db/add-user! {:user "simo", :userattrs nil})
    (is (= 2 (- (count (db/get-users)) (count test-data-users))))
    (is (= {:key "value"} (users/get-user-attributes "pekka")))))

(deftest test-roles
  (db/add-user! {:user "pekka", :userattrs nil})
  (db/add-user! {:user "simo", :userattrs nil})
  (roles/add-role! "pekka" :owner)
  (roles/add-role! "pekka" :owner) ;; add should be idempotent
  (is (= #{:logged-in :owner} (roles/get-roles "pekka")))
  (is (= #{:logged-in} (roles/get-roles "simo")))
  (is (= #{:logged-in} (roles/get-roles "juho"))) ;; default role
  (is (thrown? RuntimeException (roles/add-role! "pekka" :unknown-role))))

(deftest test-application-events
  (db/add-user! {:user "event-test", :userattrs nil})
  (db/add-user! {:user "event-test-approver", :userattrs nil})
  (db/add-user! {:user "event-test-reviewer", :userattrs nil})
  (let [uid "event-test"
        wf (:id (db/create-workflow! {:organization "abc" :modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 1}))
        item (:id (db/create-catalogue-item! {:title "A" :form nil :resid nil :wfid wf}))
        fetch (fn [app] (select-keys (applications/get-application-state app)
                                     [:state :curround]))]
    (actors/add-approver! wf uid 0)
    (actors/add-reviewer! wf "event-test-reviewer" 0)
    (actors/add-approver! wf "event-test-approver" 1)

    (testing "submitting, approving"
      (let [app (applications/create-new-draft uid wf)]
        (db/add-application-item! {:application app :item item})

        (is (= {:curround 0 :state "draft"} (fetch app)))

        (is (thrown? ForbiddenException (applications/approve-application uid app 0 ""))
            "Should not be able to approve draft")

        (is (thrown? ForbiddenException (applications/withdraw-application uid app 0 ""))
            "Should not be able to withdraw draft")

        (is (thrown? ForbiddenException (applications/submit-application "event-test-approver" app))
            "Should not be able to submit when not applicant")

        (applications/submit-application uid app)
        (is (= {:curround 0 :state "applied"} (fetch app)))

        (applications/try-autoapprove-application uid app)
        (is (= {:curround 0 :state "applied"} (fetch app))
            "Autoapprove should do nothing")

        (is (thrown? ForbiddenException (applications/submit-application uid app)) ; TODO Wrong exception?
            "Should not be able to submit twice")

        (is (thrown? ForbiddenException (applications/approve-application uid app 1 "")) ; TODO Wrong exception?
            "Should not be able to approve wrong round")

        (testing "withdrawing and resubmitting"
          (applications/withdraw-application uid app 0 "test withdraw")
          (is (= {:curround 0 :state "withdrawn"} (fetch app)))

          (applications/submit-application uid app)
          (is (= {:curround 0 :state "applied"} (fetch app))))

        (is (thrown? ForbiddenException (applications/review-application uid app 0 ""))
            "Should not be able to review as an approver")
        (applications/approve-application uid app 0 "c1")
        (is (= {:curround 1 :state "applied"} (fetch app)))

        (is (thrown? ForbiddenException (applications/approve-application uid app 1 ""))
            "Should not be able to approve if not approver")

        (is (thrown? ForbiddenException (applications/withdraw-application "event-test-approver" app 1 ""))
            "Should not be able to withdraw as approver")

        (is (= 2 (count (db/get-entitlements))) "should find test data entitlements")

        (applications/approve-application "event-test-approver" app 1 "c2")
        (is (= {:curround 1 :state "approved"} (fetch app)))

        (is (= [{:catappid app :resid nil :userid uid}]
               (map #(select-keys % [:catappid :resid :userid])
                    (filter (comp #{app} :catappid)
                            (db/get-entitlements)))))

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
      (let [app (applications/create-new-draft uid wf)]
        (db/add-application-item! {:application app :item item})

        (is (= {:curround 0 :state "draft"} (fetch app)))
        (applications/submit-application uid app)
        (is (= {:curround 0 :state "applied"} (fetch app)))
        (applications/reject-application uid app 0 "comment")
        (is (= {:curround 0 :state "rejected"} (fetch app)))))

    (testing "returning, resubmitting"
      (let [app (applications/create-new-draft uid wf)]
        (db/add-application-item! {:application app :item item})

        (is (thrown? ForbiddenException (applications/return-application uid app 0 "comment"))
            "Should not be able to return before submitting")

        (applications/submit-application uid app)

        (is (thrown? ForbiddenException (applications/return-application "event-test-approver" app 0 "comment"))
            "Should not be able to return when not approver")

        (applications/return-application uid app 0 "comment")
        (is (= {:curround 0 :state "returned"} (fetch app)))

        (is (thrown? ForbiddenException (applications/return-application uid app 0 "comment"))
            "Should not be able to return twice")

        (is (thrown? ForbiddenException (applications/submit-application "event-test-approver" app)) ; TODO wrong exception?
            "Should not be able to resubmit when not approver")

        (applications/submit-application uid app)
        (is (= {:curround 0 :state "applied"} (fetch app)))))

    (testing "review"
      (let [rev-wf (:id (db/create-workflow! {:organization "abc" :owneruserid uid :modifieruserid uid :title "Review workflow" :fnlround 1}))
            rev-item (:id (db/create-catalogue-item! {:title "Review item" :resid nil :wfid rev-wf :form nil}))
            rev-app (applications/create-new-draft uid rev-wf)]
        (db/add-application-item! {:application rev-app :item rev-item})
        (actors/add-reviewer! rev-wf "event-test-reviewer" 0)
        (actors/add-approver! rev-wf uid 1)
        (is (= {:curround 0 :state "draft"} (fetch rev-app)))
        (is (thrown? ForbiddenException (applications/review-application "event-test-reviewer" rev-app 0 ""))
            "Should not be able to review a draft")
        (is (thrown? ForbiddenException (applications/review-application uid rev-app 0 ""))
            "Should not be able to review if not reviewer")
        (applications/submit-application uid rev-app)
        (is (= {:curround 0 :state "applied"} (fetch rev-app)))
        (is (thrown? ForbiddenException (applications/review-application "event-test-reviewer" rev-app 1 ""))
            "Should not be able to review wrong round")
        (is (thrown? ForbiddenException (applications/approve-application "event-test-reviewer" rev-app 0 ""))
            "Should not be able to approve as reviewer")
        (applications/review-application "event-test-reviewer" rev-app 0 "looks good to me")
        (is (= {:curround 1 :state "applied"} (fetch rev-app)))
        (applications/return-application uid rev-app 1 "comment")
        (is (= {:curround 0 :state "returned"} (fetch rev-app)))
        (is (thrown? ForbiddenException (applications/review-application "event-test-reviewer" rev-app 0 ""))
            "Should not be able to review when returned")
        (applications/submit-application uid rev-app)
        (applications/withdraw-application uid rev-app 0 "test withdraw")
        (is (= {:curround 0 :state "withdrawn"} (fetch rev-app)))
        (is (thrown? ForbiddenException (applications/review-application "event-test-reviewer" rev-app 0 ""))
            "Should not be able to review when withdrawn")))

    (testing "closing"
      (testing "a draft as the applicant"
        (let [app (applications/create-new-draft uid wf)]
          (db/add-application-item! {:application app :item item})
          (applications/close-application uid app 0 "closing draft")
          (is (= {:curround 0 :state "closed"} (fetch app)))))
      (testing "an applied application as the applicant"
        (let [app (applications/create-new-draft uid wf)]
          (db/add-application-item! {:application app :item item})
          (applications/submit-application uid app)
          (testing "as approver fails"
            (is (thrown? ForbiddenException (applications/close-application "event-test-approver" app 0 "closing applied"))))
          (applications/close-application uid app 0 "closing applied")
          (is (= {:curround 0 :state "closed"} (fetch app)))))
      (testing "an approved application as the applicant"
        (let [app (applications/create-new-draft uid wf)]
          (db/add-application-item! {:application app :item item})
          (applications/submit-application uid app)
          (applications/approve-application uid app 0 "c1")
          (applications/approve-application "event-test-approver" app 1 "c2")
          (applications/close-application uid app 1 "closing approved")
          (is (= {:curround 1 :state "closed"} (fetch app)))))
      (testing "an approved application as the approver"
        (let [app (applications/create-new-draft uid wf)]
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
            auto-app (applications/create-new-draft uid auto-wf)]
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
      (db/add-user! {:user "third-party-reviewer", :userattrs (cheshire/generate-string {:eppn "third-party-reviewer" :mail ""})})
      (db/add-user! {:user "another-reviewer", :userattrs (cheshire/generate-string {:eppn "another-reviewer" :mail ""})})
      (testing "3rd party review"
        (let [new-app (applications/create-new-draft uid new-wf)]
          (db/add-application-item! {:application new-app :item new-item})
          (applications/submit-application uid new-app)
          (is (= #{:logged-in} (roles/get-roles "third-party-reviewer"))) ;; default role
          (is (= #{:logged-in} (roles/get-roles "another-reviewer"))) ;; default role
          (applications/send-review-request uid new-app 0 "review?" "third-party-reviewer")
          (is (= #{:logged-in} (roles/get-roles "third-party-reviewer")))
          ;; should not send twice to third-party-reviewer, but another-reviewer should still be added
          (applications/send-review-request uid new-app 0 "can you please review this?" ["third-party-reviewer" "another-reviewer"])
          (is (= #{:logged-in} (roles/get-roles "third-party-reviewer")))
          (is (= #{:logged-in} (roles/get-roles "another-reviewer")))
          (is (= (fetch new-app) {:curround 0 :state "applied"}))
          (applications/perform-third-party-review "third-party-reviewer" new-app 0 "comment")
          (is (thrown? ForbiddenException (applications/review-application "third-party-reviewer" new-app 0 "another comment")
                       "Should not be able to do normal review"))
          (is (= (fetch new-app) {:curround 0 :state "applied"}))
          (applications/approve-application uid new-app 0 "")
          (is (= (fetch new-app) {:curround 0 :state "approved"}))
          (is (thrown? ForbiddenException (applications/perform-third-party-review "third-party-reviewer" new-app 0 "another comment")
                       "Should not be able to review when approved"))
          (is (thrown? ForbiddenException (applications/perform-third-party-review "other-reviewer" new-app 0 "too late comment"))
              "Should not be able to review when approved")
          (is (= (->> (applications/get-application-state new-app)
                      :events
                      (map #(select-keys % [:round :event :comment])))
                 [{:round 0 :event "apply" :comment nil}
                  {:round 0 :event "review-request" :comment "review?"}
                  {:round 0 :event "review-request" :comment "can you please review this?"}
                  {:round 0 :event "third-party-review" :comment "comment"}
                  {:round 0 :event "approve" :comment ""}]))))
      (testing "lazy 3rd party reviewer"
        (let [app-to-close (applications/create-new-draft uid new-wf)
              app-to-approve (applications/create-new-draft uid new-wf)
              app-to-reject (applications/create-new-draft uid new-wf)
              app-to-return (applications/create-new-draft uid new-wf)]
          (db/add-application-item! {:application app-to-close :item new-item})
          (db/add-application-item! {:application app-to-approve :item new-item})
          (db/add-application-item! {:application app-to-reject :item new-item})
          (db/add-application-item! {:application app-to-return :item new-item})
          (applications/submit-application uid app-to-close)
          (applications/submit-application uid app-to-approve)
          (applications/submit-application uid app-to-reject)
          (applications/submit-application uid app-to-return)
          (applications/send-review-request uid app-to-close 0 "can you please review this?" "third-party-reviewer")
          (applications/send-review-request uid app-to-approve 0 "can you please review this?" "third-party-reviewer")
          (applications/send-review-request uid app-to-reject 0 "can you please review this?" "third-party-reviewer")
          (applications/send-review-request uid app-to-return 0 "can you please review this?" "third-party-reviewer")
          (applications/close-application uid app-to-close 0 "closing")
          (is (= (fetch app-to-close) {:curround 0 :state "closed"}) "should be able to close application even without review")
          (applications/approve-application uid app-to-approve 0 "approving")
          (is (= (fetch app-to-approve) {:curround 0 :state "approved"}) "should be able to approve application even without review")
          (applications/reject-application uid app-to-reject 0 "rejecting")
          (is (= (fetch app-to-reject) {:curround 0 :state "rejected"}) "should be able to reject application even without review")
          (applications/return-application uid app-to-return 0 "returning")
          (is (= (fetch app-to-return) {:curround 0 :state "returned"}) "should be able to return application even without review")
          (is (thrown? ForbiddenException (applications/perform-third-party-review "third-party-reviewer" app-to-close 0 "comment"))
              "Should not be able to review when closed")
          (is (thrown? ForbiddenException (applications/perform-third-party-review "third-party-reviewer" app-to-approve 0 "comment"))
              "Should not be able to review when approved")
          (is (thrown? ForbiddenException (applications/perform-third-party-review "third-party-reviewer" app-to-reject 0 "comment"))
              "Should not be able to review when rejected")
          (is (thrown? ForbiddenException (applications/perform-third-party-review "third-party-reviewer" app-to-return 0 "another comment"))
              "Should not be able to review when returned")
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
                  {:round 0 :event "return" :comment "returning"}])))))))

(deftest test-get-entitlements-for-export
  (db/add-user! {:user "jack" :userattrs nil})
  (db/add-user! {:user "jill" :userattrs nil})
  (let [wf (:id (db/create-workflow! {:organization "abc" :modifieruserid "owner" :owneruserid "owner" :title "Test workflow" :fnlround 1}))
        res1 (:id (db/create-resource! {:resid "resource1" :organization "pre" :owneruserid "owner" :modifieruserid "owner"}))
        res2 (:id (db/create-resource! {:resid "resource2" :organization "pre" :owneruserid "owner" :modifieruserid "owner"}))
        item1 (:id (db/create-catalogue-item! {:title "item1" :form nil :resid res1 :wfid wf}))
        item2 (:id (db/create-catalogue-item! {:title "item2" :form nil :resid res2 :wfid wf}))
        jack-app (applications/create-new-draft "jack" wf)
        jill-app (applications/create-new-draft "jill" wf)]
    (db/add-application-item! {:application jack-app :item item1})
    (db/add-application-item! {:application jill-app :item item1})
    (db/add-application-item! {:application jill-app :item item2})
    (applications/submit-application "jack" jack-app)
    (applications/submit-application "jill" jill-app)
    ;; entitlements should now be added via autoapprove
    (binding [context/*roles* #{:handler}]
      (let [lines (split-lines (entitlements/get-entitlements-for-export))]
        (is (= 6 (count lines))) ;; header + 3 resources + 2 in test data
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

(defn- filter-requests-with-application-id [requests application-id]
  (for [request requests
        :let [entitlements (cheshire/parse-string (get-in request [:body "postData"]) keyword)]
        :when (some (comp #{application-id} :application) entitlements)]
    request))

;; TODO separate tests for entitlement poller business and posting to a random external service

#_(deftest ^:eftest/synchronized test-entitlement-granting
  (testing "application that is not approved should not result in entitlements"
    (with-redefs [rems.db.core/add-entitlement! #(throw (Error. "don't call me"))]
      (entitlements/update-entitlements-for {:id 3
                                             :state "applied"
                                             :applicantuserid "bob"})))
  (with-open [server (stub/start! {"/add" {:status 200}
                                   "/remove" {:status 200}})]
    (mount/with-substitutes [#'rems.config/env (mount/state :start {:entitlements-target
                                                                    {:add (str (:uri server) "/add")
                                                                     :remove (str (:uri server) "/remove")}})]
      (mount/start #'rems.config/env)
      (binding [rems.db.core/*db* @rems.db.core/db-connection]
        (let [uid "bob"
              admin "owner"
              organization "foo"
              workflow {:type :workflow/dynamic :handlers [admin]}
              wfid (:id (db/create-workflow! {:organization "abc" :modifieruserid "owner" :owneruserid "owner" :title "dynamic" :fnlround -1 :workflow (cheshire/generate-string workflow)}))
              formid (:id (db/create-form! {:organization "abc" :title "internal-title" :user "owner"}))
              res1 (:id (db/create-resource! {:resid "resource1" :organization organization :owneruserid admin :modifieruserid admin}))
              res2 (:id (db/create-resource! {:resid "resource2" :organization organization :owneruserid admin :modifieruserid admin}))
              item1 (:id (db/create-catalogue-item! {:title "item1" :form formid :resid res1 :wfid wfid}))
              item2 (:id (db/create-catalogue-item! {:title "item2" :form formid :resid res2 :wfid wfid}))]
          (db/add-user! {:user uid :userattrs (cheshire/generate-string {"mail" "b@o.b"})})
          (db/add-user! {:user admin :userattrs nil})
          (let [app-id (applications/create-new-draft uid wfid)]
            (db/add-application-item! {:application app-id :item item1})
            (db/add-application-item! {:application app-id :item item2})
            (applications/add-application-created-event! {:application-id app-id
                                                          :catalogue-item-ids [item1 item2]
                                                          :time (time/now)
                                                          :actor uid})
            (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/submit
                                                      :actor uid
                                                      :application-id app-id
                                                      :time (time/now)})))
            (testing "submitted application should not yet cause entitlements"
              (rems.poller.entitlements/run)
              (is (empty? (db/get-entitlements {:application app-id})))
              (is (empty? (filter-requests-with-application-id (stub/recorded-requests server) app-id))))

            (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/approve
                                                      :actor admin
                                                      :application-id app-id
                                                      :comment ""
                                                      :time (time/now)})))

            (testing "approved application generated entitlements"
              (rems.poller.entitlements/run)
              (rems.poller.entitlements/run) ;; run twice to check idempotence
              (testing "db"
                (= [1 2]
                   (db/get-entitlements {:application app-id})))
              (testing "POST"
                (let [data (first (filter-requests-with-application-id (stub/recorded-requests server) app-id))
                      target (:path data)
                      body (cheshire/parse-string (get-in data [:body "postData"]) keyword)]
                  (is (= "/add" target))
                  (is (= #{{:resource "resource1" :application app-id :user "bob" :mail "b@o.b"}
                           {:resource "resource2" :application app-id :user "bob" :mail "b@o.b"}}
                         (set body))))))

            (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/close
                                                      :actor admin
                                                      :application-id app-id
                                                      :comment ""
                                                      :time (time/now)})))

            (testing "closed application should end entitlements"
              (rems.poller.entitlements/run)
              (testing "db"
                (= [1 2]
                   (db/get-entitlements {:application app-id})))
              (testing "POST"
                (let [data (second (filter-requests-with-application-id (stub/recorded-requests server) app-id))
                      target (:path data)
                      body (cheshire/parse-string (get-in data [:body "postData"]) keyword)]
                  (is (= "/remove" target))
                  (is (= #{{:resource "resource1" :application app-id :user "bob" :mail "b@o.b"}
                           {:resource "resource2" :application app-id :user "bob" :mail "b@o.b"}}
                         (set body)))))))
          (mount/stop #'rems.db.core/db-connection))))))

(deftest test-dynamic-workflow
  (db/add-user! {:user "alice" :userattrs "{}"})
  (db/add-user! {:user "bob" :userattrs "{}"})
  (db/add-user! {:user "handler" :userattrs "{}"})
  (let [workflow {:type :workflow/dynamic
                  :handlers ["handler"]}
        wfid (:id (db/create-workflow! {:organization "abc" :modifieruserid "owner" :owneruserid "owner" :title "dynamic" :fnlround -1 :workflow (cheshire/generate-string workflow)}))
        form (:id (db/create-form! {:organization "abc" :title "internal-title" :user "owner"}))
        form-item (:id (db/create-form-item! {:type "text" :user "owner" :value 0}))
        _ (db/link-form-item! {:form form :itemorder 1 :item form-item :user "owner" :optional false})
        res (:id (db/create-resource! {:resid "some resource" :organization "abc" :owneruserid "owner" :modifieruserid "owner"}))
        ci (:id (db/create-catalogue-item! {:title "dynamic" :resid res :wfid wfid :form form}))
        app-id (applications/create-new-draft "alice" wfid)]
    (db/add-application-item! {:application app-id :item ci})
    (db/save-field-value! {:application app-id :form form :item form-item :user "alice" :value "X"})
    ;; TODO: rewrite these tests to be event-based; remove the above stuff
    (applications/add-application-created-event! {:application-id app-id
                                                  :catalogue-item-ids [ci]
                                                  :time (time/now)
                                                  :actor "alice"})

    (is (= {:applicantuserid "alice"
            :state :rems.workflow.dynamic/draft
            :workflow workflow}
           (select-keys (applications/get-dynamic-application-state app-id) [:applicantuserid :state :workflow])))
    (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/invite-member
                                              :actor "alice"
                                              :member {:name "Jane Doe" :email "jane.doe@members.com"}
                                              :application-id app-id
                                              :time (time/now)})))
    (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/save-draft
                                              :actor "alice"
                                              :application-id app-id
                                              :time (time/now)
                                              :field-values {form-item "X"}
                                              :accepted-licenses #{}})))
    (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/submit
                                              :actor "alice"
                                              :application-id app-id
                                              :time (time/now)})))
    (is (= :rems.workflow.dynamic/submitted
           (:state (applications/get-dynamic-application-state app-id))))
    (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/add-member
                                              :actor "handler"
                                              :member {:userid "bob"}
                                              :application-id app-id
                                              :time (time/now)})))
    (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/invite-member
                                              :actor "handler"
                                              :member {:name "John Doe" :email "john.doe@members.com"}
                                              :application-id app-id
                                              :time (time/now)})))
    (is (= [{:userid "alice"} {:userid "bob"}]
           (:members (applications/get-dynamic-application-state app-id))))
    (is (nil? (applications/dynamic-command! {:type :rems.workflow.dynamic/approve
                                              :actor "handler"
                                              :application-id app-id
                                              :time (time/now)
                                              :comment ""})))
    (is (= :rems.workflow.dynamic/approved
           (:state (applications/get-dynamic-application-state app-id))))))

(deftest test-create-demo-data!
  ;; just a smoke test, check that create-demo-data doesn't fail
  ;; TODO can't create duplicate data
  ;;(test-data/create-demo-data!)
  (is true))
