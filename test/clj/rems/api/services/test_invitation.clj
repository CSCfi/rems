(ns ^:integration rems.api.services.test-invitation
  (:require [clojure.test :refer :all]
            [rems.api.services.invitation :as invitation]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [caches-fixture rollback-db-fixture test-db-fixture]]
            [rems.testing-util :refer [fixed-time-fixture with-user]])
  (:import [org.joda.time DateTime DateTimeZone DateTimeUtils]))

(def test-time (DateTime. 10000 DateTimeZone/UTC))

(use-fixtures
  :once
  test-db-fixture
  caches-fixture
  (fixed-time-fixture test-time))

(use-fixtures :each rollback-db-fixture)

(deftest test-crud-invitation
  (test-helpers/create-user! {:eppn "owner" :commonName "owner" :mail "owner@example.com"} :owner)
  (test-helpers/create-organization! {:organization/id "abc" :organization/name {:en "ABC"} :organization/short-name {:en "ABC"}})
  (let [workflow-id (test-helpers/create-workflow! {})]


    (with-user "owner"
      (testing "before creating invitations"
        (is (= [] (invitation/get-invitations nil))))

      ;;(DateTimeUtils/setCurrentMillisFixed (.getMillis test-time2))
      (testing "creating invitations"
        (testing "without any type"
          (is (= {:success false
                  :errors
                  [{:type :t.actions.errors/invalid-invitation-type :workflow-id nil}]}
                 (invitation/create-invitation! {:userid "owner"
                                                 :email "kek@kone.net"}))))

        (testing "with invalid workflow"
          (is (= {:success false
                  :errors [{:type :t.actions.errors/invalid-workflow :workflow-id 42}]}
                 (invitation/create-invitation! {:userid "owner"
                                                 :email "kek@kone.net"
                                                 :workflow-id 42}))))

        (testing "success"
          (is (= {:success true}
                 (dissoc (invitation/create-invitation! {:userid "owner"
                                                         :email "kek@kone.net"
                                                         :workflow-id workflow-id})
                         :id)))))
      (testing "after creating invitations"
        (is (= [{:invitation/email "kek@kone.net"
                 :invitation/invited-by "owner"
                 :invitation/created test-time
                 :invitation/workflow {:workflow/id workflow-id}}]
               (mapv #(dissoc % :invitation/token :invitation/id)
                     (invitation/get-invitations nil))))))))

;; TODO test id is generated
;; TODO test token is generated
