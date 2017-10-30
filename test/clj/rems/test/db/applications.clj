(ns rems.test.db.applications
  (:require [cheshire.core :as cheshire]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [rems.db.applications :refer :all]))

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
