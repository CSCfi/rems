(ns rems.test.db.entitlements
  (:require [cheshire.core :as cheshire]
            [clj-time.core :as time]
            [clojure.string :refer [split-lines]]
            [clojure.test :refer :all]
            [rems.auth.NotAuthorizedException]
            [rems.context :as context]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [stub-http.core :as stub])
  (:import rems.auth.NotAuthorizedException))

(deftest test-get-entitlements-for-export
  (with-redefs [db/get-entitlements-for-export
                (fn []
                  [{:resid "res1" :catappid 11 :userid "user1" :start (time/date-time 2001 10 11)}
                   {:resid "res2" :catappid 12 :userid "user2" :start (time/date-time 2002 10 11)}])]
    (binding [context/*roles* #{:approver}]
      (let [lines (split-lines (entitlements/get-entitlements-for-export))]
        (is (= 3 (count lines)))
        (is (.contains (nth lines 1) "res1"))
        (is (.contains (nth lines 1) "2001"))
        (is (.contains (nth lines 1) "11"))
        (is (.contains (nth lines 2) "res2"))
        (is (.contains (nth lines 2) "2002"))
        (is (.contains (nth lines 2) "12"))))
    (binding [context/*roles* #{:applicant :reviewer}]
      (is (thrown? NotAuthorizedException
                   (entitlements/get-entitlements-for-export))))))

(deftest test-add-entitlements-for
  (with-redefs [rems.db.core/add-entitlement! #(throw (Error. "don't call me"))]
    (entitlements/add-entitlements-for {:id 3
                                        :state "applied"
                                        :applicantuserid "bob"}))
  (let [db (atom [])]
    (with-open [server (stub/start! {"/entitlements" {:status 200}})]
      (with-redefs [rems.db.core/add-entitlement! #(swap! db conj %)
                    rems.config/env {:entitlements-target
                                     (str (:uri server) "/entitlements")}]
        (entitlements/add-entitlements-for {:id 3
                                                      :state "approved"
                                                      :applicantuserid "bob"})
        (is (= [{:application 3 :user "bob"}] @db))
        (let [data (-> (stub/recorded-requests server)
                       first
                       :body
                       (get "postData")
                       cheshire/parse-string)]
          (is (= {"application" 3 "user" "bob"} data)))))))
