(ns ^:integration rems.test.db.applications
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as generators]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.applications :refer :all]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.workflow.dynamic :as dynamic]
            [schema-generators.generators :as sg])
  (:import (org.joda.time DateTime DateTimeZone)))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'rems.config/env
     #'rems.db.core/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (test-data/create-test-data!)
    (f)
    (mount/stop)))

(deftest can-act-as?-test
  (is (can-act-as? "developer" (get-application-state 12) "approver"))
  (is (not (can-act-as? "developer" (get-application-state 12) "reviewer")))
  (is (not (can-act-as? "alice" (get-application-state 12) "approver"))))

(deftest test-handling-event?
  (are [en] (handling-event? nil {:event en})
    "approve"
    "autoapprove"
    "reject"
    "return"
    "review")
  (is (not (handling-event? nil {:event "apply"})))
  (is (not (handling-event? nil {:event "withdraw"})))
  (is (handling-event? {:applicantuserid 123} {:event "close" :userid 456}))
  (is (not (handling-event? {:applicantuserid 123} {:event "close" :userid 123}))
      "applicant's own close is not handler's action"))

(deftest test-handled?
  (is (not (handled? nil)))
  (is (handled? {:state "approved"}))
  (is (handled? {:state "rejected"}))
  (is (handled? {:state "returned"}))
  (is (not (handled? {:state "closed"})))
  (is (not (handled? {:state "withdrawn"})))
  (is (handled? {:state "approved" :events [{:event "approve"}]}))
  (is (handled? {:state "rejected" :events [{:event "reject"}]}))
  (is (handled? {:state "returned" :events [{:event "apply"} {:event "return"}]}))
  (is (not (handled? {:state "closed"
                      :events [{:event "apply"}
                               {:event "close"}]}))
      "applicant's own close is not handled by others")
  (is (not (handled? {:state "withdrawn"
                      :events [{:event "apply"}
                               {:event "withdraw"}]}))
      "applicant's own withdraw is not handled by others")
  (is (handled? {:state "closed" :applicantuserid 123
                 :events [{:event "apply" :userid 123}
                          {:event "return" :userid 456}
                          {:event "close" :userid 123}]})
      "previously handled (returned) is still handled if closed by the applicant")
  (is (not (handled? {:state "closed" :applicantuserid 123
                      :events [{:event "apply" :userid 123}
                               {:event "withdraw" :userid 123}
                               {:event "close" :userid 123}]}))
      "actions only by applicant"))

(deftest test-event-serialization
  (let [generators {DateTime (generators/fmap #(DateTime. ^long % DateTimeZone/UTC)
                                              (generators/large-integer* {:min 0}))}]
    (doseq [event (sg/sample 100 dynamic/Event generators)]
      (is (= event (-> event event-to-json json-to-event))))))
