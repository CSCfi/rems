(ns rems.application.test-master-workflow
  (:require [clojure.test :refer :all]
            [rems.application.master-workflow :refer [application-permissions-view]]
            [rems.permissions :as permissions]))

(deftest test-calculate-permissions
  (testing "reviewer may review only once"
    (let [requested (reduce application-permissions-view nil [{:event/type :application.event/created
                                                               :event/actor "applicant"}
                                                              {:event/type :application.event/submitted
                                                               :event/actor "applicant"}
                                                              {:event/type :application.event/review-requested
                                                               :event/actor "handler"
                                                               :application/reviewers ["reviewer1" "reviewer2"]}])
          reviewed (reduce application-permissions-view requested [{:event/type :application.event/reviewed
                                                                    :event/actor "reviewer1"}])]
      (is (contains? (permissions/user-permissions requested "reviewer1")
                     :application.command/review))
      (is (not (contains? (permissions/user-permissions reviewed "reviewer1")
                          :application.command/review)))
      (is (contains? (permissions/user-permissions reviewed "reviewer2")
                     :application.command/review))))

  (testing "decider may decide only once"
    (let [requested (reduce application-permissions-view nil [{:event/type :application.event/created
                                                               :event/actor "applicant"}
                                                              {:event/type :application.event/submitted
                                                               :event/actor "applicant"}
                                                              {:event/type :application.event/decision-requested
                                                               :event/actor "handler"
                                                               :application/deciders ["decider"]}])
          decided (reduce application-permissions-view requested [{:event/type :application.event/decided
                                                                   :event/actor "decider"}])]
      (is (contains? (permissions/user-permissions requested "decider")
                     :application.command/decide))
      (is (not (contains? (permissions/user-permissions decided "decider")
                          :application.command/decide)))))

  (testing "everyone can accept invitation"
    (let [created (reduce application-permissions-view nil [{:event/type :application.event/created
                                                             :event/actor "applicant"}])]
      (is (contains? (permissions/user-permissions created "joe")
                     :application.command/accept-invitation))))

  (testing "nobody can accept invitation for closed application"
    (let [closed (reduce application-permissions-view nil [{:event/type :application.event/created
                                                            :event/actor "applicant"}
                                                           {:event/type :application.event/closed
                                                            :event/actor "applicant"}])]
      (is (not (contains? (permissions/user-permissions closed "joe")
                          :application.command/accept-invitation)))
      (is (not (contains? (permissions/user-permissions closed "applicant")
                          :application.command/accept-invitation)))))

  (testing "applicant change"
    (let [original (reduce application-permissions-view nil [{:event/type :application.event/created
                                                              :event/actor "applicant"}
                                                             {:event/type :application.event/submitted
                                                              :event/actor "applicant"}
                                                             {:event/type :application.event/member-added
                                                              :event/actor "handler"
                                                              :application/member {:userid "new"}}])
          changed (reduce application-permissions-view original [{:event/type :application.event/applicant-changed
                                                                  :event/actor "handler"
                                                                  :application/applicant {:userid "new"}}])]
      (is (= #{:application.command/copy-as-new
               :application.command/remove-member
               :application.command/accept-licenses
               :application.command/uninvite-member}
             (permissions/user-permissions original "applicant")))
      (is (= #{:application.command/copy-as-new :application.command/accept-licenses}
             (permissions/user-permissions original "new")))
      (is (= #{:application.command/copy-as-new :application.command/accept-licenses}
             (permissions/user-permissions changed "applicant")))
      (is (= #{:application.command/copy-as-new
               :application.command/remove-member
               :application.command/accept-licenses
               :application.command/uninvite-member}
             (permissions/user-permissions changed "new"))))))
