(ns ^:integration rems.db.test-entitlements
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.config]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [reset-caches-fixture test-db-fixture rollback-db-fixture]]
            [rems.json :as json]
            [rems.testing-util :refer [fixed-time-fixture suppress-logging]]
            [stub-http.core :as stub]))

(def +test-time+ (time/date-time 2050 01 01)) ;; needs to be in the future so that catalogue items are active
(def +test-time-string+ "2050-01-01T00:00:00.000Z")

(use-fixtures
  :once
  (fixed-time-fixture +test-time+)
  (suppress-logging "rems.db.entitlements")
  test-db-fixture
  rollback-db-fixture
  reset-caches-fixture)

(def +entitlements+
  [{:resid "res1" :catappid 11 :userid "user1" :start (time/date-time 2001 10 11) :mail "user1@tes.t" :end (time/date-time 2003 10 11)}
   {:resid "res2" :catappid 12 :userid "user2" :start (time/date-time 2002 10 11) :mail "user2@tes.t"}])

(def +expected-payload+
  [{:resource "res1" :application 11 :user "user1" :mail "user1@tes.t" :end "2003-10-11T00:00:00.000Z"}
   {:resource "res2" :application 12 :user "user2" :mail "user2@tes.t" :end nil}])

(defn run-with-server
  [endpoint-spec callback]
  (with-open [server (stub/start! {"/add" endpoint-spec
                                   "/remove" endpoint-spec})]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :entitlements-target {:add (str (:uri server) "/add")
                                                               :remove (str (:uri server) "/remove")})]
      (callback server))))

(defn ega-config [server]
  {:id :test-entitlement-push
   :type :ega
   :connect-server-url (str (:uri server) "/c")
   :permission-server-url (str (:uri server) "/p")})

(defn run-with-ega-server
  [spec callback]
  (with-open [server (stub/start! spec)]
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :entitlement-push [(ega-config server)])]
      (callback server))))

(deftest test-post-entitlements!
  (testing "ok :add action"
    (run-with-server
     {:status 200}
     (fn [server]
       (is (nil? (#'entitlements/post-entitlements! {:action :add :type :basic :entitlements +entitlements+})))
       (is (= [+expected-payload+] (for [r (stub/recorded-requests server)]
                                     (json/parse-string (get-in r [:body "postData"]))))))))

  (testing "not-found"
    (run-with-server
     {:status 404}
     (fn [_]
       (is (= "failed: 404" (#'entitlements/post-entitlements! {:action :add :type :basic :entitlements +entitlements+}))))))
  (testing "timeout"
    (run-with-server
     {:status 200 :delay 5000} ;; timeout of 2500 in code
     (fn [_]
       (is (= "failed: exception" (#'entitlements/post-entitlements! {:action :add :type :basic :entitlements +entitlements+}))))))
  (testing "invalid url"
    (with-redefs [rems.config/env (assoc rems.config/env
                                         :entitlements-target {:add "http://invalid/entitlements"})]
      (is (= "failed: exception" (#'entitlements/post-entitlements! {:action :add :type :basic :entitlements +entitlements+})))))
  (testing "no server configured"
    (is (nil? (#'entitlements/post-entitlements! {:action :add :type :basic :entitlements +entitlements+})))))

(defn- get-requests [server]
  (doall
   (for [req (stub/recorded-requests server)]
     {:path (:path req)
      :body (json/parse-string (get-in req [:body "postData"]))})))

(defn- requests-for-paths [server ^String path]
  (filter #(= (% :path) path) (set (get-requests server))))

(deftest test-entitlement-granting
  (let [applicant "bob"
        member "elsa"
        admin "owner"
        wfid (test-helpers/create-workflow! {:handlers [admin]})
        form-id (test-helpers/create-form! {})
        lic-id1 (test-helpers/create-license! {})
        lic-id2 (test-helpers/create-license! {})
        item1 (test-helpers/create-catalogue-item!
               {:resource-id (test-helpers/create-resource! {:resource-ext-id "resource1"
                                                             :license-ids [lic-id1]})
                :form-id form-id
                :workflow-id wfid})
        item2 (test-helpers/create-catalogue-item!
               {:resource-id (test-helpers/create-resource! {:resource-ext-id "resource2"
                                                             :license-ids [lic-id2]})
                :form-id form-id
                :workflow-id wfid})
        item3 (test-helpers/create-catalogue-item!
               {:resource-id (test-helpers/create-resource! {:resource-ext-id "resource3"
                                                             :license-ids [lic-id1]})
                :form-id form-id
                :workflow-id wfid})]
    (test-helpers/create-user! {:userid applicant :email "b@o.b" :name "Bob"})
    (test-helpers/create-user! {:userid member :email "e.l@s.a" :name "Elsa"})
    (test-helpers/create-user! {:userid admin :email "o.w@n.er" :name "Owner"})

    (entitlements/process-outbox!) ;; empty outbox from pending posts

    (let [app-id (test-helpers/create-application! {:actor applicant :catalogue-item-ids [item1 item2]})]
      (testing "submitted application should not yet cause entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-helpers/command! {:type :application.command/accept-licenses
                                   :application-id app-id
                                   :accepted-licenses [lic-id1 lic-id2]
                                   :actor applicant})
           (test-helpers/command! {:type :application.command/submit
                                   :application-id app-id
                                   :actor applicant})
           (test-helpers/command! {:type :application.command/add-member
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
             (test-helpers/command! {:type :application.command/approve
                                     :application-id app-id
                                     :actor admin
                                     :comment ""})
             (test-helpers/command! {:type :application.command/accept-licenses
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
               (let [add-paths (requests-for-paths server "/add")]
                 (is (= #{{:path "/add" :body [{:application app-id :mail "b@o.b" :resource "resource1" :user "bob" :end nil}]}
                          {:path "/add" :body [{:application app-id :mail "b@o.b" :resource "resource2" :user "bob" :end nil}]}}
                        (set add-paths)))))))))

      (testing "approved application, more accepted licenses generates more entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-helpers/command! {:type :application.command/accept-licenses
                                   :application-id app-id
                                   :actor member
                                   :accepted-licenses [lic-id1 lic-id2]}) ; now accept all licenses

           (entitlements/process-outbox!)

           (testing "all entitlements exist in db"
             (is (= #{[applicant "resource1"] [applicant "resource2"]
                      [member "resource1"] [member "resource2"]}
                    (set (map (juxt :userid :resid) (db/get-entitlements {:application app-id}))))))
           (testing "new entitlements were POSTed to callbacks"
             (let [add-paths (requests-for-paths server "/add")]
               (is (= #{{:path "/add" :body [{:resource "resource1" :application app-id :user "elsa" :mail "e.l@s.a" :end nil}]}
                        {:path "/add" :body [{:resource "resource2" :application app-id :user "elsa" :mail "e.l@s.a" :end nil}]}}
                      (set add-paths))))))))

      (testing "removing a member ends entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-helpers/command! {:type :application.command/remove-member
                                   :application-id app-id
                                   :actor admin
                                   :member {:userid member}
                                   :comment "Left team"})
           (entitlements/process-outbox!)

           (testing "entitlements removed from db"
             (is (= #{[applicant "resource1"] [applicant "resource2"]}
                    (set (map (juxt :userid :resid) (db/get-entitlements {:application app-id :active-at (time/now)}))))))
           (testing "removed entitlements were POSTed to callback"
             (is (= #{{:path "/remove" :body [{:resource "resource1" :application app-id :user "elsa" :mail "e.l@s.a" :end +test-time-string+}]}
                      {:path "/remove" :body [{:resource "resource2" :application app-id :user "elsa" :mail "e.l@s.a" :end +test-time-string+}]}}
                    (set (get-requests server))))))))

      (testing "changing resources changes entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-helpers/command! {:type :application.command/change-resources
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
                   remove-paths (requests-for-paths server "/remove")]
               (is (= #{{:path "/add" :body [{:resource "resource3" :application app-id :user "bob" :mail "b@o.b" :end nil}]}}
                      (set add-paths)))
               (is (= #{{:path "/remove" :body [{:resource "resource2" :application app-id :user "bob" :mail "b@o.b" :end +test-time-string+}]}}
                      (set remove-paths))))))))

      (testing "closed application should end entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-helpers/command! {:type :application.command/close
                                   :application-id app-id
                                   :actor admin
                                   :comment "Finished"})

           (entitlements/process-outbox!)

           (testing "entitlements ended in db"
             (is (= [] (db/get-entitlements {:application app-id :active-at (time/now)}))))
           (testing "ended entitlements POSTed to callback"
             (is (= #{{:path "/remove" :body [{:resource "resource1" :application app-id :user "bob" :mail "b@o.b" :end +test-time-string+}]}
                      {:path "/remove" :body [{:resource "resource3" :application app-id :user "bob" :mail "b@o.b" :end +test-time-string+}]}}
                    (set (get-requests server)))))))))

    (testing "approve with end time"
      (let [end (time/date-time 2100 01 01)
            app-id (test-helpers/create-application! {:actor applicant :catalogue-item-ids [item1]})]
        (test-helpers/command! {:type :application.command/accept-licenses
                                :application-id app-id
                                :accepted-licenses [lic-id1 lic-id2]
                                :actor applicant})
        (test-helpers/command! {:type :application.command/submit
                                :application-id app-id
                                :actor applicant})
        (test-helpers/command! {:type :application.command/approve
                                :application-id app-id
                                :actor admin
                                :entitlement-end end
                                :comment ""})

        (run-with-server
         {:status 200}
         (fn [server]
           (entitlements/process-outbox!)

           (is (= [{:resid "resource1" :userid applicant :end (time/date-time 2100 01 01)}]
                  (mapv #(select-keys % [:resid :userid :end]) (db/get-entitlements {:application app-id}))))
           (is (= [{:path "/add" :body [{:resource "resource1" :application app-id :user "bob" :mail "b@o.b" :end "2100-01-01T00:00:00.000Z"}]}]
                  (requests-for-paths server "/add")))))))

    (let [app-id (test-helpers/create-application! {:actor applicant :catalogue-item-ids [item1]})]
      (test-helpers/command! {:type :application.command/accept-licenses
                              :application-id app-id
                              :accepted-licenses [lic-id1 lic-id2]
                              :actor applicant})
      (test-helpers/command! {:type :application.command/submit
                              :application-id app-id
                              :actor applicant})
      (test-helpers/command! {:type :application.command/approve
                              :application-id app-id
                              :actor admin
                              :comment ""})

      (entitlements/process-outbox!)

      (testing "revoked application should end entitlements"
        (run-with-server
         {:status 200}
         (fn [server]
           (test-helpers/command! {:type :application.command/revoke
                                   :application-id app-id
                                   :actor admin
                                   :comment "Banned"})

           (entitlements/process-outbox!)

           (testing "entitlements ended in db"
             (is (= [] (db/get-entitlements {:application app-id :active-at (time/now)}))))
           (testing "ended entitlements POSTed to callback"
             (is (= [{:path "/remove" :body [{:resource "resource1" :application app-id :user "bob" :mail "b@o.b" :end +test-time-string+}]}]
                    (get-requests server))))))))))


(deftest test-entitlement-push
  (let [post-data-for (fn [server]
                        {:action :add
                         :type :ega
                         :entitlements [{:resid "EGAD00001006673" :userid "elixir-user" :start (time/date-time 2002 10 11) :mail "elixir-user@elixir.org"}]
                         :config (ega-config server)})]
    (testing "ok :add action"
      (run-with-ega-server
       {"/p/permissions" {:status 200 :content-type "application/json" :body (json/generate-string {})}}
       (fn [server]
         (is (= nil (#'entitlements/post-entitlements! (post-data-for server))))
         (let [post (update-in (first (stub/recorded-requests server)) [:body "postData"] json/parse-string)]
           (is (= "elixir-user" (get-in post [:headers :x-account-id]))))))) ; sanity check only, content test is in ega.clj

    (testing "not-found"
      (run-with-ega-server
       {"/p/permissions" {:status 404}}
       (fn [server]
         (is (= 404 (:status (#'entitlements/post-entitlements! (post-data-for server))))))))

    (testing "timeout"
      (run-with-ega-server
       {"/p/permissions" {:status 200 :delay 5000}} ;; timeout of 2500 in code
       (fn [server]
         (is (= {:status "exception"} (#'entitlements/post-entitlements! (post-data-for server)))))))

    (testing "invalid url"
      (is (= {:status "exception"} (#'entitlements/post-entitlements! (post-data-for {:uri "http://invalid/address/altogether"})))))

    (testing "no server configured means no problem"
      (is (= nil (#'entitlements/post-entitlements! (dissoc (post-data-for {:uri "http://invalid/address/no/problem"}) :config)))))))
