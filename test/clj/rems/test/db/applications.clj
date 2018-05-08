(ns rems.test.db.applications
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.applications :refer :all]
            [rems.db.core :as db]))

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

(deftest test-get-active-licenses
  (let [get-active-licenses #'rems.db.applications/get-active-licenses
        today (time/now)
        yesterday (time/minus today (time/days 1))
        expired-license-end (time/plus yesterday (time/hours 1))
        just-created-license-start (time/minus today (time/hours 1))]
    (with-redefs [db/get-licenses (fn [params] [{:id :always :start nil :endt nil}
                                                {:id :expired :start nil :endt expired-license-end}
                                                {:id :just-created :start just-created-license-start :endt nil}])]
      (is (:always (set (map :id (get-active-licenses today nil)))) "always license should be visible today")
      (is (:always (set (map :id (get-active-licenses yesterday nil)))) "always license should be visible yesterday")
      (is (not (:expired (set (map :id (get-active-licenses today nil))))) "expired license should not be visible today")
      (is (:expired (set (map :id (get-active-licenses yesterday nil)))) "expired license should be visible yesterday")
      (is (:just-created (set (map :id (get-active-licenses today nil)))) "just created license should be visible today")
      (is (not (:just-created (set (map :id (get-active-licenses yesterday nil))))) "just created license should not be visible yesterday"))))
