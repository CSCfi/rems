(ns rems.workflow.test-dynamic
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.util :refer [getx]]
            [rems.workflow.dynamic :refer [handle-command] :as dynamic])
  (:import [java.util UUID]
           [org.joda.time DateTime]))

(def ^:private test-time (DateTime. 1000))
(def ^:private command-defaults {:application-id 123
                                 :time test-time})
(def ^:private dummy-created-event {:event/type :application.event/created
                                    :event/time test-time
                                    :event/actor "applicant"
                                    :application/id 123
                                    :application/resources []
                                    :application/licenses []
                                    :form/id 1
                                    :workflow/id 1
                                    :workflow/type :workflow/dynamic
                                    :application/external-id nil
                                    :workflow.dynamic/handlers #{"assistant"}})

(defn apply-events [application events]
  (events/validate-events events)
  (dynamic/apply-events application events))

(defmacro assert-ex
  "Like assert but throw the result with ex-info and not as string. "
  ([x message]
   `(when-not ~x
      (throw (ex-info (str "Assert failed: " ~message "\n" (pr-str '~x))
                      (merge ~message {:expression '~x}))))))

(defn- fail-command
  ([application cmd]
   (fail-command application cmd nil))
  ([application cmd injections]
   (let [cmd (merge command-defaults cmd)
         result (handle-command cmd application injections)]
     (assert-ex (not (:success result)) {:cmd cmd :result result})
     result)))

(defn- ok-command
  ([application cmd]
   (ok-command application cmd nil))
  ([application cmd injections]
   (let [cmd (merge command-defaults cmd)
         result (handle-command cmd application injections)]
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
               :event/actor "applicant"
               :application/id 123
               :application/field-values {1 "foo" 2 "bar"}
               :application/accepted-licenses #{1 2}}]
             (ok-command application
                         {:type :application.command/save-draft
                          :actor "applicant"
                          :field-values {1 "foo" 2 "bar"}
                          :accepted-licenses #{1 2}}))))
    (testing "only the applicant can save a draft"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/save-draft
                            :actor "non-applicant"
                            :field-values {1 "foo" 2 "bar"}
                            :accepted-licenses #{1 2}})
             (fail-command application
                           {:type :application.command/save-draft
                            :actor "assistant"
                            :field-values {1 "foo" 2 "bar"}
                            :accepted-licenses #{1 2}}))))
    (testing "draft cannot be updated after submitting"
      (let [application (apply-events application
                                      [{:event/type :application.event/submitted
                                        :event/time test-time
                                        :event/actor "applicant"
                                        :application/id 123}])]
        (is (= {:errors [{:type :forbidden}]}
               (fail-command application
                             {:type :application.command/save-draft
                              :actor "applicant"
                              :field-values {1 "updated"}
                              :accepted-licenses #{3}})))))
    (testing "draft can be updated after returning it to applicant"
      (let [application (apply-events application
                                      [{:event/type :application.event/submitted
                                        :event/time test-time
                                        :event/actor "applicant"
                                        :application/id 123}
                                       {:event/type :application.event/returned
                                        :event/time test-time
                                        :event/actor "assistant"
                                        :application/id 123
                                        :application/comment ""}])]
        (is (= [{:event/type :application.event/draft-saved
                 :event/time test-time
                 :event/actor "applicant"
                 :application/id 123
                 :application/field-values {1 "updated"}
                 :application/accepted-licenses #{3}}]
               (ok-command application
                           {:type :application.command/save-draft
                            :actor "applicant"
                            :field-values {1 "updated"}
                            :accepted-licenses #{3}})))))))

(deftest test-submit-form-validation
  (let [injections {:validate-form-answers fake-validate-form-answers}
        created-event {:event/type :application.event/created
                       :event/time test-time
                       :event/actor "applicant"
                       :application/id 123
                       :application/resources [{:catalogue-item/id 10
                                                :resource/ext-id "urn:11"}
                                               {:catalogue-item/id 20
                                                :resource/ext-id "urn:21"}]
                       :application/licenses [{:license/id 30}
                                              {:license/id 31}]
                       :form/id 40
                       :workflow/id 50
                       :workflow/type :workflow/dynamic
                       :application/external-id nil
                       :workflow.dynamic/handlers #{"handler"}}
        draft-saved-event {:event/type :application.event/draft-saved
                           :event/time test-time
                           :event/actor "applicant"
                           :application/id 123
                           :application/field-values {41 "foo"
                                                      42 "bar"}
                           :application/accepted-licenses #{30 31}}
        submit-command {:type :application.command/submit
                        :actor "applicant"}
        application (apply-events nil [created-event draft-saved-event])]

    (testing "can submit a valid form"
      (is (= [{:event/type :application.event/submitted
               :event/time test-time
               :event/actor "applicant"
               :application/id 123}]
             (ok-command application submit-command injections))))

    (testing "cannot submit when required fields are empty"
      (is (= {:errors [{:type :t.form.validation/required
                        :field-id 41}]}
             (-> application
                 (apply-events [(assoc-in draft-saved-event [:application/field-values 41] "")])
                 (fail-command submit-command injections)))))

    (testing "cannot submit when not all licenses are accepted"
      (is (= {:errors [{:type :t.form.validation/required
                        :license-id 31}]}
             (-> application
                 (apply-events [(update-in draft-saved-event [:application/accepted-licenses] disj 31)])
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
                                 :event/actor "applicant"
                                 :application/id 123}])
                 (fail-command submit-command injections)))))))

(deftest test-approve-or-reject
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor "applicant"
                                    :application/id 123}])]
    (testing "approved successfully"
      (is (= [{:event/type :application.event/approved
               :event/time test-time
               :event/actor "assistant"
               :application/id 123
               :application/comment "fine"}]
             (ok-command application
                         {:type :application.command/approve
                          :actor "assistant"
                          :comment "fine"}))))
    (testing "rejected successfully"
      (is (= [{:event/type :application.event/rejected
               :application/comment "bad"
               :event/time test-time
               :event/actor "assistant"
               :application/id 123}]
             (ok-command application
                         {:type :application.command/reject
                          :actor "assistant"
                          :comment "bad"}))))))

(deftest test-return
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor "applicant"
                                    :application/id 123}])]
    (is (= [{:event/type :application.event/returned
             :event/time test-time
             :event/actor "assistant"
             :application/id 123
             :application/comment "ret"}]
           (ok-command application
                       {:type :application.command/return
                        :actor "assistant"
                        :comment "ret"})))))

(deftest test-close
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor "applicant"
                                    :application/id 123}
                                   {:event/type :application.event/approved
                                    :event/time test-time
                                    :event/actor "assistant"
                                    :application/id 123
                                    :application/comment ""}])]
    (is (= [{:event/type :application.event/closed
             :event/time test-time
             :event/actor "assistant"
             :application/id 123
             :application/comment "outdated"}]
           (ok-command application
                       {:type :application.command/close
                        :actor "assistant"
                        :comment "outdated"})))))

(deftest test-decision
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor "applicant"
                                    :application/id 123}])
        injections {:valid-user? #{"deity"}}]
    (testing "required :valid-user? injection"
      (is (= {:errors [{:type :missing-injection :injection :valid-user?}]}
             (handle-command {:application-id 123 :time test-time
                              :actor "assistant" :deciders ["deity"] :type :application.command/request-decision
                              :comment "pls"}
                             application
                             {}))))
    (testing "decider must be a valid user"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "deity2"}]}
             (handle-command {:application-id 123 :time test-time
                              :actor "assistant" :deciders ["deity2"] :type :application.command/request-decision
                              :comment "pls"}
                             application
                             injections))))
    (testing "deciding before ::request-decision should fail"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:application-id 123 :time test-time :comment "pls"
                              :actor "deity" :decision :approved :type :application.command/decide}
                             application
                             injections))))
    (let [events (ok-command application
                             {:type :application.command/request-decision
                              :actor "assistant"
                              :deciders ["deity"]
                              :comment ""}
                             injections)
          request-id (:application/request-id (first events))
          requested (apply-events application events)]
      (testing "decision requested successfully"
        (is (instance? UUID request-id))
        (is (= [{:event/type :application.event/decision-requested
                 :event/time test-time
                 :event/actor "assistant"
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
                                    :event/actor "applicant"
                                    :application/id 123}
                                   {:event/type :application.event/member-added
                                    :event/time test-time
                                    :event/actor "applicant"
                                    :application/id 123
                                    :application/member {:userid "somebody"}}])
        injections {:valid-user? #{"member1" "member2" "somebody" "applicant"}}]
    (testing "add two members"
      (is (= [{:event/type :application.event/member-added
               :event/time test-time
               :event/actor "assistant"
               :application/id 123
               :application/member {:userid "member1"}}]
             (ok-command application
                         {:type :application.command/add-member
                          :actor "assistant"
                          :member {:userid "member1"}}
                         injections))))
    (testing "only handler can add members"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/add-member
                            :actor "applicant"
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
                            :actor "assistant"
                            :member {:userid "member3"}}
                           injections))))
    (testing "added members can see the application"
      (is (-> (apply-commands application
                              [{:type :application.command/add-member
                                :actor "assistant"
                                :member {:userid "member1"}}]
                              injections)
              (model/see-application? "member1"))))))

(deftest test-invite-member
  (let [application (apply-events nil [dummy-created-event])
        injections {:valid-user? #{"somebody" "applicant"}
                    :secure-token (constantly "very-secure")}]
    (testing "applicant can invite members"
      (is (= [{:event/type :application.event/member-invited
               :event/time test-time
               :event/actor "applicant"
               :application/id 123
               :application/member {:name "Member Applicant 1"
                                    :email "member1@applicants.com"}
               :invitation/token "very-secure"}]
             (ok-command application
                         {:type :application.command/invite-member
                          :actor "applicant"
                          :member {:name "Member Applicant 1"
                                   :email "member1@applicants.com"}}
                         injections))))
    (testing "handler can invite members"
      (let [application (apply-events application [{:event/type :application.event/submitted
                                                    :event/time test-time
                                                    :event/actor "applicant"
                                                    :application/id 123}])]
        (is (= [{:event/type :application.event/member-invited
                 :event/time test-time
                 :event/actor "assistant"
                 :application/id 123
                 :application/member {:name "Member Applicant 1"
                                      :email "member1@applicants.com"}
                 :invitation/token "very-secure"}]
               (ok-command application
                           {:type :application.command/invite-member
                            :actor "assistant"
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
                                    :event/actor "applicant"
                                    :application/id 123}])]
      (testing "applicant can't invite members to submitted application"
        (is (= {:errors [{:type :forbidden}]}
               (fail-command submitted
                             {:type :application.command/invite-member
                              :actor "applicant"
                              :member {:name "Member Applicant 1"
                                       :email "member1@applicants.com"}}
                             injections))))
      (testing "handler can invite members to submitted application"
        (is (ok-command submitted
                        {:type :application.command/invite-member
                         :actor "assistant"
                         :member {:name "Member Applicant 1"
                                  :email "member1@applicants.com"}}
                        injections))))))

(deftest test-accept-invitation
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/member-invited
                                    :event/time test-time
                                    :event/actor "applicant"
                                    :application/id 123
                                    :application/member {:name "Some Body" :email "somebody@applicants.com"}
                                    :invitation/token "very-secure"}])
        injections {:valid-user? #{"somebody" "somebody2" "applicant"}}]

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
                                        :event/actor "applicant"
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
                                    :event/actor "applicant"
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
                                   :event/actor "applicant"
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
                                    :event/actor "applicant"
                                    :application/id 123}
                                   {:event/type :application.event/member-added
                                    :event/time test-time
                                    :event/actor "assistant"
                                    :application/id 123
                                    :application/member {:userid "somebody"}}])
        injections {:valid-user? #{"somebody" "applicant" "assistant"}}]
    (testing "remove member by applicant"
      (is (= [{:event/type :application.event/member-removed
               :event/time test-time
               :event/actor "applicant"
               :application/id 123
               :application/member {:userid "somebody"}
               :application/comment "some comment"}]
             (ok-command application
                         {:type :application.command/remove-member
                          :actor "applicant"
                          :member {:userid "somebody"}
                          :comment "some comment"}
                         injections))))
    (testing "remove applicant by applicant"
      (is (= {:errors [{:type :cannot-remove-applicant}]}
             (fail-command application
                           {:type :application.command/remove-member
                            :actor "applicant"
                            :member {:userid "applicant"}
                            :comment ""}
                           injections))))
    (testing "remove member by handler"
      (is (= [{:event/type :application.event/member-removed
               :event/time test-time
               :event/actor "assistant"
               :application/id 123
               :application/member {:userid "somebody"}
               :application/comment ""}]
             (ok-command application
                         {:type :application.command/remove-member
                          :actor "assistant"
                          :member {:userid "somebody"}
                          :comment ""}
                         injections))))
    (testing "only members can be removed"
      (is (= {:errors [{:type :user-not-member :user {:userid "notamember"}}]}
             (fail-command application
                           {:type :application.command/remove-member
                            :actor "assistant"
                            :member {:userid "notamember"}
                            :comment ""}
                           injections))))
    (testing "removed members cannot see the application"
      (is (-> application
              (model/see-application? "somebody")))
      (is (not (-> application
                   (apply-commands [{:type :application.command/remove-member
                                     :actor "applicant"
                                     :member {:userid "somebody"}
                                     :comment ""}]
                                   injections)
                   (model/see-application? "somebody")))))))


(deftest test-uninvite-member
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/member-invited
                                    :event/time test-time
                                    :event/actor "applicant"
                                    :application/id 123
                                    :application/member {:name "Some Body" :email "some@body.com"}
                                    :invitation/token "123456"}
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor "applicant"
                                    :application/id 123}])
        injections {}]
    (testing "uninvite member by applicant"
      (is (= []
             (:invited-members
              (apply-commands application
                              [{:type :application.command/uninvite-member :actor "applicant" :member {:name "Some Body" :email "some@body.com"}
                                :comment ""}]
                              injections)))))
    (testing "uninvite member by handler"
      (is (= []
             (:invited-members
              (apply-commands application
                              [{:type :application.command/uninvite-member :actor "assistant" :member {:name "Some Body" :email "some@body.com"}
                                :comment ""}]
                              injections)))))
    (testing "only invited members can be uninvited"
      (is (= {:errors [{:type :user-not-member :user {:name "Not Member" :email "not@member.com"}}]}
             (handle-command {:application-id 123 :time test-time
                              :type :application.command/uninvite-member :actor "assistant" :member {:name "Not Member" :email "not@member.com"}
                              :comment ""}
                             application
                             injections))))))

(deftest test-comment
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor "applicant"
                                    :application/id 123}])
        injections {:valid-user? #{"commenter" "commenter2" "commenter3"}}]
    (testing "required :valid-user? injection"
      (is (= {:errors [{:type :missing-injection :injection :valid-user?}]}
             (handle-command {:application-id 123 :time test-time :comment ""
                              :actor "assistant" :commenters ["commenter"] :type :application.command/request-comment}
                             application
                             {}))))
    (testing "commenters must not be empty"
      (is (= {:errors [{:type :must-not-be-empty :key :commenters}]}
             (handle-command {:application-id 123 :time test-time :comment ""
                              :actor "assistant" :commenters [] :type :application.command/request-comment}
                             application
                             {}))))
    (testing "commenters must be a valid users"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "invaliduser"} {:type :t.form.validation/invalid-user :userid "invaliduser2"}]}
             (handle-command {:application-id 123 :time test-time :comment ""
                              :actor "assistant" :commenters ["invaliduser" "commenter" "invaliduser2"] :type :application.command/request-comment}
                             application
                             injections))))
    (testing "commenting before ::request-comment should fail"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:application-id 123 :time test-time
                              :actor "commenter" :comment "" :type :application.command/comment}
                             application
                             injections))))
    (let [events-1 (ok-command application
                               {:type :application.command/request-comment
                                :actor "assistant"
                                :commenters ["commenter" "commenter2"]
                                :comment ""}
                               injections)
          request-id-1 (:application/request-id (first events-1))
          application (apply-events application events-1)
          ;; Make a new request that should partly override previous
          events-2 (ok-command application
                               {:type :application.command/request-comment
                                :actor "assistant"
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
                 :event/actor "assistant"
                 :application/id 123}]
               events-1))
        (is (instance? UUID request-id-2))
        (is (= [{:event/type :application.event/comment-requested
                 :application/request-id request-id-2
                 :application/commenters ["commenter"]
                 :application/comment ""
                 :event/time test-time
                 :event/actor "assistant"
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
