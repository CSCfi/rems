(ns rems.test.db.entitlements
  (:require [cheshire.core :as cheshire]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.context :as context]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [stub-http.core :as stub]))

(def +entitlements+
  [{:resid "res1" :catappid 11 :userid "user1" :start (time/date-time 2001 10 11) :mail "user1@tes.t"}
   {:resid "res2" :catappid 12 :userid "user2" :start (time/date-time 2002 10 11) :mail "user2@tes.t"}])

(def +expected-payload+
  [{"resource" "res1" "application" 11 "user" "user1" "mail" "user1@tes.t"}
   {"resource" "res2" "application" 12 "user" "user2" "mail" "user2@tes.t"}])

(defn run-with-server
  "Run callback with a mock entitlements http server set up.
   Return sequence of data received by mock server."
  [endpoint-spec callback]
  (with-open [server (stub/start! {"/entitlements" endpoint-spec})]
    (with-redefs [rems.config/env {:entitlements-target
                                   (str (:uri server) "/entitlements")}]
      (callback)
      (for [r (stub/recorded-requests server)]
        (cheshire/parse-string (get-in r [:body "postData"]))))))

(deftest test-post-entitlements
  (let [log (atom [])]
    (with-redefs [db/log-entitlement-post! #(swap! log conj %)]
      (testing "ok"
        (is (= [+expected-payload+]
               (run-with-server {:status 200}
                                #(#'entitlements/post-entitlements +entitlements+))))
        (let [[{payload :payload status :status}] @log]
          (is (= 200 status))
          (is (= +expected-payload+ (cheshire/parse-string payload))))
        (reset! log []))
      (testing "not found"
        (run-with-server {:status 404}
                         #(#'entitlements/post-entitlements +entitlements+))
        (let [[{payload :payload status :status}] @log]
          (is (= 404 status))
          (is (= +expected-payload+ (cheshire/parse-string payload))))
        (reset! log []))
      (testing "timeout"
        (run-with-server {:status 200 :delay 5000} ;; timeout of 2500 in code
                         #(#'entitlements/post-entitlements +entitlements+))
        (let [[{payload :payload status :status}] @log]
          (is (= "exception" status))
          (is (= +expected-payload+ (cheshire/parse-string payload)))))
      (testing "no server"
        (with-redefs [rems.config/env {:entitlements-target "http://invalid/entitlements"}]
          (#'entitlements/post-entitlements +entitlements+)
          (let [[{payload :payload status :status}] @log]
          (is (= "exception" status))
          (is (= +expected-payload+ (cheshire/parse-string payload)))))))))
