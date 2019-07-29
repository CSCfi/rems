(ns ^:integration rems.test-db
  "Namespace for tests that use an actual database."
  (:require [cheshire.core :as cheshire]
            [clj-time.core :as time]
            [clojure.string :refer [split-lines]]
            [clojure.test :refer :all]
            [rems.config]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [rems.db.form :as form]
            [rems.db.roles :as roles]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.db.users :as users]
            [rems.poller.entitlements :as entitlements-poller]
            [rems.testing-tempura :refer [fake-tempura-fixture]]
            [rems.util :refer [get-user-id]])
  (:import (rems.auth ForbiddenException)))

(use-fixtures :once fake-tempura-fixture test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-get-catalogue-items
  (testing "without catalogue items"
    (is (empty? (db/get-catalogue-items))))

  (testing "with two items"
    (let [item1 (test-data/create-catalogue-item! {})
          item2 (test-data/create-catalogue-item! {})]
      (is (= (set [item1 item2]) (set (map :id (db/get-catalogue-items))))
          "should find the two items")
      (is (= item1 (:id (first (db/get-catalogue-items {:ids [item1]}))))
          "should find same catalogue item by id")
      (is (= item2 (:id (first (db/get-catalogue-items {:ids [item2]}))))
          "should find same catalogue item by id"))))

(deftest test-multi-applications
  (db/add-user! {:user "test-user" :userattrs nil})
  (db/add-user! {:user "handler" :userattrs nil})
  (let [applicant "test-user"
        wfid (test-data/create-dynamic-workflow! {:handlers ["handler"]})
        res1 (test-data/create-resource! {:resource-ext-id "resid111"})
        res2 (test-data/create-resource! {:resource-ext-id "resid222"})
        form-id (test-data/create-form! {})
        item1 (test-data/create-catalogue-item! {:form-id form-id :resource-id res1 :workflow-id wfid})
        item2 (test-data/create-catalogue-item! {:form-id form-id :resource-id res2 :workflow-id wfid})
        app-id (test-data/create-application! {:catalogue-item-ids [item1 item2]
                                               :actor applicant})]
    (test-data/run! {:type :application.command/submit
                     :actor applicant
                     :application-id app-id
                     :time (time/now)})
    (test-data/run! {:type :application.command/approve
                     :actor "handler"
                     :application-id app-id
                     :time (time/now)
                     :comment ""})
    (is (= :application.state/approved (:application/state (applications/get-application applicant app-id))))

    (entitlements-poller/run)
    (is (= ["resid111" "resid222"] (sort (map :resid (db/get-entitlements {:application app-id}))))
        "should create entitlements for both resources")))

(deftest test-users
  (db/add-user! {:user "pekka", :userattrs nil})
  (db/add-user! {:user "simo", :userattrs nil})
  (is (= 2 (count (db/get-users))))
  (db/add-user! {:user "pekka", :userattrs (cheshire/generate-string {:key "value"})})
  (db/add-user! {:user "simo", :userattrs nil})
  (is (= 2 (count (db/get-users))))
  (is (= {:key "value"} (users/get-user-attributes "pekka"))))

(deftest test-roles
  (db/add-user! {:user "pekka", :userattrs nil})
  (db/add-user! {:user "simo", :userattrs nil})
  (roles/add-role! "pekka" :owner)
  (roles/add-role! "pekka" :owner) ;; add should be idempotent
  (is (= #{:logged-in :owner} (roles/get-roles "pekka")))
  (is (= #{:logged-in} (roles/get-roles "simo")))
  (is (= #{:logged-in} (roles/get-roles "juho"))) ;; default role
  (is (thrown? RuntimeException (roles/add-role! "pekka" :unknown-role))))

(deftest test-get-entitlements-for-export
  (db/add-user! {:user "handler" :userattrs nil})
  (db/add-user! {:user "jack" :userattrs nil})
  (db/add-user! {:user "jill" :userattrs nil})
  (let [wf (test-data/create-dynamic-workflow! {:handlers ["handler"]})
        form-id (test-data/create-form! {})
        res1 (test-data/create-resource! {:resource-ext-id "resource1"})
        res2 (test-data/create-resource! {:resource-ext-id "resource2"})
        item1 (test-data/create-catalogue-item! {:form-id form-id :resource-id res1 :workflow-id wf})
        item2 (test-data/create-catalogue-item! {:form-id form-id :resource-id res2 :workflow-id wf})
        jack-app (test-data/create-application! {:actor "jack" :catalogue-item-ids [item1]})
        jill-app (test-data/create-application! {:actor "jill" :catalogue-item-ids [item1 item2]})]
    (test-data/run! {:type :application.command/submit
                     :time (time/now)
                     :actor "jack"
                     :application-id jack-app})
    (test-data/run! {:type :application.command/approve
                     :time (time/now)
                     :actor "handler"
                     :application-id jack-app
                     :comment ""})
    (test-data/run! {:type :application.command/submit
                     :time (time/now)
                     :actor "jill"
                     :application-id jill-app})
    (test-data/run! {:type :application.command/approve
                     :time (time/now)
                     :actor "handler"
                     :application-id jill-app
                     :comment ""})
    (entitlements-poller/run)

    (binding [context/*roles* #{:handler}]
      (let [lines (split-lines (entitlements/get-entitlements-for-export))]
        (is (= 4 (count lines))) ;; header + 3 resources
        (is (some #(and (.contains % "resource1")
                        (.contains % "jill")
                        (.contains % (str jill-app)))
                  lines))
        (is (some #(and (.contains % "resource2")
                        (.contains % "jill")
                        (.contains % (str jill-app)))
                  lines))
        (is (some #(and (.contains % "resource1")
                        (.contains % "jack")
                        (.contains % (str jack-app)))
                  lines))))
    (binding [context/*roles* #{:applicant}]
      (is (thrown? ForbiddenException
                   (entitlements/get-entitlements-for-export))))))

(deftest test-create-demo-data!
  ;; just a smoke test, check that create-demo-data doesn't fail
  (test-data/create-demo-data!)
  (is true))
