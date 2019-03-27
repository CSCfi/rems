(ns rems.api.test-applications-v2
  (:require [clojure.test :refer :all]
            [lambdaisland.deep-diff :as ddiff]
            [rems.api.applications-v2 :refer :all]
            [rems.common-util :refer [deep-merge]]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.permissions :as permissions]
            [rems.workflow.dynamic :as dynamic])
  (:import [java.util UUID]
           [org.joda.time DateTime]))

(defn- validate-events [events]
  (doseq [event events]
    (applications/validate-dynamic-event event))
  events)

(deftest test-application-view
  (let [injections {:get-form {40 {:id 40
                                   :organization "org"
                                   :title "form title"
                                   :items [{:id 41
                                            :localizations {:en {:title "en title"
                                                                 :inputprompt "en placeholder"}
                                                            :fi {:title "fi title"
                                                                 :inputprompt "fi placeholder"}}
                                            :optional false
                                            :options []
                                            :maxlength 100
                                            :type "description"}
                                           {:id 42
                                            :localizations {:en {:title "en title"
                                                                 :inputprompt "en placeholder"}
                                                            :fi {:title "fi title"
                                                                 :inputprompt "fi placeholder"}}
                                            :optional false
                                            :options []
                                            :maxlength 100
                                            :type "text"}]
                                   :start (DateTime. 100)
                                   :end nil}}

                    :get-catalogue-item {10 {:id 10
                                             :resource-id 11
                                             :resid "urn:11"
                                             :wfid 50
                                             :formid 40
                                             :title "non-localized title"
                                             :localizations {:en {:id 10
                                                                  :langcode :en
                                                                  :title "en title"}
                                                             :fi {:id 10
                                                                  :langcode :fi
                                                                  :title "fi title"}}
                                             :start (DateTime. 100)
                                             :end nil
                                             :enabled true
                                             :archived false
                                             :state "enabled"}
                                         20 {:id 20
                                             :resource-id 21
                                             :resid "urn:21"
                                             :wfid 50
                                             :formid 40
                                             :title "non-localized title"
                                             :localizations {:en {:id 20
                                                                  :langcode :en
                                                                  :title "en title"}
                                                             :fi {:id 20
                                                                  :langcode :fi
                                                                  :title "fi title"}}
                                             :start (DateTime. 100)
                                             :end nil
                                             :enabled true
                                             :archived false
                                             :state "enabled"}}

                    :get-license {30 {:id 30
                                      :licensetype "link"
                                      :title "non-localized title"
                                      :textcontent "http://non-localized-license-link"
                                      :localizations {:en {:title "en title"
                                                           :textcontent "http://en-license-link"}
                                                      :fi {:title "fi title"
                                                           :textcontent "http://fi-license-link"}}
                                      :start (DateTime. 100)
                                      :end nil
                                      :enabled true
                                      :archived false}
                                  31 {:id 31
                                      :licensetype "text"
                                      :title "non-localized title"
                                      :textcontent "non-localized license text"
                                      :localizations {:en {:title "en title"
                                                           :textcontent "en license text"}
                                                      :fi {:title "fi title"
                                                           :textcontent "fi license text"}}
                                      :start (DateTime. 100)
                                      :end nil
                                      :enabled true
                                      :archived false}
                                  32 {:id 32
                                      :licensetype "attachment"
                                      :title "non-localized title"
                                      :textcontent "non-localized filename"
                                      :attachment-id 3200
                                      :localizations {:en {:title "en title"
                                                           :textcontent "en filename"
                                                           :attachment-id 3201}
                                                      :fi {:title "fi title"
                                                           :textcontent "fi filename"
                                                           :attachment-id 3202}}
                                      :start (DateTime. 100)
                                      :end nil
                                      :enabled true
                                      :archived false}}

                    :get-user {"applicant" {:eppn "applicant"
                                            :mail "applicant@example.com"
                                            :commonName "Applicant"}}}
        apply-events (fn [events]
                       (-> events
                           validate-events
                           (build-application-view injections)
                           permissions/cleanup))]

    (testing "created"
      (let [events [{:event/type :application.event/created
                     :event/time (DateTime. 1000)
                     :event/actor "applicant"
                     :application/id 1
                     :application/external-id "extid"
                     :application/resources [{:catalogue-item/id 10
                                              :resource/ext-id "urn:11"}
                                             {:catalogue-item/id 20
                                              :resource/ext-id "urn:21"}]
                     :application/licenses [{:license/id 30}
                                            {:license/id 31}
                                            {:license/id 32}]
                     :form/id 40
                     :workflow/id 50
                     :workflow/type :workflow/dynamic
                     :workflow.dynamic/handlers #{"handler"}}]
            expected-application {:application/id 1
                                  :application/external-id "extid"
                                  :application/created (DateTime. 1000)
                                  :application/modified (DateTime. 1000)
                                  :application/last-activity (DateTime. 1000)
                                  ;; TODO: unify applicant, members, handlers etc. to use either {:userid "user"} or "user"
                                  :application/applicant "applicant"
                                  :application/applicant-attributes {:eppn "applicant"
                                                                     :mail "applicant@example.com"
                                                                     :commonName "Applicant"}
                                  :application/members #{}
                                  :application/invitation-tokens {}
                                  :application/resources [{:catalogue-item/id 10
                                                           :resource/id 11
                                                           :resource/ext-id "urn:11"
                                                           :catalogue-item/title {:en "en title"
                                                                                  :fi "fi title"
                                                                                  :default "non-localized title"}
                                                           :catalogue-item/start (DateTime. 100)
                                                           :catalogue-item/end nil
                                                           :catalogue-item/enabled true
                                                           :catalogue-item/archived false
                                                           :catalogue-item/state :enabled}
                                                          {:catalogue-item/id 20
                                                           :resource/id 21
                                                           :resource/ext-id "urn:21"
                                                           :catalogue-item/title {:en "en title"
                                                                                  :fi "fi title"
                                                                                  :default "non-localized title"}
                                                           :catalogue-item/start (DateTime. 100)
                                                           :catalogue-item/end nil
                                                           :catalogue-item/enabled true
                                                           :catalogue-item/archived false
                                                           :catalogue-item/state :enabled}]
                                  :application/licenses [{:license/id 30
                                                          :license/accepted false
                                                          :license/type :link
                                                          :license/title {:en "en title"
                                                                          :fi "fi title"
                                                                          :default "non-localized title"}
                                                          :license/link {:en "http://en-license-link"
                                                                         :fi "http://fi-license-link"
                                                                         :default "http://non-localized-license-link"}
                                                          :license/start (DateTime. 100)
                                                          :license/end nil
                                                          :license/enabled true
                                                          :license/archived false}
                                                         {:license/id 31
                                                          :license/accepted false
                                                          :license/type :text
                                                          :license/title {:en "en title"
                                                                          :fi "fi title"
                                                                          :default "non-localized title"}
                                                          :license/text {:en "en license text"
                                                                         :fi "fi license text"
                                                                         :default "non-localized license text"}
                                                          :license/start (DateTime. 100)
                                                          :license/end nil
                                                          :license/enabled true
                                                          :license/archived false}
                                                         {:license/id 32
                                                          :license/accepted false
                                                          :license/type :attachment
                                                          :license/title {:en "en title"
                                                                          :fi "fi title"
                                                                          :default "non-localized title"}
                                                          :license/attachment-id {:en 3201
                                                                                  :fi 3202
                                                                                  :default 3200}
                                                          :license/attachment-filename {:en "en filename"
                                                                                        :fi "fi filename"
                                                                                        :default "non-localized filename"}
                                                          :license/start (DateTime. 100)
                                                          :license/end nil
                                                          :license/enabled true
                                                          :license/archived false}]
                                  :application/accepted-licenses {"applicant" #{}}
                                  :application/events events
                                  :application/description ""
                                  :application/form {:form/id 40
                                                     :form/title "form title"
                                                     :form/fields [{:field/id 41
                                                                    :field/value ""
                                                                    :field/type :description
                                                                    :field/title {:en "en title" :fi "fi title"}
                                                                    :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                                                    :field/optional false
                                                                    :field/options []
                                                                    :field/max-length 100}
                                                                   {:field/id 42
                                                                    :field/value ""
                                                                    :field/type :text
                                                                    :field/title {:en "en title" :fi "fi title"}
                                                                    :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                                                    :field/optional false
                                                                    :field/options []
                                                                    :field/max-length 100}]}
                                  :application/workflow {:workflow/id 50
                                                         :workflow/type :workflow/dynamic
                                                         :workflow.dynamic/state :application.state/draft
                                                         :workflow.dynamic/handlers #{"handler"}
                                                         :workflow.dynamic/awaiting-commenters #{}
                                                         :workflow.dynamic/awaiting-deciders #{}}}]
        (is (= expected-application (apply-events events)))

        (testing "> draft saved"
          (let [events (conj events {:event/type :application.event/draft-saved
                                     :event/time (DateTime. 2000)
                                     :event/actor "applicant"
                                     :application/id 1
                                     :application/field-values {41 "foo"
                                                                42 "bar"}
                                     :application/accepted-licenses #{30 31 32}})
                expected-application (deep-merge expected-application
                                                 {:application/modified (DateTime. 2000)
                                                  :application/last-activity (DateTime. 2000)
                                                  :application/events events
                                                  :application/licenses [{:license/accepted true}
                                                                         {:license/accepted true}
                                                                         {:license/accepted true}]
                                                  :application/description "foo"
                                                  :application/accepted-licenses {"applicant" #{30 31 32}}
                                                  :application/form {:form/fields [{:field/value "foo"}
                                                                                   {:field/value "bar"}]}})]
            (is (= expected-application (apply-events events)))

            (testing "> submitted"
              (let [events (conj events {:event/type :application.event/submitted
                                         :event/time (DateTime. 3000)
                                         :event/actor "applicant"
                                         :application/id 1})
                    expected-application (deep-merge expected-application
                                                     {:application/last-activity (DateTime. 3000)
                                                      :application/events events
                                                      :application/workflow {:workflow.dynamic/state :application.state/submitted}})]
                (is (= expected-application (apply-events events)))

                (testing "> returned"
                  (let [events (conj events {:event/type :application.event/returned
                                             :event/time (DateTime. 4000)
                                             :event/actor "handler"
                                             :application/id 1
                                             :application/comment "fix stuff"})
                        expected-application (deep-merge expected-application
                                                         {:application/last-activity (DateTime. 4000)
                                                          :application/events events
                                                          :application/workflow {:workflow.dynamic/state :application.state/returned}
                                                          :application/form {:form/fields [{:field/previous-value "foo"}
                                                                                           {:field/previous-value "bar"}]}})]
                    (is (= expected-application (apply-events events)))

                    (testing "> draft saved x2"
                      (let [events (conj events
                                         {:event/type :application.event/draft-saved
                                          :event/time (DateTime. 5000)
                                          :event/actor "applicant"
                                          :application/id 1
                                          ;; non-submitted versions should not show up as the previous value
                                          :application/field-values {41 "intermediate draft"
                                                                     42 "intermediate draft"}
                                          :application/accepted-licenses #{30 31 32}}
                                         {:event/type :application.event/draft-saved
                                          :event/time (DateTime. 6000)
                                          :event/actor "applicant"
                                          :application/id 1
                                          :application/field-values {41 "new foo"
                                                                     42 "new bar"}
                                          :application/accepted-licenses #{30 31 32}})
                            expected-application (deep-merge expected-application
                                                             {:application/modified (DateTime. 6000)
                                                              :application/last-activity (DateTime. 6000)
                                                              :application/events events
                                                              :application/description "new foo"
                                                              :application/form {:form/fields [{:field/value "new foo"
                                                                                                :field/previous-value "foo"}
                                                                                               {:field/value "new bar"
                                                                                                :field/previous-value "bar"}]}})]
                        (is (= expected-application (apply-events events)))

                        (testing "> submitted"
                          (let [events (conj events
                                             {:event/type :application.event/submitted
                                              :event/time (DateTime. 7000)
                                              :event/actor "applicant"
                                              :application/id 1})
                                expected-application (deep-merge expected-application
                                                                 {:application/last-activity (DateTime. 7000)
                                                                  :application/events events
                                                                  :application/workflow {:workflow.dynamic/state :application.state/submitted}})]
                            (is (= expected-application (apply-events events)))))))

                    (testing "> submitted (no draft saved)"
                      (let [events (conj events
                                         {:event/type :application.event/submitted
                                          :event/time (DateTime. 7000)
                                          :event/actor "applicant"
                                          :application/id 1})
                            expected-application (deep-merge expected-application
                                                             {:application/last-activity (DateTime. 7000)
                                                              :application/events events
                                                              :application/workflow {:workflow.dynamic/state :application.state/submitted}
                                                              ;; when there was no draft-saved event, the current and
                                                              ;; previous submitted answers must be the same
                                                              :application/form {:form/fields [{:field/value "foo"
                                                                                                :field/previous-value "foo"}
                                                                                               {:field/value "bar"
                                                                                                :field/previous-value "bar"}]}})]
                        (is (= expected-application (apply-events events)))))))

                (testing "> approved"
                  (let [events (conj events
                                     {:event/type :application.event/approved
                                      :event/time (DateTime. 4000)
                                      :event/actor "handler"
                                      :application/id 1
                                      :application/comment "looks good"})
                        expected-application (deep-merge expected-application
                                                         {:application/last-activity (DateTime. 4000)
                                                          :application/events events
                                                          :application/workflow {:workflow.dynamic/state :application.state/approved}})]
                    (is (= expected-application (apply-events events)))

                    (testing "> closed"
                      (let [events (conj events
                                         {:event/type :application.event/closed
                                          :event/time (DateTime. 5000)
                                          :event/actor "handler"
                                          :application/id 1
                                          :application/comment "the project is finished"})
                            expected-application (deep-merge expected-application
                                                             {:application/last-activity (DateTime. 5000)
                                                              :application/events events
                                                              :application/workflow {:workflow.dynamic/state :application.state/closed}})]
                        (is (= expected-application (apply-events events)))))))

                (testing "> rejected"
                  (let [events (conj events
                                     {:event/type :application.event/rejected
                                      :event/time (DateTime. 4000)
                                      :event/actor "handler"
                                      :application/id 1
                                      :application/comment "never gonna happen"})
                        expected-application (deep-merge expected-application
                                                         {:application/last-activity (DateTime. 4000)
                                                          :application/events events
                                                          :application/workflow {:workflow.dynamic/state :application.state/rejected}})]
                    (is (= expected-application (apply-events events)))))

                (testing "> comment requested"
                  (let [request-id (UUID/fromString "4de6c2b0-bb2e-4745-8f92-bd1d1f1e8298")
                        events (conj events
                                     {:event/type :application.event/comment-requested
                                      :event/time (DateTime. 4000)
                                      :event/actor "handler"
                                      :application/id 1
                                      :application/request-id request-id
                                      :application/commenters ["commenter"]
                                      :application/comment "please comment"})
                        expected-application (deep-merge expected-application
                                                         {:application/last-activity (DateTime. 4000)
                                                          :application/events events
                                                          :application/workflow {:workflow.dynamic/awaiting-commenters #{"commenter"}}})]
                    (is (= expected-application (apply-events events)))

                    (testing "> commented"
                      (let [events (conj events
                                         {:event/type :application.event/commented
                                          :event/time (DateTime. 5000)
                                          :event/actor "commenter"
                                          :application/id 1
                                          :application/request-id request-id
                                          :application/comment "looks good"})
                            expected-application (deep-merge expected-application
                                                             {:application/last-activity (DateTime. 5000)
                                                              :application/events events
                                                              :application/workflow {:workflow.dynamic/awaiting-commenters #{}}})]
                        (is (= expected-application (apply-events events)))))))

                (testing "> decision requested"
                  (let [request-id (UUID/fromString "db9c7fd6-53be-4b04-b15d-a3a8e0a45e49")
                        events (conj events
                                     {:event/type :application.event/decision-requested
                                      :event/time (DateTime. 4000)
                                      :event/actor "handler"
                                      :application/id 1
                                      :application/request-id request-id
                                      :application/deciders ["decider"]
                                      :application/comment "please decide"})
                        expected-application (deep-merge expected-application
                                                         {:application/last-activity (DateTime. 4000)
                                                          :application/events events
                                                          :application/workflow {:workflow.dynamic/awaiting-deciders #{"decider"}}})]
                    (is (= expected-application (apply-events events)))

                    (testing "> decided"
                      (let [events (conj events
                                         {:event/type :application.event/decided
                                          :event/time (DateTime. 5000)
                                          :event/actor "decider"
                                          :application/id 1
                                          :application/request-id request-id
                                          :application/decision :approved
                                          :application/comment "I approve this"})
                            expected-application (deep-merge expected-application
                                                             {:application/last-activity (DateTime. 5000)
                                                              :application/events events
                                                              :application/workflow {:workflow.dynamic/awaiting-deciders #{}}})]
                        (is (= expected-application (apply-events events)))))))

                (testing "> member invited"
                  (let [token "b187bda7b9da9053a5d8b815b029e4ba"
                        events (conj events
                                     {:event/type :application.event/member-invited
                                      :event/time (DateTime. 4000)
                                      :event/actor "applicant"
                                      :application/id 1
                                      :application/member {:name "Mr. Member"
                                                           :email "member@example.com"}
                                      :invitation/token token})
                        expected-application (deep-merge expected-application
                                                         {:application/last-activity (DateTime. 4000)
                                                          :application/events events
                                                          :application/invitation-tokens {token {:name "Mr. Member"
                                                                                                 :email "member@example.com"}}})]
                    (is (= expected-application (apply-events events)))

                    (testing "> member uninvited"
                      (let [events (conj events
                                         {:event/type :application.event/member-uninvited
                                          :event/time (DateTime. 5000)
                                          :event/actor "applicant"
                                          :application/id 1
                                          :application/member {:name "Mr. Member"
                                                               :email "member@example.com"}
                                          :application/comment "he left the project"})
                            expected-application (merge expected-application
                                                        {:application/last-activity (DateTime. 5000)
                                                         :application/events events
                                                         :application/invitation-tokens {}})]
                        (is (= expected-application (apply-events events)))))

                    (testing "> member joined"
                      (let [events (conj events
                                         {:event/type :application.event/member-joined
                                          :event/time (DateTime. 5000)
                                          :event/actor "member"
                                          :application/id 1
                                          :invitation/token token})
                            expected-application (merge expected-application
                                                        {:application/last-activity (DateTime. 5000)
                                                         :application/events events
                                                         :application/members #{{:userid "member"}}
                                                         :application/invitation-tokens {}})]
                        (is (= expected-application (apply-events events)))))))

                (testing "> member added"
                  (let [events (conj events
                                     {:event/type :application.event/member-added
                                      :event/time (DateTime. 4000)
                                      :event/actor "handler"
                                      :application/id 1
                                      :application/member {:userid "member"}})
                        expected-application (merge expected-application
                                                    {:application/last-activity (DateTime. 4000)
                                                     :application/events events
                                                     :application/members #{{:userid "member"}}})]
                    (is (= expected-application (apply-events events)))

                    (testing "> member removed"
                      (let [events (conj events
                                         {:event/type :application.event/member-removed
                                          :event/time (DateTime. 5000)
                                          :event/actor "applicant"
                                          :application/id 1
                                          :application/member {:userid "member"}
                                          :application/comment "he left the project"})
                            expected-application (merge expected-application
                                                        {:application/last-activity (DateTime. 5000)
                                                         :application/events events
                                                         :application/members #{}})]
                        (is (= expected-application (apply-events events)))))))))))))))

(deftest test-apply-user-permissions
  (let [application (-> (application-view nil {:event/type :application.event/created
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
      (let [application (application-view application {:event/type :application.event/member-invited
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
