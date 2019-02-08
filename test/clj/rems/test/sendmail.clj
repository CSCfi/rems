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
            [rems.auth.ForbiddenException]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.workflow-actors :as actors]
            [rems.email :as email]
            [rems.test.tempura :refer [fake-tempura-fixture]])
  (:import (rems.auth ForbiddenException NotAuthorizedException)))

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

(use-fixtures :each
  (fn [f]
    (conman/with-transaction [rems.db.core/*db* {:isolation :serializable}]
      (jdbc/db-set-rollback-only! rems.db.core/*db*)
      (f))))

(defn- subject-to-check [subject]
  (str "([:t.email/" subject "-subject :t/missing])"))

(defn- status-msg-to-check [username appid item-title status-key]
  (str "([:t.email/status-changed-msg :t/missing] [\"" username "\" " appid " \"" item-title "\" \"([:t.applications.states/" status-key " :t/missing])\" \"localhost:3000/form/" appid "\"])"))

(deftest test-sending-email
  (let [common-name "Test User"
        applicant "test-user"
        applicant-attrs {"eppn" applicant "mail" "invalid-addr" "commonName" common-name}]
    (with-redefs [catalogue/cached {:localizations (catalogue/load-catalogue-item-localizations!)}]
      (binding [context/*root-path* "localhost:3000"]
        (let [approver "approver"
              reviewer "reviewer"
              wfid1 (:id (db/create-workflow! {:organization "abc" :owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 1}))
              wfid2 (:id (db/create-workflow! {:organization "abc" :owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 0}))
              wfid3 (:id (db/create-workflow! {:organization "abc" :owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 1}))
              wfid4 (:id (db/create-workflow! {:organization "abc" :owneruserid "workflow-owner" :modifieruserid "workflow-owner" :title "" :fnlround 1}))
              _ (actors/add-reviewer! wfid1 reviewer 0)
              _ (actors/add-approver! wfid1 approver 1)
              _ (actors/add-approver! wfid2 approver 0)
              _ (actors/add-approver! wfid3 reviewer 0)
              _ (actors/add-approver! wfid3 approver 0)
              _ (actors/add-approver! wfid3 approver 1)
              _ (actors/add-approver! wfid4 approver 0)
              _ (actors/add-approver! wfid4 reviewer 0)
              _ (actors/add-approver! wfid4 approver 1)
              _ (actors/add-approver! wfid4 reviewer 1)
              item1 (:id (db/create-catalogue-item! {:title "item" :form nil :resid nil :wfid wfid1 :state "enabled"}))
              item2 (:id (db/create-catalogue-item! {:title "item2" :form nil :resid nil :wfid wfid2 :state "enabled"}))
              item3 (:id (db/create-catalogue-item! {:title "item3" :form nil :resid nil :wfid wfid3 :state "enabled"}))
              item4 (:id (db/create-catalogue-item! {:title "item4" :form nil :resid nil :wfid wfid4 :state "enabled"}))
              app1 (applications/create-new-draft applicant wfid1)
              app2 (applications/create-new-draft applicant wfid2)
              app3 (applications/create-new-draft applicant wfid2)
              app4 (applications/create-new-draft applicant wfid2)
              app5 (applications/create-new-draft applicant wfid3)
              app6 (applications/create-new-draft applicant wfid4)
              _ (db/add-application-item! {:application app1 :item item1})
              _ (db/add-application-item! {:application app2 :item item2})
              _ (db/add-application-item! {:application app3 :item item2})
              _ (db/add-application-item! {:application app4 :item item2})
              _ (db/add-application-item! {:application app5 :item item3})
              _ (db/add-application-item! {:application app6 :item item4})
              items1 (rems.db.applications/get-catalogue-items-by-application-id app1)
              items2 (rems.db.applications/get-catalogue-items-by-application-id app2)
              items3 (rems.db.applications/get-catalogue-items-by-application-id app3)
              items4 (rems.db.applications/get-catalogue-items-by-application-id app4)
              approver-attrs {"eppn" approver "mail" "appr-invalid" "commonName" "App Rover"}
              _ (db/add-user! {:user approver :userattrs (generate-string approver-attrs)})
              reviewer-attrs {"eppn" "reviewer" "mail" "rev-invalid" "commonName" "Rev Iwer"}
              _ (db/add-user! {:user reviewer :userattrs (generate-string reviewer-attrs)})
              _ (db/add-user! {:user applicant :userattrs (generate-string applicant-attrs)})
              outside-attrs {"eppn" "outside-reviewer" "mail" "out-invalid" "commonName" "Out Sider"}
              _ (db/add-user! {:user "outside-reviewer" :userattrs (generate-string outside-attrs)})]
          (testing "Applicant and reviewer should receive an email about the new application"
            (conjure/mocking [email/confirm-application-creation
                              email/review-request]
                             (applications/submit-application applicant app1)
                             (conjure/verify-called-once-with-args email/confirm-application-creation
                                                                   app1
                                                                   items1)
                             (conjure/verify-called-once-with-args email/review-request
                                                                   reviewer-attrs
                                                                   common-name
                                                                   app1
                                                                   items1)))
          (testing "Approver gets notified after review round"
            (conjure/mocking [email/approval-request]
                             (applications/review-application reviewer app1 0 "")
                             (conjure/verify-called-once-with-args email/approval-request
                                                                   approver-attrs
                                                                   common-name
                                                                   app1
                                                                   items1)))
          (testing "Applicant gets notified when application is approved"
            (conjure/mocking [email/status-change-alert]
                             (applications/approve-application approver app1 1 "")
                             (conjure/verify-called-once-with-args email/status-change-alert
                                                                   applicant-attrs
                                                                   app1
                                                                   items1
                                                                   "approved")))
          (conjure/mocking [email/send-mail]
                           (applications/submit-application applicant app2)
                           (applications/submit-application applicant app3))
          (testing "Applicant gets notified when application is rejected"
            (conjure/mocking [email/status-change-alert]
                             (applications/reject-application approver app2 0 "")
                             (conjure/verify-called-once-with-args email/status-change-alert
                                                                   applicant-attrs
                                                                   app2
                                                                   items2
                                                                   "rejected")))
          (testing "Applicant gets notified when application is returned to him/her"
            (conjure/mocking [email/status-change-alert]
                             (applications/return-application approver app3 0 "")
                             (conjure/verify-called-once-with-args email/status-change-alert
                                                                   applicant-attrs
                                                                   app3
                                                                   items3
                                                                   "returned")))
          (testing "Emails should not be sent when actions fail"
            (conjure/mocking [email/send-mail]
                             (is (thrown? ForbiddenException (applications/approve-application applicant app4 1 ""))
                                 "Approval should fail")
                             (is (thrown? ForbiddenException (applications/reject-application applicant app4 1 ""))
                                 "Rejection should fail")
                             (is (thrown? ForbiddenException (applications/review-application applicant app4 1 ""))
                                 "Review should fail")
                             (is (thrown? ForbiddenException (applications/return-application applicant app4 1 ""))
                                 "Return should fail")
                             (is (thrown? ForbiddenException (applications/close-application applicant app4 2 ""))
                                 "closing should fail")
                             (is (thrown? ForbiddenException (applications/withdraw-application applicant app1 2 ""))
                                 "withdraw should fail")
                             (conjure/verify-call-times-for email/send-mail 0)))
          (testing "Applicant is notified of closed application"
            (conjure/mocking [email/status-change-alert]
                             (applications/close-application approver app1 1 "")
                             (conjure/verify-called-once-with-args email/status-change-alert
                                                                   applicant-attrs
                                                                   app1
                                                                   items1
                                                                   "closed")))
          (testing "Applicant is notified of withdrawn application"
            (conjure/mocking [email/status-change-alert]
                             (applications/submit-application applicant app4)
                             (applications/withdraw-application applicant app4 0 "")
                             (conjure/verify-called-once-with-args email/status-change-alert
                                                                   applicant-attrs
                                                                   app4
                                                                   items4
                                                                   "withdrawn")))
          (testing "3rd party reviewer is notified after a review request"
            (conjure/mocking [email/review-request
                              email/status-change-alert]
                             (applications/submit-application applicant app4)
                             (applications/send-review-request approver app4 0 "" reviewer)
                             (applications/send-review-request approver app4 0 "" reviewer)
                             (conjure/verify-called-once-with-args email/review-request
                                                                   reviewer-attrs
                                                                   common-name
                                                                   app4
                                                                   items4)
                             (conjure/verify-call-times-for email/review-request 1)
                             (applications/perform-third-party-review reviewer app4 0 "")))
          (testing "Actors are notified when their attention is no longer required"
            (conjure/mocking [email/action-not-needed]
                             (applications/submit-application applicant app5)
                             (applications/approve-application approver app5 0 "")
                             ;; NB: reviewer is here also approver
                             (conjure/verify-called-once-with-args email/action-not-needed
                                                                   reviewer-attrs
                                                                   common-name
                                                                   app5))
            (conjure/mocking [email/action-not-needed]
                             (applications/send-review-request approver app5 1 "" reviewer)
                             (applications/approve-application approver app5 1 "")
                             (conjure/verify-called-once-with-args email/action-not-needed
                                                                   reviewer-attrs
                                                                   common-name
                                                                   app5)))
          (testing "Multiple rounds with lazy actors"
            (conjure/mocking [email/action-not-needed]
                             (applications/submit-application applicant app6)
                             (applications/send-review-request approver app6 0 "" "outside-reviewer")
                             (applications/approve-application approver app6 0 "")
                             (conjure/verify-call-times-for email/action-not-needed 2)
                             (conjure/verify-nth-call-args-for 1
                                                               email/action-not-needed
                                                               reviewer-attrs
                                                               common-name
                                                               app6)
                             (conjure/verify-nth-call-args-for 2
                                                               email/action-not-needed
                                                               outside-attrs
                                                               common-name
                                                               app6))
            (conjure/mocking [email/action-not-needed]
                             (applications/send-review-request approver app6 1 "" "outside-reviewer")
                             (applications/perform-third-party-review "outside-reviewer" app6 1 "")
                             (applications/approve-application approver app6 1 "")
                             (conjure/verify-called-once-with-args email/action-not-needed
                                                                   reviewer-attrs
                                                                   common-name
                                                                   app6))))))))
