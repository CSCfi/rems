(ns rems.db.approvals
  "Approvals tests that require transactions."
  (:require [rems.db.core :as db]
            [rems.context :as context]
            [rems.contents :as contents]
            [rems.db.applications :as applications]
            [rems.db.approvals :as approvals]
            [rems.db.roles :as roles]
            [rems.env :refer [*db*]]
            [rems.util :refer [get-user-id]]
            [luminus-migrations.core :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [rems.config :refer [env]]
            [mount.core :as mount]
            [conman.core :as conman]
            [cheshire.core :refer :all]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'rems.config/env
      #'rems.env/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (f)
    (mount/stop)))

(deftest test-approve-transaction
  (binding [context/*user* {"eppn" "tester"}]
    (testing "with workflow and approver"
      (let [uid (get-user-id)
            resid (:id (db/create-resource! {:id 27 :resid "" :prefix "" :modifieruserid 1}))
            wfid (:id (db/create-workflow! {:owneruserid "" :modifieruserid "" :title "" :fnlround 0}))
            item (:id (db/create-catalogue-item! {:title "" :form nil :resid resid :wfid wfid}))
            app (applications/create-new-draft item)

            get (fn [app-id]
                  (let [apps (db/get-applications {:id app-id})]
                    (is (= 1 (count apps)))
                    (select-keys (first apps) [:state :curround])))]

        (db/create-workflow-approver! {:wfid wfid :appruserid uid :round 0})
        (db/update-application-state! {:id app :user uid :state "applied" :curround 0})

        (testing "when a db function throws"
          (with-redefs [db/add-application-approval! (fn [& _] (throw (Exception. "oops")))]
            (is (thrown? Exception (approvals/approve app 0 "approval should rollback")))
            (is (= {:state "applied" :curround 0} (get app)) "state should be same"))

          (with-redefs [db/update-application-state! (fn [& _] (throw (Exception. "oops")))]
            (is (thrown? Exception (approvals/approve app 0 "approval should rollback")))
            (is (= {:state "applied" :curround 0} (get app)) "state should be same"))

          (with-redefs [db/add-entitlement! (fn [& _] (throw (Exception. "oops")))]
            (is (thrown? Exception (approvals/approve app 0 "approval should rollback")))
            (is (= {:state "applied" :curround 0} (get app)) "state should be same"))
          )))))
