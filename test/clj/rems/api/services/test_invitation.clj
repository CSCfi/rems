(ns ^:integration rems.api.services.test-invitation
  (:require [clojure.test :refer :all]
            [rems.api.services.invitation :as invitation]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [caches-fixture rollback-db-fixture test-db-fixture]]
            [rems.email.core :as email]
            [rems.testing-util :refer [fixed-time-fixture with-user]])
  (:import [org.joda.time DateTime DateTimeZone]))

(def test-time (DateTime. 10000 DateTimeZone/UTC))

(use-fixtures
  :once
  test-db-fixture
  caches-fixture
  (fixed-time-fixture test-time))

(use-fixtures :each rollback-db-fixture)

(deftest test-crud-invitation
  (test-helpers/create-user! {:eppn "owner" :commonName "owner" :mail "owner@example.com"} :owner)
  (let [workflow-id (test-helpers/create-workflow! {})]
    (with-user "owner"
      (testing "before creating invitations"
        (is (= [] (invitation/get-invitations nil))))

      (testing "creating invitations"
        (testing "without any type"
          (is (= {:success false
                  :errors
                  [{:type :errors/invalid-invitation-type :workflow-id nil}]}
                 (invitation/create-invitation! {:userid "owner"
                                                 :name "Dorothy Vaughan"
                                                 :email "dorothy.vaughan@nasa.gov"}))))

        (testing "with invalid workflow"
          (is (= {:success false
                  :errors [{:type :errors/invalid-workflow :workflow-id 42}]}
                 (invitation/create-invitation! {:userid "owner"
                                                 :name "Dorothy Vaughan"
                                                 :email "dorothy.vaughan@nasa.gov"
                                                 :workflow-id 42}))))

        (testing "success"
          (let [sent-email (atom nil)]
            (with-redefs [email/enqueue-email! (fn [& args] (reset! sent-email args))]
              (let [invitation (invitation/create-invitation! {:userid "owner"
                                                               :name "Dorothy Vaughan"
                                                               :email "dorothy.vaughan@nasa.gov"
                                                               :workflow-id workflow-id})]
                (is (= {:success true} (dissoc invitation :invitation/id)))
                (is (number? (:invitation/id invitation)))
                (is (= "dorothy.vaughan@nasa.gov" (:to (first @sent-email)))))))))

      (testing "after creating invitations"
        (let [invitations (invitation/get-invitations nil)]
          (is (= [{:invitation/name "Dorothy Vaughan"
                   :invitation/email "dorothy.vaughan@nasa.gov"
                   :invitation/invited-by {:userid "owner"
                                           :name "Owner"
                                           :email "owner@example.com"}
                   :invitation/created test-time
                   :invitation/workflow {:workflow/id workflow-id}}]
                 (mapv #(dissoc % :invitation/token :invitation/id)
                       invitations)))
          (is (number? (:invitation/id (first invitations))))
          (is (= :not-present (:invitation/token (first invitations) :not-present)) "must not reveal token"))))))
