(ns rems.api.test-applications-v2
  (:require [clojure.test :refer :all]
            [rems.api.applications-v2 :refer :all]
            [rems.common-util :refer [deep-merge]]
            [rems.permissions :as permissions]
            [rems.application.model :as model]))

(deftest test-apply-user-permissions
  (let [application (-> (model/application-view nil {:event/type :application.event/created
                                                     :event/actor "applicant"
                                                     :workflow.dynamic/handlers #{"handler"}})
                        (permissions/give-role-to-users :role-1 ["user-1"])
                        (permissions/give-role-to-users :role-2 ["user-2"])
                        (permissions/set-role-permissions {:role-1 []
                                                           :role-2 [:foo :bar]}))]
    (testing "users with a role can see the application"
      (is (not (nil? (apply-user-permissions application "user-1")))))
    (testing "users without a role cannot see the application"
      (is (nil? (apply-user-permissions application "user-3"))))
    (testing "lists the user's permissions"
      (is (= #{} (:application/permissions (apply-user-permissions application "user-1"))))
      (is (= #{:foo :bar} (:application/permissions (apply-user-permissions application "user-2")))))
    (testing "lists the user's roles"
      (is (= #{:role-1} (:application/roles (apply-user-permissions application "user-1"))))
      (is (= #{:role-2} (:application/roles (apply-user-permissions application "user-2")))))

    (let [all-events [{:event/type :application.event/created}
                      {:event/type :application.event/submitted}
                      {:event/type :application.event/comment-requested}]
          restricted-events [{:event/type :application.event/created}
                             {:event/type :application.event/submitted}]
          application (-> application
                          (assoc :application/events all-events)
                          (permissions/set-role-permissions {:role-1 [:see-everything]}))]
      (testing "privileged users"
        (let [application (apply-user-permissions application "user-1")]
          (testing "see all events"
            (is (= all-events
                   (:application/events application))))
          (testing "see dynamic workflow handlers"
            (is (= #{"handler"}
                   (get-in application [:application/workflow :workflow.dynamic/handlers]))))))

      (testing "normal users"
        (let [application (apply-user-permissions application "user-2")]
          (testing "see only some events"
            (is (= restricted-events
                   (:application/events application))))
          (testing "don't see dynamic workflow handlers"
            (is (= nil
                   (get-in application [:application/workflow :workflow.dynamic/handlers])))))))

    (testing "invitation tokens are not visible to anybody"
      (let [application (model/application-view application {:event/type :application.event/member-invited
                                                             :application/member {:name "member"
                                                                                  :email "member@example.com"}
                                                             :invitation/token "secret"})]
        (testing "- original"
          (is (= {"secret" {:name "member"
                            :email "member@example.com"}}
                 (:application/invitation-tokens application)))
          (is (= nil
                 (:application/invited-members application))))
        (doseq [user-id ["applicant" "handler"]]
          (testing (str "- as user " user-id)
            (let [application (apply-user-permissions application user-id)]
              (is (= nil
                     (:application/invitation-tokens application)))
              (is (= #{{:name "member"
                        :email "member@example.com"}}
                     (:application/invited-members application))))))))))
