(ns rems.workflow.test-dynamic
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.util :refer [assert-ex getx]]
            [rems.workflow.dynamic :as dynamic])
  (:import [java.util UUID]
           [org.joda.time DateTime]))

(def ^:private test-time (DateTime. 1000))
(def ^:private command-defaults {:application-id 123
                                 :time test-time})
(def ^:private applicant-user-id "applicant")
(def ^:private handler-user-id "assistant")
(def ^:private dummy-created-event {:event/type :application.event/created
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123
                                    :application/external-id nil
                                    :application/resources []
                                    :application/licenses []
                                    :form/id 1
                                    :workflow/id 1
                                    :workflow/type :workflow/dynamic})
(def ^:private dummy-workflows {1 {:workflow {:handlers [handler-user-id]}}})

(defn apply-events [application events]
  (events/validate-events events)
  (-> (dynamic/apply-events application events)
      (model/enrich-workflow-handlers dummy-workflows)))

(defn- fail-command
  ([application cmd]
   (fail-command application cmd nil))
  ([application cmd injections]
   (let [cmd (merge command-defaults cmd)
         result (dynamic/handle-command cmd application injections)]
     (assert-ex (not (:success result)) {:cmd cmd :result result})
     result)))

(defn- ok-command
  ([application cmd]
   (ok-command application cmd nil))
  ([application cmd injections]
   (let [cmd (merge command-defaults cmd)
         result (dynamic/handle-command cmd application injections)]
     (assert-ex (:success result) {:cmd cmd :result result})
     (events/validate-events [(getx result :result)]))))

(defn- apply-command
  ([application cmd]
   (apply-command application cmd nil))
  ([application cmd injections]
   (apply-events application (ok-command application cmd injections))))

(defn- apply-commands
  ([application commands]
   (apply-commands application commands nil))
  ([application commands injections]
   (reduce (fn [app cmd] (apply-command app cmd injections))
           application commands)))

(defn- fake-validate-form-answers [_form-id answers]
  (->> (:items answers)
       (map (fn [[field-id value]]
              (when (str/blank? value)
                {:type :t.form.validation/required
                 :field-id field-id})))
       (remove nil?)))

;;; Tests

(deftest test-save-draft
  (let [application (apply-events nil [dummy-created-event])]
    (testing "saves a draft"
      (is (= [{:event/type :application.event/draft-saved
               :event/time test-time
               :event/actor applicant-user-id
               :application/id 123
               :application/field-values {1 "foo" 2 "bar"}}]
             (ok-command application
                         {:type :application.command/save-draft
                          :actor applicant-user-id
                          :field-values [{:field 1 :value "foo"}
                                         {:field 2 :value "bar"}]}))))
    (testing "only the applicant can save a draft"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/save-draft
                            :actor "non-applicant"
                            :field-values [{:field 1 :value "foo"}
                                           {:field 2 :value "bar"}]})
             (fail-command application
                           {:type :application.command/save-draft
                            :actor handler-user-id
                            :field-values [{:field 1 :value "foo"}
                                           {:field 2 :value "bar"}]}))))
    (testing "draft cannot be updated after submitting"
      (let [application (apply-events application
                                      [{:event/type :application.event/submitted
                                        :event/time test-time
                                        :event/actor applicant-user-id
                                        :application/id 123}])]
        (is (= {:errors [{:type :forbidden}]}
               (fail-command application
                             {:type :application.command/save-draft
                              :actor applicant-user-id
                              :field-values [{:field 1 :value "updated"}]})))))
    (testing "draft can be updated after returning it to applicant"
      (let [application (apply-events application
                                      [{:event/type :application.event/submitted
                                        :event/time test-time
                                        :event/actor applicant-user-id
                                        :application/id 123}
                                       {:event/type :application.event/returned
                                        :event/time test-time
                                        :event/actor handler-user-id
                                        :application/id 123
                                        :application/comment ""}])]
        (is (= [{:event/type :application.event/draft-saved
                 :event/time test-time
                 :event/actor applicant-user-id
                 :application/id 123
                 :application/field-values {1 "updated"}}]
               (ok-command application
                           {:type :application.command/save-draft
                            :actor applicant-user-id
                            :field-values [{:field 1 :value "updated"}]})))))))

(deftest test-accept-licenses
  (let [application (apply-events nil [dummy-created-event])]
    (is (= [{:event/type :application.event/licenses-accepted
             :event/time test-time
             :event/actor applicant-user-id
             :application/id 123
             :application/accepted-licenses #{1 2}}]
           (ok-command application
                       {:type :application.command/accept-licenses
                        :actor applicant-user-id
                        :accepted-licenses #{1 2}})))))

(deftest test-add-licenses
  (let [application (apply-events nil [dummy-created-event
                                       {:event/type :application.event/submitted
                                        :event/time test-time
                                        :event/actor applicant-user-id
                                        :application/id 123}])]
    (is (= [{:event/type :application.event/licenses-added
             :event/time test-time
             :event/actor handler-user-id
             :application/id 123
             :application/comment "comment"
             :application/licenses [{:license/id 1} {:license/id 2}]}]
           (ok-command application
                       {:type :application.command/add-licenses
                        :actor handler-user-id
                        :comment "comment"
                        :licenses [1 2]})))
    (is (= {:errors [{:type :must-not-be-empty :key :licenses}]}
           (fail-command application
                         {:type :application.command/add-licenses
                          :actor handler-user-id
                          :comment "comment"
                          :licenses []})))))

(deftest test-change-resources
  (let [application (apply-events nil [dummy-created-event])
        submitted-application (apply-events nil [dummy-created-event
                                                 {:event/type :application.event/submitted
                                                  :event/time test-time
                                                  :event/actor applicant-user-id
                                                  :application/id 123}])
        injections {:get-catalogue-item {1 {:id 1 :resid "abc" :formid 1 :wfid 1}
                                         2 {:id 2 :resid "efg" :formid 1 :wfid 1}
                                         3 {:id 3 :resid "hij" :formid 1 :wfid 2}
                                         4 {:id 4 :resid "klm" :formid 2 :wfid 1}} }]
    (testing "applicant can change draft resources"
      (is (= [{:event/type :application.event/resources-changed
               :event/time test-time
               :event/actor applicant-user-id
               :application/id 123
               :application/resources [{:catalogue-item/id 1 :resource/ext-id "abc"}
                                       {:catalogue-item/id 2 :resource/ext-id "efg"}]}]
             (ok-command application
                         {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids [1 2]}
                         injections)))
      (is (= {:errors [{:type :forbidden}]}
             (fail-command submitted-application
                           {:type :application.command/change-resources
                            :actor applicant-user-id
                            :catalogue-item-ids [1 2]}
                           injections)))
      (is (= {:errors [{:type :unbundlable-catalogue-items
                        :catalogue-item-ids [1 2 3]}]}
             (fail-command application
                           {:type :application.command/change-resources
                            :actor applicant-user-id
                            :catalogue-item-ids [1 2 3]}
                           injections)))
      (is (= {:errors [{:type :unbundlable-catalogue-items
                        :catalogue-item-ids [1 2 4]}]}
             (fail-command application
                           {:type :application.command/change-resources
                            :actor applicant-user-id
                            :catalogue-item-ids [1 2 4]}
                           injections))))

    (testing "handler can change submitted resources"
      (is (= [{:event/type :application.event/resources-changed
               :event/time test-time
               :event/actor handler-user-id
               :application/id 123
               :application/comment "Changed these for you"
               :application/resources [{:catalogue-item/id 1 :resource/ext-id "abc"}
                                       {:catalogue-item/id 2 :resource/ext-id "efg"}]}]
             (ok-command submitted-application
                         {:type :application.command/change-resources
                          :actor handler-user-id
                          :comment "Changed these for you"
                          :catalogue-item-ids [1 2]}
                         injections)))

      (is (= [{:event/type :application.event/resources-changed
               :event/time test-time
               :event/actor handler-user-id
               :application/id 123
               :application/comment "Changed these for you"
               :application/resources [{:catalogue-item/id 1 :resource/ext-id "abc"}
                                       {:catalogue-item/id 2 :resource/ext-id "efg"}
                                       {:catalogue-item/id 3 :resource/ext-id "hij"}
                                       {:catalogue-item/id 4 :resource/ext-id "klm"}]}]
             (ok-command submitted-application
                         {:type :application.command/change-resources
                          :actor handler-user-id
                          :comment "Changed these for you"
                          :catalogue-item-ids [1 2 3 4]}
                         injections))))

    (is (= {:errors [{:type :invalid-catalogue-item :catalogue-item-id 42}]}
           (fail-command application
                         {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids [42]}
                         injections)))
    (is (= {:errors [{:type :must-not-be-empty :key :catalogue-item-ids}]}
           (fail-command application
                         {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids []})))))

(deftest test-submit
  (let [injections {:validate-form-answers fake-validate-form-answers}
        created-event {:event/type :application.event/created
                       :event/time test-time
                       :event/actor applicant-user-id
                       :application/id 123
                       :application/external-id nil
                       :application/resources [{:catalogue-item/id 10
                                                :resource/ext-id "urn:11"}
                                               {:catalogue-item/id 20
                                                :resource/ext-id "urn:21"}]
                       :application/licenses [{:license/id 30}
                                              {:license/id 31}]
                       :form/id 40
                       :workflow/id 50
                       :workflow/type :workflow/dynamic}
        draft-saved-event {:event/type :application.event/draft-saved
                           :event/time test-time
                           :event/actor applicant-user-id
                           :application/id 123
                           :application/field-values {41 "foo"
                                                      42 "bar"}}
        submit-command {:type :application.command/submit
                        :actor applicant-user-id}
        application (apply-events nil [created-event draft-saved-event])]

    (testing "can submit a valid form"
      (is (= [{:event/type :application.event/submitted
               :event/time test-time
               :event/actor applicant-user-id
               :application/id 123}]
             (ok-command application submit-command injections))))

    (testing "cannot submit when required fields are empty"
      (is (= {:errors [{:type :t.form.validation/required
                        :field-id 41}]}
             (-> application
                 (apply-events [(assoc-in draft-saved-event [:application/field-values 41] "")])
                 (fail-command submit-command injections)))))

    (testing "non-applicant cannot submit"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           (assoc submit-command :actor "non-applicant")
                           injections))))

    (testing "cannot submit twice"
      (is (= {:errors [{:type :forbidden}]}
             (-> application
                 (apply-events [{:event/type :application.event/submitted
                                 :event/time test-time
                                 :event/actor applicant-user-id
                                 :application/id 123}])
                 (fail-command submit-command injections)))))))

(deftest test-approve-or-reject
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123}])]
    (testing "approved successfully"
      (is (= [{:event/type :application.event/approved
               :event/time test-time
               :event/actor handler-user-id
               :application/id 123
               :application/comment "fine"}]
             (ok-command application
                         {:type :application.command/approve
                          :actor handler-user-id
                          :comment "fine"}))))
    (testing "rejected successfully"
      (is (= [{:event/type :application.event/rejected
               :application/comment "bad"
               :event/time test-time
               :event/actor handler-user-id
               :application/id 123}]
             (ok-command application
                         {:type :application.command/reject
                          :actor handler-user-id
                          :comment "bad"}))))))

(deftest test-return
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123}])]
    (is (= [{:event/type :application.event/returned
             :event/time test-time
             :event/actor handler-user-id
             :application/id 123
             :application/comment "ret"}]
           (ok-command application
                       {:type :application.command/return
                        :actor handler-user-id
                        :comment "ret"})))))

(deftest test-close
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123}
                                   {:event/type :application.event/approved
                                    :event/time test-time
                                    :event/actor handler-user-id
                                    :application/id 123
                                    :application/comment ""}])]
    (is (= [{:event/type :application.event/closed
             :event/time test-time
             :event/actor handler-user-id
             :application/id 123
             :application/comment "outdated"}]
           (ok-command application
                       {:type :application.command/close
                        :actor handler-user-id
                        :comment "outdated"})))))

(deftest test-decision
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123}])
        injections {:valid-user? #{"deity"}}]
    (testing "required :valid-user? injection"
      (is (= {:errors [{:type :missing-injection :injection :valid-user?}]}
             (fail-command application
                           {:type :application.command/request-decision
                            :actor handler-user-id
                            :deciders ["deity"]
                            :comment "pls"}
                           {}))))
    (testing "decider must be a valid user"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "deity2"}]}
             (fail-command application
                           {:type :application.command/request-decision
                            :actor handler-user-id
                            :deciders ["deity2"]
                            :comment "pls"}
                           injections))))
    (testing "deciding before ::request-decision should fail"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/decide
                            :actor "deity"
                            :decision :approved
                            :comment "pls"}
                           injections))))
    (let [events (ok-command application
                             {:type :application.command/request-decision
                              :actor handler-user-id
                              :deciders ["deity"]
                              :comment ""}
                             injections)
          request-id (:application/request-id (first events))
          requested (apply-events application events)]
      (testing "decision requested successfully"
        (is (instance? UUID request-id))
        (is (= [{:event/type :application.event/decision-requested
                 :event/time test-time
                 :event/actor handler-user-id
                 :application/id 123
                 :application/request-id request-id
                 :application/deciders ["deity"]
                 :application/comment ""}]
               events)))
      (testing "only the requested user can decide"
        (is (= {:errors [{:type :forbidden}]}
               (fail-command requested
                             {:type :application.command/decide
                              :actor "deity2"
                              :decision :approved
                              :comment ""}
                             injections))))
      (let [events (ok-command requested
                               {:type :application.command/decide
                                :actor "deity"
                                :decision :approved
                                :comment ""}
                               injections)
            approved (apply-events requested events)]
        (testing "decided approved successfully"
          (is (= [{:event/type :application.event/decided
                   :event/time test-time
                   :event/actor "deity"
                   :application/id 123
                   :application/request-id request-id
                   :application/decision :approved
                   :application/comment ""}]
                 events)))
        (testing "cannot approve twice"
          (is (= {:errors [{:type :forbidden}]}
                 (fail-command approved
                               {:type :application.command/decide
                                :actor "deity"
                                :decision :approved
                                :comment ""}
                               injections)))))
      (let [events (ok-command requested
                               {:type :application.command/decide
                                :actor "deity"
                                :decision :rejected
                                :comment ""}
                               injections)
            rejected (apply-events requested events)]
        (testing "decided rejected successfully"
          (is (= [{:event/type :application.event/decided
                   :event/time test-time
                   :event/actor "deity"
                   :application/id 123
                   :application/request-id request-id
                   :application/decision :rejected
                   :application/comment ""}]
                 events)))
        (testing "can not reject twice"
          (is (= {:errors [{:type :forbidden}]}
                 (fail-command rejected
                               {:type :application.command/decide
                                :actor "deity"
                                :decision :rejected
                                :comment ""}
                               injections)))))
      (testing "other decisions are not possible"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Value does not match schema"
                              (fail-command requested
                                            {:type :application.command/decide
                                             :actor "deity"
                                             :decision :foobar
                                             :comment ""}
                                            injections)))))))

(deftest test-add-member
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123}
                                   {:event/type :application.event/member-added
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123
                                    :application/member {:userid "somebody"}}])
        injections {:valid-user? #{"member1" "member2" "somebody" applicant-user-id}}]
    (testing "handler can add members"
      (is (= [{:event/type :application.event/member-added
               :event/time test-time
               :event/actor handler-user-id
               :application/id 123
               :application/member {:userid "member1"}}]
             (ok-command application
                         {:type :application.command/add-member
                          :actor handler-user-id
                          :member {:userid "member1"}}
                         injections))))
    (testing "only handler can add members"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/add-member
                            :actor applicant-user-id
                            :member {:userid "member1"}}
                           injections)
             (fail-command application
                           {:type :application.command/add-member
                            :actor "member1"
                            :member {:userid "member2"}}
                           injections))))
    (testing "only valid users can be added"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "member3"}]}
             (fail-command application
                           {:type :application.command/add-member
                            :actor handler-user-id
                            :member {:userid "member3"}}
                           injections))))
    (testing "added members can see the application"
      (is (-> (apply-commands application
                              [{:type :application.command/add-member
                                :actor handler-user-id
                                :member {:userid "member1"}}]
                              injections)
              (model/see-application? "member1"))))))

(deftest test-invite-member
  (let [application (apply-events nil [dummy-created-event])
        injections {:valid-user? #{"somebody" applicant-user-id}
                    :secure-token (constantly "very-secure")}]
    (testing "applicant can invite members"
      (is (= [{:event/type :application.event/member-invited
               :event/time test-time
               :event/actor applicant-user-id
               :application/id 123
               :application/member {:name "Member Applicant 1"
                                    :email "member1@applicants.com"}
               :invitation/token "very-secure"}]
             (ok-command application
                         {:type :application.command/invite-member
                          :actor applicant-user-id
                          :member {:name "Member Applicant 1"
                                   :email "member1@applicants.com"}}
                         injections))))
    (testing "handler can invite members"
      (let [application (apply-events application [{:event/type :application.event/submitted
                                                    :event/time test-time
                                                    :event/actor applicant-user-id
                                                    :application/id 123}])]
        (is (= [{:event/type :application.event/member-invited
                 :event/time test-time
                 :event/actor handler-user-id
                 :application/id 123
                 :application/member {:name "Member Applicant 1"
                                      :email "member1@applicants.com"}
                 :invitation/token "very-secure"}]
               (ok-command application
                           {:type :application.command/invite-member
                            :actor handler-user-id
                            :member {:name "Member Applicant 1"
                                     :email "member1@applicants.com"}}
                           injections)))))
    (testing "other users cannot invite members"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/invite-member
                            :actor "member1"
                            :member {:name "Member Applicant 1"
                                     :email "member1@applicants.com"}}
                           injections))))
    (let [submitted (apply-events application
                                  [{:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123}])]
      (testing "applicant can't invite members to submitted application"
        (is (= {:errors [{:type :forbidden}]}
               (fail-command submitted
                             {:type :application.command/invite-member
                              :actor applicant-user-id
                              :member {:name "Member Applicant 1"
                                       :email "member1@applicants.com"}}
                             injections))))
      (testing "handler can invite members to submitted application"
        (is (ok-command submitted
                        {:type :application.command/invite-member
                         :actor handler-user-id
                         :member {:name "Member Applicant 1"
                                  :email "member1@applicants.com"}}
                        injections))))))

(deftest test-accept-invitation
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/member-invited
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123
                                    :application/member {:name "Some Body" :email "somebody@applicants.com"}
                                    :invitation/token "very-secure"}])
        injections {:valid-user? #{"somebody" "somebody2" applicant-user-id}}]

    (testing "invited member can join draft"
      (is (= [{:event/type :application.event/member-joined
               :event/time test-time
               :event/actor "somebody"
               :application/id 123
               :invitation/token "very-secure"}]
             (ok-command application
                         {:type :application.command/accept-invitation
                          :actor "somebody"
                          :token "very-secure"}
                         injections))))

    (testing "invited member can't join if they are already a member"
      (let [application (apply-events application
                                      [{:event/type :application.event/member-added
                                        :event/time test-time
                                        :event/actor applicant-user-id
                                        :application/id 123
                                        :application/member {:userid "somebody"}}])]
        (is (= {:errors [{:type :already-member :application-id (:id application)}]}
               (fail-command application
                             {:type :application.command/accept-invitation
                              :actor "somebody"
                              :token "very-secure"}
                             injections)))))

    (testing "invalid token can't be used to join"
      (is (= {:errors [{:type :t.actions.errors/invalid-token :token "wrong-token"}]}
             (fail-command application
                           {:type :application.command/accept-invitation
                            :actor "somebody"
                            :token "wrong-token"}
                           injections))))

    (testing "token can't be used twice"
      (let [application (apply-events application
                                      [{:event/type :application.event/member-joined
                                        :event/time test-time
                                        :event/actor "somebody"
                                        :application/id 123
                                        :invitation/token "very-secure"}])]
        (is (= {:errors [{:type :t.actions.errors/invalid-token :token "very-secure"}]}
               (fail-command application
                             {:type :application.command/accept-invitation
                              :actor "somebody2"
                              :token "very-secure"}
                             injections)))))

    (let [submitted (apply-events application
                                  [{:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123}])]
      (testing "invited member can join submitted application"
        (is (= [{:event/type :application.event/member-joined
                 :event/actor "somebody"
                 :event/time test-time
                 :application/id 123
                 :invitation/token "very-secure"}]
               (ok-command application
                           {:type :application.command/accept-invitation
                            :actor "somebody"
                            :token "very-secure"}
                           injections))))

      (let [closed (apply-events submitted
                                 [{:event/type :application.event/closed
                                   :event/time test-time
                                   :event/actor applicant-user-id
                                   :application/id 123
                                   :application/comment ""}])]
        (testing "invited member can't join a closed application"
          (is (= {:errors [{:type :forbidden}]}
                 (fail-command closed
                               {:type :application.command/accept-invitation
                                :actor "somebody"
                                :token "very-secure"}
                               injections))))))))

(deftest test-remove-member
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123}
                                   {:event/type :application.event/member-added
                                    :event/time test-time
                                    :event/actor handler-user-id
                                    :application/id 123
                                    :application/member {:userid "somebody"}}])
        injections {:valid-user? #{"somebody" applicant-user-id handler-user-id}}]
    (testing "applicant can remove members"
      (is (= [{:event/type :application.event/member-removed
               :event/time test-time
               :event/actor applicant-user-id
               :application/id 123
               :application/member {:userid "somebody"}
               :application/comment "some comment"}]
             (ok-command application
                         {:type :application.command/remove-member
                          :actor applicant-user-id
                          :member {:userid "somebody"}
                          :comment "some comment"}
                         injections))))
    (testing "handler can remove members"
      (is (= [{:event/type :application.event/member-removed
               :event/time test-time
               :event/actor handler-user-id
               :application/id 123
               :application/member {:userid "somebody"}
               :application/comment ""}]
             (ok-command application
                         {:type :application.command/remove-member
                          :actor handler-user-id
                          :member {:userid "somebody"}
                          :comment ""}
                         injections))))
    (testing "applicant cannot be removed"
      (is (= {:errors [{:type :cannot-remove-applicant}]}
             (fail-command application
                           {:type :application.command/remove-member
                            :actor applicant-user-id
                            :member {:userid applicant-user-id}
                            :comment ""}
                           injections)
             (fail-command application
                           {:type :application.command/remove-member
                            :actor handler-user-id
                            :member {:userid applicant-user-id}
                            :comment ""}
                           injections))))
    (testing "non-members cannot be removed"
      (is (= {:errors [{:type :user-not-member :user {:userid "notamember"}}]}
             (fail-command application
                           {:type :application.command/remove-member
                            :actor handler-user-id
                            :member {:userid "notamember"}
                            :comment ""}
                           injections))))
    (testing "removed members cannot see the application"
      (is (-> application
              (model/see-application? "somebody")))
      (is (not (-> application
                   (apply-commands [{:type :application.command/remove-member
                                     :actor applicant-user-id
                                     :member {:userid "somebody"}
                                     :comment ""}]
                                   injections)
                   (model/see-application? "somebody")))))))


(deftest test-uninvite-member
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/member-invited
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123
                                    :application/member {:name "Some Body" :email "some@body.com"}
                                    :invitation/token "123456"}
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123}])
        injections {}]
    (testing "uninvite member by applicant"
      (is (= [{:event/type :application.event/member-uninvited
               :event/time test-time
               :event/actor applicant-user-id
               :application/id 123
               :application/member {:name "Some Body" :email "some@body.com"}
               :application/comment ""}]
             (ok-command application
                         {:type :application.command/uninvite-member
                          :actor applicant-user-id
                          :member {:name "Some Body" :email "some@body.com"}
                          :comment ""}
                         injections))))
    (testing "uninvite member by handler"
      (is (= [{:event/type :application.event/member-uninvited
               :event/time test-time
               :event/actor handler-user-id
               :application/id 123
               :application/member {:name "Some Body" :email "some@body.com"}
               :application/comment ""}]
             (ok-command application
                         {:type :application.command/uninvite-member
                          :actor handler-user-id
                          :member {:name "Some Body" :email "some@body.com"}
                          :comment ""}
                         injections))))
    (testing "only invited members can be uninvited"
      (is (= {:errors [{:type :user-not-member :user {:name "Not Member" :email "not@member.com"}}]}
             (fail-command application
                           {:type :application.command/uninvite-member
                            :actor handler-user-id
                            :member {:name "Not Member" :email "not@member.com"}
                            :comment ""}
                           injections))))))

(deftest test-comment
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id 123}])
        injections {:valid-user? #{"commenter" "commenter2" "commenter3"}}]
    (testing "required :valid-user? injection"
      (is (= {:errors [{:type :missing-injection :injection :valid-user?}]}
             (fail-command application
                           {:type :application.command/request-comment
                            :actor handler-user-id
                            :commenters ["commenter"]
                            :comment ""}
                           {}))))
    (testing "commenters must not be empty"
      (is (= {:errors [{:type :must-not-be-empty :key :commenters}]}
             (fail-command application
                           {:type :application.command/request-comment
                            :actor handler-user-id
                            :commenters []
                            :comment ""}
                           {}))))
    (testing "commenters must be a valid users"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "invaliduser"}
                       {:type :t.form.validation/invalid-user :userid "invaliduser2"}]}
             (fail-command application
                           {:type :application.command/request-comment
                            :actor handler-user-id
                            :commenters ["invaliduser" "commenter" "invaliduser2"]
                            :comment ""}
                           injections))))
    (testing "commenting before ::request-comment should fail"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/comment
                            :actor "commenter"
                            :comment ""}
                           injections))))
    (let [events-1 (ok-command application
                               {:type :application.command/request-comment
                                :actor handler-user-id
                                :commenters ["commenter" "commenter2"]
                                :comment ""}
                               injections)
          request-id-1 (:application/request-id (first events-1))
          application (apply-events application events-1)
          ;; Make a new request that should partly override previous
          events-2 (ok-command application
                               {:type :application.command/request-comment
                                :actor handler-user-id
                                :commenters ["commenter"]
                                :comment ""}
                               injections)
          request-id-2 (:application/request-id (first events-2))
          application (apply-events application events-2)]
      (testing "comment requested successfully"
        (is (instance? UUID request-id-1))
        (is (= [{:event/type :application.event/comment-requested
                 :application/request-id request-id-1
                 :application/commenters ["commenter" "commenter2"]
                 :application/comment ""
                 :event/time test-time
                 :event/actor handler-user-id
                 :application/id 123}]
               events-1))
        (is (instance? UUID request-id-2))
        (is (= [{:event/type :application.event/comment-requested
                 :application/request-id request-id-2
                 :application/commenters ["commenter"]
                 :application/comment ""
                 :event/time test-time
                 :event/actor handler-user-id
                 :application/id 123}]
               events-2)))
      (testing "only the requested commenter can comment"
        (is (= {:errors [{:type :forbidden}]}
               (fail-command application
                             {:type :application.command/comment
                              :actor "commenter3"
                              :comment "..."}
                             injections))))
      (testing "comments are linked to different requests"
        (is (= [request-id-2]
               (map :application/request-id
                    (ok-command application
                                {:type :application.command/comment
                                 :actor "commenter"
                                 :comment "..."}
                                injections))))
        (is (= [request-id-1]
               (map :application/request-id
                    (ok-command application
                                {:type :application.command/comment
                                 :actor "commenter2"
                                 :comment "..."}
                                injections)))))
      (let [events (ok-command application
                               {:type :application.command/comment
                                :actor "commenter"
                                :comment "..."}
                               injections)
            application (apply-events application events)]
        (testing "commented successfully"
          (is (= [{:event/type :application.event/commented
                   :event/time test-time
                   :event/actor "commenter"
                   :application/id 123
                   :application/request-id request-id-2
                   :application/comment "..."}]
                 events)))
        (testing "cannot comment twice"
          (is (= {:errors [{:type :forbidden}]}
                 (fail-command application
                               {:type :application.command/comment
                                :actor "commenter"
                                :comment "..."}
                               injections))))
        (testing "other commenter can still comment"
          (is (= [{:event/type :application.event/commented
                   :event/time test-time
                   :event/actor "commenter2"
                   :application/id 123
                   :application/request-id request-id-1
                   :application/comment "..."}]
                 (ok-command application
                             {:type :application.command/comment
                              :actor "commenter2"
                              :comment "..."}
                             injections))))))))
