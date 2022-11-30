(ns ^:integration rems.service.test-invitation
  (:require [clojure.test :refer :all]
            [rems.service.invitation :as invitation]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [reset-caches-fixture rollback-db-fixture test-db-fixture]]
            [rems.email.core :as email]
            [rems.testing-util :refer [fixed-time-fixture with-user]])
  (:import [org.joda.time DateTime DateTimeUtils DateTimeZone]))

(def invitation-time (DateTime. 10000 DateTimeZone/UTC))
(def acceptance-time (DateTime. 20000 DateTimeZone/UTC))

(use-fixtures
  :once
  test-db-fixture
  reset-caches-fixture
  (fixed-time-fixture invitation-time))

(use-fixtures :each rollback-db-fixture)

(deftest test-crud-invitation
  (test-helpers/create-user! {:userid "owner" :name "owner" :email "owner@example.com"} :owner)
  (test-helpers/create-user! {:userid "new-handler" :name "New Handler" :email "new-handler@example.com"})
  (let [workflow-id (test-helpers/create-workflow! {})]
    (with-user "owner"
      (testing "before creating invitations"
        (is (= [] (invitation/get-invitations nil))))

      (testing "creating invitations"
        (testing "without any type"
          (is (= {:success false
                  :errors
                  [{:type :t.accept-invitation.errors/invalid-invitation-type :workflow-id nil}]}
                 (invitation/create-invitation! {:userid "owner"
                                                 :name "Dorothy Vaughan"
                                                 :email "dorothy.vaughan@nasa.gov"}))))

        (testing "with invalid workflow"
          (is (= {:success false
                  :errors [{:type :t.accept-invitation.errors/invalid-workflow :workflow-id 100042}]}
                 (invitation/create-invitation! {:userid "owner"
                                                 :name "Dorothy Vaughan"
                                                 :email "dorothy.vaughan@nasa.gov"
                                                 :workflow-id 100042}))))

        (testing "success"
          (let [sent-email (atom nil)]
            (with-redefs [email/enqueue-email! (fn [& args] (reset! sent-email args))]
              (let [invitation (invitation/create-invitation! {:userid "owner"
                                                               :name "Dorothy Vaughan"
                                                               :email "dorothy.vaughan@nasa.gov"
                                                               :workflow-id workflow-id})
                    invitation-id (:invitation/id invitation)]
                (is (= {:success true} (dissoc invitation :invitation/id)))
                (is (number? invitation-id))
                (is (= "dorothy.vaughan@nasa.gov" (:to (first @sent-email))))

                (testing "after creating invitations"
                  (let [invitations (invitation/get-invitations nil)]
                    (is (= [{:invitation/name "Dorothy Vaughan"
                             :invitation/email "dorothy.vaughan@nasa.gov"
                             :invitation/invited-by {:userid "owner"
                                                     :name "Owner"
                                                     :email "owner@example.com"}
                             :invitation/created invitation-time
                             :invitation/sent invitation-time
                             :invitation/workflow {:workflow/id workflow-id}}]
                           (mapv #(dissoc % :invitation/token :invitation/id)
                                 invitations)))
                    (is (contains? (set (map :invitation/id invitations)) invitation-id))
                    (is (= :not-present (:invitation/token (first invitations) :not-present)) "must not reveal token")

                    (testing "accept invitation with fake code"
                      (let [response (invitation/accept-invitation! {:userid "new-handler"
                                                                     :token "doesnotexist"})]
                        (is (= {:success false
                                :errors [{:key :t.accept-invitation.errors/invalid-token :token "doesnotexist"}]}
                               response))))

                    (DateTimeUtils/setCurrentMillisFixed (.getMillis acceptance-time))

                    (testing "accept invitation"
                      (let [token (-> invitation-id invitation/get-invitation-full first :invitation/token)
                            response (invitation/accept-invitation! {:userid "new-handler"
                                                                     :token token})]
                        (is (= {:success true
                                :invitation/workflow {:workflow/id workflow-id}}
                               response))))

                    (testing "after accepting invitation"
                      (let [invitations (invitation/get-invitations nil)]
                        (is (= [{:invitation/name "Dorothy Vaughan"
                                 :invitation/email "dorothy.vaughan@nasa.gov"
                                 :invitation/invited-user {:userid "new-handler"
                                                           :name "New Handler"
                                                           :email "new-handler@example.com"}
                                 :invitation/invited-by {:userid "owner"
                                                         :name "Owner"
                                                         :email "owner@example.com"}
                                 :invitation/created invitation-time
                                 :invitation/sent invitation-time
                                 :invitation/accepted acceptance-time
                                 :invitation/workflow {:workflow/id workflow-id}}]
                               (mapv #(dissoc % :invitation/token :invitation/id)
                                     invitations)))))

                    (testing "accept invitation again"
                      (let [token (-> invitation-id invitation/get-invitation-full first :invitation/token)
                            response (invitation/accept-invitation! {:userid "new-handler"
                                                                     :token token})]
                        (is (= {:success false
                                :errors [{:key :t.accept-invitation.errors.already-member/workflow}]}
                               response))))))))))))))
