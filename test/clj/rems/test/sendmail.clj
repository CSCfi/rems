(ns ^:integration rems.test.sendmail
  "Namespace for test that utilizes an actual database and verifies that e-mails are being to sent to correctly."
  (:require [cheshire.core :refer :all]
            [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conjure.core :as conjure]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.auth.NotAuthorizedException]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.workflow-actors :as actors]
            [rems.email :as email]
            [rems.test.tempura :refer [fake-tempura-fixture]])
  (:import rems.auth.NotAuthorizedException))

(use-fixtures
  :once
  fake-tempura-fixture
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

(defn- subject-to-check [subject]
  (str "([:t.email/" subject "-subject :t/missing])"))

(defn- status-msg-to-check [username appid catid item-title status-key]
  (str "([:t.email/status-changed-msg :t/missing] [\"" username "\" " appid " \"" item-title "\" \":t.applications.states/" status-key "\" \"localhost:3000/form/" catid "/" appid "\"])"))

(deftest test-sending-email
  (with-redefs [catalogue/cached {:localizations (catalogue/load-catalogue-item-localizations!)}]
    (binding [context/*user* {"eppn" "test-user" "mail" "invalid-addr" "commonName" "Test User"}
              context/*root-path* "localhost:3000"]
      (let [uid "approver"
            uid2 "reviewer"
            wfid1 (:id (db/create-workflow! {:owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 1}))
            wfid2 (:id (db/create-workflow! {:owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 0}))
            wfid3 (:id (db/create-workflow! {:owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 1}))
            wfid4 (:id (db/create-workflow! {:owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 1}))
            _ (actors/add-reviewer! wfid1 uid2 0)
            _ (actors/add-approver! wfid1 uid 1)
            _ (actors/add-approver! wfid2 uid 0)
            _ (actors/add-approver! wfid3 uid2 0)
            _ (actors/add-approver! wfid3 uid 0)
            _ (actors/add-approver! wfid3 uid 1)
            _ (actors/add-approver! wfid4 uid 0)
            _ (actors/add-approver! wfid4 uid2 0)
            _ (actors/add-approver! wfid4 uid 1)
            _ (actors/add-approver! wfid4 uid2 1)
            item1 (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wfid1}))
            item2 (:id (db/create-catalogue-item! {:title "item2" :form nil :resid nil :wfid wfid2}))
            item3 (:id (db/create-catalogue-item! {:title "item3" :form nil :resid nil :wfid wfid3}))
            item4 (:id (db/create-catalogue-item! {:title "item4" :form nil :resid nil :wfid wfid4}))
            app1 (applications/create-new-draft item1)
            app2 (applications/create-new-draft item2)
            app3 (applications/create-new-draft item2)
            app4 (applications/create-new-draft item2)
            app5 (applications/create-new-draft item3)
            app6 (applications/create-new-draft item4)]
        (db/add-user! {:user uid :userattrs (generate-string {"eppn" "approver" "mail" "appr-invalid" "commonName" "App Rover"})})
        (db/add-user! {:user uid2 :userattrs (generate-string {"eppn" "reviewer" "mail" "rev-invalid" "commonName" "Rev Iwer"})})
        (db/add-user! {:user "test-user" :userattrs (generate-string context/*user*)})
        (db/add-user! {:user "outside-reviewer" :userattrs (generate-string {"eppn" "outside-reviewer" "mail" "out-invalid" "commonName" "Out Sider"})})
        (testing "Applicant and reviewer should receive an email about the new application"
          (conjure/mocking [email/send-mail]
            (applications/submit-application app1)
            (conjure/verify-call-times-for email/send-mail 2)
            (conjure/verify-first-call-args-for email/send-mail
                                                "invalid-addr"
                                                (subject-to-check "application-sent")
                                                (str "([:t.email/application-sent-msg :t/missing] [\"Test User\" \"item\" \"localhost:3000/form/" item1 "/" app1 "\"])"))
            (conjure/verify-nth-call-args-for 2
                                              email/send-mail
                                              "rev-invalid"
                                              (subject-to-check "review-request")
                                              (str "([:t.email/review-request-msg :t/missing] [\"Rev Iwer\" \"Test User\" " app1 " \"item\" \"localhost:3000/form/" item1 "/" app1 "\"])"))))
        (testing "Approver gets notified after review round"
          (conjure/mocking [email/send-mail]
            (binding [context/*user* {"eppn" "reviewer"}]
              (applications/review-application app1 0 "")
              (conjure/verify-call-times-for email/send-mail 1)
              (conjure/verify-first-call-args-for email/send-mail
                                                  "appr-invalid"
                                                  (subject-to-check "approval-request")
                                                  (str "([:t.email/approval-request-msg :t/missing] [\"App Rover\" \"Test User\" " app1 " \"item\" \"localhost:3000/form/" item1 "/" app1 "\"])")))))
        (testing "Applicant gets notified when application is approved"
          (conjure/mocking [email/send-mail]
            (binding [context/*user* {"eppn" "approver"}]
              (applications/approve-application app1 1 "")
              (conjure/verify-call-times-for email/send-mail 1)
              (conjure/verify-first-call-args-for email/send-mail "invalid-addr" (subject-to-check "status-changed") (status-msg-to-check "Test User" app1 item1 "item" "approved")))))
        (conjure/mocking [email/send-mail]
          (applications/submit-application app2)
          (applications/submit-application app3))
        (binding [context/*user* {"eppn" "approver"}]
          (testing "Applicant gets notified when application is rejected"
            (conjure/mocking [email/send-mail]
              (applications/reject-application app2 0 "")
              (conjure/verify-call-times-for email/send-mail 1)
              (conjure/verify-first-call-args-for email/send-mail "invalid-addr" (subject-to-check "status-changed") (status-msg-to-check "Test User" app2 item2 "item2" "rejected"))))
          (testing "Applicant gets notified when application is returned to him/her"
            (conjure/mocking [email/send-mail]
              (applications/return-application app3 0 "")
              (conjure/verify-call-times-for email/send-mail 1)
              (conjure/verify-first-call-args-for email/send-mail "invalid-addr" (subject-to-check "status-changed") (status-msg-to-check "Test User" app3 item2 "item2" "returned"))))
          (testing "Emails should not be sent when actions fail"
            (conjure/mocking [email/send-mail]
              (is (thrown? NotAuthorizedException (applications/approve-application app4 1 ""))
                  "Approval should fail")
              (is (thrown? NotAuthorizedException (applications/reject-application app4 1 ""))
                  "Rejection should fail")
              (is (thrown? NotAuthorizedException (applications/review-application app4 1 ""))
                  "Review should fail")
              (is (thrown? NotAuthorizedException (applications/return-application app4 1 ""))
                  "Return should fail")
              (is (thrown? NotAuthorizedException (applications/close-application app4 2 ""))
                  "closing should fail")
              (is (thrown? NotAuthorizedException (applications/withdraw-application app1 2 ""))
                  "withdraw should fail")
              (conjure/verify-call-times-for email/send-mail 0))))
        (testing "Applicant is notified of closed application"
          (conjure/mocking [email/send-mail]
            (applications/close-application app1 1 "")
            (conjure/verify-call-times-for email/send-mail 1)
            (conjure/verify-first-call-args-for email/send-mail "invalid-addr" (subject-to-check "status-changed") (status-msg-to-check "Test User" app1 item1 "item" "closed"))))
        (testing "Applicant is notified of withdrawn application"
          (conjure/mocking [email/send-mail]
            (applications/submit-application app4)
            (applications/withdraw-application app4 0 "")
            (conjure/verify-call-times-for email/send-mail 3)
            (conjure/verify-nth-call-args-for 3 email/send-mail "invalid-addr" (subject-to-check "status-changed") (status-msg-to-check "Test User" app4 item2 "item2" "withdrawn"))))
        (testing "3rd party reviewer is notified after a review request"
          (conjure/mocking [email/send-mail]
            (applications/submit-application app4)
            (binding [context/*user* {"eppn" "approver"}]
              (applications/send-review-request app4 0 "" uid2)
              (applications/send-review-request app4 0 "" uid2))
            (conjure/verify-call-times-for email/send-mail 3)
            (conjure/verify-nth-call-args-for 3
                                              email/send-mail
                                              "rev-invalid"
                                              (subject-to-check "review-request")
                                              (str "([:t.email/review-request-msg :t/missing] [\"Rev Iwer\" \"Test User\" " app4 " \"item2\" \"localhost:3000/form/" item2 "/" app4 "\"])"))
            (binding [context/*user* {"eppn" "reviewer"}]
              (applications/perform-3rd-party-review app4 0 ""))
            (conjure/verify-call-times-for email/send-mail 3)))
        (testing "Actors are notified when their attention is no longer required"
          (conjure/mocking [email/send-mail]
            (applications/submit-application app5)
            (binding [context/*user* {"eppn" "approver"}]
              (applications/approve-application app5 0 ""))
            (conjure/verify-call-times-for email/send-mail 5)
            (conjure/verify-nth-call-args-for 4
                                              email/send-mail
                                              "rev-invalid"
                                              (subject-to-check "action-not-needed")
                                              (str "([:t.email/action-not-needed-msg :t/missing] [\"Rev Iwer\" \"Test User\" " app5 "])"))
            (binding [context/*user* {"eppn" "approver"}]
              (applications/send-review-request app5 1 "" uid2)
              (applications/approve-application app5 1 ""))
            (conjure/verify-call-times-for email/send-mail 8)
            (conjure/verify-nth-call-args-for 7
                                              email/send-mail
                                              "rev-invalid"
                                              (subject-to-check "action-not-needed")
                                              (str "([:t.email/action-not-needed-msg :t/missing] [\"Rev Iwer\" \"Test User\" " app5 "])"))))
        (testing "Multiple rounds with lazy actors"
          (conjure/mocking [email/send-mail]
            (applications/submit-application app6)
            (binding [context/*user* {"eppn" "approver"}]
              (applications/send-review-request app6 0 "" "outside-reviewer")
              (applications/approve-application app6 0 "")
              (applications/send-review-request app6 1 "" "outside-reviewer"))
            (binding [context/*user* {"eppn" "outside-reviewer"}]
              (applications/perform-3rd-party-review app6 1 ""))
            (binding [context/*user* {"eppn" "approver"}]
              (applications/approve-application app6 1 ""))
              (conjure/verify-call-times-for email/send-mail 11)))))))
