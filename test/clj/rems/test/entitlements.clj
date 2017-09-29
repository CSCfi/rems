(ns rems.test.entitlements
  (:require [clj-time.core :as time]
            [clojure.string :refer [split-lines]]
            [clojure.test :refer :all]
            [rems.auth.NotAuthorizedException]
            [rems.context :as context]
            [rems.db.core :as db]
            [rems.entitlements :as entitlements])
  (:import rems.auth.NotAuthorizedException))

(deftest test-get-entitlements-for-export
  (with-redefs [db/get-entitlements-for-export
                (fn []
                  [{:resid "res1" :catappid 11 :userid "user1" :start (time/date-time 2001 10 11)}
                   {:resid "res2" :catappid 12 :userid "user2" :start (time/date-time 2002 10 11)}])]
    (binding [context/*roles* #{:approver}]
      (let [lines (split-lines (#'entitlements/get-entitlements-for-export))]
        (is (= 3 (count lines)))
        (is (.contains (nth lines 1) "res1"))
        (is (.contains (nth lines 1) "2001"))
        (is (.contains (nth lines 1) "11"))
        (is (.contains (nth lines 2) "res2"))
        (is (.contains (nth lines 2) "2002"))
        (is (.contains (nth lines 2) "12"))))
    (binding [context/*roles* #{:applicant :reviewer}]
      (is (thrown? NotAuthorizedException
                   (#'entitlements/get-entitlements-for-export))))))
