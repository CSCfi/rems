(ns ^:integration rems.test.sendmail
  "Namespace for test that utilizes an actual database and verifies that e-mails are being to sent to correctly."
  (:require [cheshire.core :refer :all]
            [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [conjure.core :as conjure]
            [conman.core :as conman]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.workflow-actors :as actors]
            [rems.email :as email]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [rems.util :refer [get-user-id]]))

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

(deftest test-sending-email
    (conjure/mocking [email/send-mail]
      (with-redefs [catalogue/cached {:localizations (catalogue/load-catalogue-item-localizations!)}]
        (binding [context/*user* {"eppn" "test-user" "mail" "invalid-addr" "commonName" "Test User"}
                  context/*root-path* "localhost:3000"]
          (let [uid "approver"
                uid2 "reviewer"
                wfid1 (:id (db/create-workflow! {:owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 1}))
                wfid2 (:id (db/create-workflow! {:owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 0}))
                _ (actors/add-reviewer! wfid1 uid2 0)
                _ (actors/add-approver! wfid1 uid 1)
                _ (actors/add-approver! wfid2 uid 0)
                item1 (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wfid1}))
                item2 (:id (db/create-catalogue-item! {:title "item2" :form nil :resid nil :wfid wfid2}))
                app1 (applications/create-new-draft item1)
                app2 (applications/create-new-draft item2)
                app3 (applications/create-new-draft item2)
                app4 (applications/create-new-draft item2)]
            (db/add-user! {:user uid :userattrs (generate-string {"mail" "appr-invalid" "commonName" "App Rover"})})
            (db/add-user! {:user uid2 :userattrs (generate-string {"mail" "rev-invalid" "commonName" "Rev Iwer"})})
            (db/add-user! {:user "test-user" :userattrs (generate-string context/*user*)})
            (applications/submit-application app1)
            (testing "Applicant and reviewer should receive an email about the new application"
              (conjure/verify-call-times-for email/send-mail 2)
              (conjure/verify-first-call-args-for email/send-mail
                                                  "invalid-addr"
                                                  "([:t.email/application-sent-subject :t/missing])"
                                                  (str "([:t.email/application-sent-msg :t/missing] [\"Test User\" \"item\" \"localhost:3000/form/" item1 "/" app1 "\"])"))
              (conjure/verify-nth-call-args-for 2
                                                email/send-mail
                                                "rev-invalid"
                                                "([:t.email/review-request-subject :t/missing])"
                                                (str "([:t.email/review-request-msg :t/missing] [\"Rev Iwer\" \"Test User\" " app1 " \"item\" \"localhost:3000/form/" item1 "/" app1 "\"])")))
            (binding [context/*user* {"eppn" "reviewer"}]
              (applications/review-application app1 0 "")
              (testing "Approver gets notified after review round"
                (conjure/verify-call-times-for email/send-mail 3)
                (conjure/verify-nth-call-args-for 3 email/send-mail
                                                  "appr-invalid"
                                                  "([:t.email/approval-request-subject :t/missing])"
                                                  (str "([:t.email/approval-request-msg :t/missing] [\"App Rover\" \"Test User\" " app1 " \"item\" \"localhost:3000/form/" item1 "/" app1 "\"])"))))
            (binding [context/*user* {"eppn" "approver"}]
              (applications/approve-application app1 1 "")
              (testing "Applicant gets notified when application is approved"
                (conjure/verify-call-times-for email/send-mail 4)
                (conjure/verify-nth-call-args-for 4 email/send-mail
                                                  "invalid-addr"
                                                  "([:t.email/status-changed-subject :t/missing])"
                                                  (str "([:t.email/status-changed-msg :t/missing] [\"Test User\" " app1 " \"item\" \":t.applications.states/approved\" \"localhost:3000/form/" item1 "/" app1 "\"])"))))
            (applications/submit-application app2)
            (applications/submit-application app3)
            (binding [context/*user* {"eppn" "approver"}]
              (testing "Applicant gets notified when application is rejected"
                (applications/reject-application app2 0 "")
                ;; Four other mails should have been sent before the previous reject action.
                (conjure/verify-call-times-for email/send-mail 9)
                (conjure/verify-nth-call-args-for 9 email/send-mail
                                                  "invalid-addr"
                                                  "([:t.email/status-changed-subject :t/missing])"
                                                  (str "([:t.email/status-changed-msg :t/missing] [\"Test User\" " app2 " \"item2\" \":t.applications.states/rejected\" \"localhost:3000/form/" item2 "/" app2 "\"])")))
              (testing "Applicant gets notified when application is returned to him/her"
                (applications/return-application app3 0 "")
                (conjure/verify-call-times-for email/send-mail 10)
                (conjure/verify-nth-call-args-for 10 email/send-mail
                                                  "invalid-addr"
                                                  "([:t.email/status-changed-subject :t/missing])"
                                                  (str "([:t.email/status-changed-msg :t/missing] [\"Test User\" " app3 " \"item2\" \":t.applications.states/returned\" \"localhost:3000/form/" item2 "/" app3 "\"])")))
              (testing "Emails should not be sent when actions fail"
                (is (thrown? Exception (applications/approve-application app4 1 ""))
                    "Approval should fail")
                (is (thrown? Exception (applications/reject-application app4 1 ""))
                    "Rejection should fail")
                (is (thrown? Exception (applications/review-application app4 1 ""))
                    "Review should fail")
                (is (thrown? Exception (applications/return-application app4 1 ""))
                    "Return should fail")
                (conjure/verify-call-times-for email/send-mail 10)))
            (testing "Applicant is notified of closed application"
              (applications/close-application app1 1 "")
              (conjure/verify-call-times-for email/send-mail 11)
              (conjure/verify-nth-call-args-for 11 email/send-mail
                                                  "invalid-addr"
                                                  "([:t.email/status-changed-subject :t/missing])"
                                                  (str "([:t.email/status-changed-msg :t/missing] [\"Test User\" " app1 " \"item\" \":t.applications.states/closed\" \"localhost:3000/form/" item1 "/" app1 "\"])")))
            (testing "Applicant is notified of withdrawn application"
              (applications/submit-application app4)
              (applications/withdraw-application app4 0 "")
              (conjure/verify-call-times-for email/send-mail 14)
              (conjure/verify-nth-call-args-for 14 email/send-mail
                                                  "invalid-addr"
                                                  "([:t.email/status-changed-subject :t/missing])"
                                                  (str "([:t.email/status-changed-msg :t/missing] [\"Test User\" " app4 " \"item2\" \":t.applications.states/withdrawn\" \"localhost:3000/form/" item2 "/" app4 "\"])"))))))))
