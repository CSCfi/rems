(ns ^:integration rems.db.test-entitlements
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [caches-fixture test-db-fixture rollback-db-fixture test-data-fixture]]
            [rems.json :as json]
            [rems.testing-util :refer [suppress-logging]]
            [stub-http.core :as stub]))

(use-fixtures
  :once
  (suppress-logging "rems.db.entitlements")
  test-db-fixture
  rollback-db-fixture
  test-data-fixture
  caches-fixture)

(def +entitlements+
  [{:resid "res1" :catappid 11 :userid "user1" :start (time/date-time 2001 10 11) :mail "user1@tes.t"}
   {:resid "res2" :catappid 12 :userid "user2" :start (time/date-time 2002 10 11) :mail "user2@tes.t"}])

(def +expected-payload+
  [{:resource "res1" :application 11 :user "user1" :mail "user1@tes.t"}
   {:resource "res2" :application 12 :user "user2" :mail "user2@tes.t"}])

(defn run-with-server
  [endpoint-spec callback]
  (with-open [server (stub/start! {"/add" endpoint-spec
                                   "/remove" endpoint-spec
                                   "/ga4gh" endpoint-spec})]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :entitlements-target {:add (str (:uri server) "/add")
                                                               :remove (str (:uri server) "/remove")
                                                               :ga4gh (str (:uri server) "/ga4gh")})]
      (callback server))))

(deftest test-post-entitlements!
  (testing "ok :add action"
    (run-with-server
     {:status 200}
     (fn [server]
       (is (nil? (#'entitlements/post-entitlements! {:action :add :entitlements +entitlements+})))
       (is (= [+expected-payload+] (for [r (stub/recorded-requests server)]
                                     (json/parse-string (get-in r [:body "postData"]))))))))

  (testing "ok :ga4gh action"
    (run-with-server
      {:status 200}
      (fn [server]
        (is (nil? (#'entitlements/post-entitlements! {:action :ga4gh :entitlements +entitlements+})))
        (is (= {:ga4gh_visa_v1 ["eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoiQ29udHJvbGxlZEFjY2Vzc0dyYW50cyIsInZhbHVlIjoicmVzMSIsInNvdXJjZSI6Imh0dHBzOi8vZ2E0Z2gub3JnL2R1cmkvbm9fb3JnIiwiYnkiOiIiLCJhc3NlcnRlZCI6MTAwMjc1ODQwMDAwMH0.shOczQ78bE00HsvhPHqY1b5PcfIW2ebWd_4p6xb3TUg"
                                "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoiQ29udHJvbGxlZEFjY2Vzc0dyYW50cyIsInZhbHVlIjoicmVzMiIsInNvdXJjZSI6Imh0dHBzOi8vZ2E0Z2gub3JnL2R1cmkvbm9fb3JnIiwiYnkiOiIiLCJhc3NlcnRlZCI6MTAzNDI5NDQwMDAwMH0.rfQTVmvlmkx2VsA5j5hGyaf0CUPh9I74WDlUbf_YHXk"]}
               (-> (stub/recorded-requests server)
                   first
                   (get-in [:body "postData"])
                   json/parse-string))))))

  (testing "not-found"
    (run-with-server
     {:status 404}
     (fn [_]
       (is (= "failed: 404" (#'entitlements/post-entitlements! {:action :add :entitlements +entitlements+}))))))
  (testing "timeout"
    (run-with-server
     {:status 200 :delay 5000} ;; timeout of 2500 in code
     (fn [_]
       (is (= "failed: exception" (#'entitlements/post-entitlements! {:action :add :entitlements +entitlements+}))))))
  (testing "invalid url"
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :entitlements-target {:add "http://invalid/entitlements"})]
      (is (= "failed: exception" (#'entitlements/post-entitlements! {:action :add :entitlements +entitlements+})))))
  (testing "no server configured"
    (is (nil? (#'entitlements/post-entitlements! {:action :add :entitlements +entitlements+})))))

(defn- get-requests [server]
  (doall
   (for [req (stub/recorded-requests server)]
     {:path (:path req)
      :body (json/parse-string (get-in req [:body "postData"]))})))

(defn- requests-for-paths [server ^String path]
  (filter #(= (% :path) path) (set (get-requests server))))

(defn- is-valid-ga4gh? [entry]
  (string? (first (:ga4gh_visa_v1 (:body entry)))))

(deftest test-entitlement-granting
  (let [applicant "bob"
        member "elsa"
        admin "owner"
        wfid (test-data/create-workflow! {:handlers [admin]})
        form-id (test-data/create-form! {})
        lic-id1 (test-data/create-license! {})
        lic-id2 (test-data/create-license! {})
        item1 (test-data/create-catalogue-item!
               {:resource-id (test-data/create-resource! {:resource-ext-id "resource1"
                                                          :license-ids [lic-id1]})
                :form-id form-id
                :workflow-id wfid})
        item2 (test-data/create-catalogue-item!
               {:resource-id (test-data/create-resource! {:resource-ext-id "resource2"
                                                          :license-ids [lic-id2]})
                :form-id form-id
                :workflow-id wfid})
        item3 (test-data/create-catalogue-item!
               {:resource-id (test-data/create-resource! {:resource-ext-id "resource3"
                                                          :license-ids [lic-id1]})
                :form-id form-id
                :workflow-id wfid})]
    (test-data/create-user! {:eppn applicant :mail "b@o.b" :commonName "Bob"})
    (test-data/create-user! {:eppn member :mail "e.l@s.a" :commonName "Elsa"})
    (test-data/create-user! {:eppn admin :mail "o.w@n.er" :commonName "Owner"})

    (entitlements/process-outbox!) ;; empty outbox from pending posts

    (let [app-id (test-data/create-application! {:actor applicant :catalogue-item-ids [item1 item2]})]
      (testing "submitted application should not yet cause entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-data/command! {:type :application.command/accept-licenses
                                :application-id app-id
                                :accepted-licenses [lic-id1 lic-id2]
                                :actor applicant})
           (test-data/command! {:type :application.command/submit
                                :application-id app-id
                                :actor applicant})
           (test-data/command! {:type :application.command/add-member
                                :application-id app-id
                                :actor admin
                                :member {:userid member}})

           (entitlements/process-outbox!)

           (is (empty? (db/get-entitlements {:application app-id})))
           (is (empty? (stub/recorded-requests server)))))

        (testing "approved application, licenses accepted by one user generates entitlements for that user"
          (run-with-server
           {:status 200}
           (fn [server]
             (test-data/command! {:type :application.command/approve
                                  :application-id app-id
                                  :actor admin
                                  :comment ""})
             (test-data/command! {:type :application.command/accept-licenses
                                  :application-id app-id
                                  :actor member
                                  :accepted-licenses [lic-id1]}) ; only accept some licenses
             (is (= {applicant #{lic-id1 lic-id2}
                     member #{lic-id1}}
                    (:application/accepted-licenses (applications/get-application app-id))))

             (entitlements/process-outbox!)

             (testing "entitlements exist in db"
               (is (= #{[applicant "resource1"] [applicant "resource2"]}
                      (set (map (juxt :userid :resid) (db/get-entitlements {:application app-id}))))))
             (testing "entitlements were POSTed to callbacks"
               (let [add-paths (requests-for-paths server "/add")
                     ga4gh-paths (requests-for-paths server "/ga4gh")]
                 (is (= #{{:path "/add" :body [{:application app-id :mail "b@o.b" :resource "resource1" :user "bob"}]}
                          {:path "/add" :body [{:application app-id :mail "b@o.b" :resource "resource2" :user "bob"}]}}
                        (set add-paths)))
                 (is (= 2 (count ga4gh-paths)))
                 (is (every? is-valid-ga4gh? ga4gh-paths))))))))

      (testing "approved application, more accepted licenses generates more entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-data/command! {:type :application.command/accept-licenses
                                :application-id app-id
                                :actor member
                                :accepted-licenses [lic-id1 lic-id2]}) ; now accept all licenses

           (entitlements/process-outbox!)

           (testing "all entitlements exist in db"
             (is (= #{[applicant "resource1"] [applicant "resource2"]
                      [member "resource1"] [member "resource2"]}
                    (set (map (juxt :userid :resid) (db/get-entitlements {:application app-id}))))))
           (testing "new entitlements were POSTed to callbacks"
             (let [add-paths (requests-for-paths server "/add")
                   ga4gh-paths (requests-for-paths server "/ga4gh")]
               (is (= #{{:path "/add" :body [{:resource "resource1" :application app-id :user "elsa" :mail "e.l@s.a"}]}
                        {:path "/add" :body [{:resource "resource2" :application app-id :user "elsa" :mail "e.l@s.a"}]}}
                      (set add-paths)))
               (is (= 2 (count ga4gh-paths)))
               (is (every? is-valid-ga4gh? ga4gh-paths)))))))

      (testing "removing a member ends entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-data/command! {:type :application.command/remove-member
                                :application-id app-id
                                :actor admin
                                :member {:userid member}
                                :comment "Left team"})

           (entitlements/process-outbox!)

           (testing "entitlements removed from db"
             (is (= #{[applicant "resource1"] [applicant "resource2"]}
                    (set (map (juxt :userid :resid) (db/get-entitlements {:application app-id :active-at (time/now)}))))))
           (testing "removed entitlements were POSTed to callback"
             (is (= #{{:path "/remove" :body [{:resource "resource1" :application app-id :user "elsa" :mail "e.l@s.a"}]}
                      {:path "/remove" :body [{:resource "resource2" :application app-id :user "elsa" :mail "e.l@s.a"}]}}
                    (set (get-requests server))))))))

      (testing "changing resources changes entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-data/command! {:type :application.command/change-resources
                                :application-id app-id
                                :actor admin
                                :catalogue-item-ids [item1 item3]
                                :comment "Removed second resource, added third resource"})

           (entitlements/process-outbox!)

           (testing "entitlements changed in db"
             (is (= #{[applicant "resource1"] [applicant "resource3"]}
                    (set (map (juxt :userid :resid) (db/get-entitlements {:application app-id :active-at (time/now)}))))))
           (testing "entitlement changes POSTed to callbacks"
             (let [add-paths (requests-for-paths server "/add")
                   remove-paths (requests-for-paths server "/remove")
                   ga4gh-paths (requests-for-paths server "/ga4gh")]
               (is (= #{{:path "/add" :body [{:resource "resource3" :application app-id :user "bob" :mail "b@o.b"}]}}
                      (set add-paths)))
               (is (= #{{:path "/remove" :body [{:resource "resource2" :application app-id :user "bob" :mail "b@o.b"}]}}
                      (set remove-paths)))
               (is (= 1 (count ga4gh-paths)))
               (is (every? is-valid-ga4gh? ga4gh-paths)))))))

      (testing "closed application should end entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-data/command! {:type :application.command/close
                                :application-id app-id
                                :actor admin
                                :comment "Finished"})

           (entitlements/process-outbox!)

           (testing "entitlements ended in db"
             (is (= [] (db/get-entitlements {:application app-id :active-at (time/now)}))))
           (testing "ended entitlements POSTed to callback"
             (is (= #{{:path "/remove" :body [{:resource "resource1" :application app-id :user "bob" :mail "b@o.b"}]}
                      {:path "/remove" :body [{:resource "resource3" :application app-id :user "bob" :mail "b@o.b"}]}}
                    (set (get-requests server)))))))))

    (let [app-id (test-data/create-application! {:actor applicant :catalogue-item-ids [item1]})]
      (test-data/command! {:type :application.command/accept-licenses
                           :application-id app-id
                           :accepted-licenses [lic-id1 lic-id2]
                           :actor applicant})
      (test-data/command! {:type :application.command/submit
                           :application-id app-id
                           :actor applicant})
      (test-data/command! {:type :application.command/approve
                           :application-id app-id
                           :actor admin
                           :comment ""})

      (entitlements/process-outbox!)

      (testing "revoked application should end entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-data/command! {:type :application.command/revoke
                                :application-id app-id
                                :actor admin
                                :comment "Banned"})

           (entitlements/process-outbox!)

           (testing "entitlements ended in db"
             (is (= [] (db/get-entitlements {:application app-id :active-at (time/now)}))))
           (testing "ended entitlements POSTed to callback"
             (is (= [{:path "/remove" :body [{:resource "resource1" :application app-id :user "bob" :mail "b@o.b"}]}]
                    (get-requests server))))))))))
