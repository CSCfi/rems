(ns rems.workflow.test-dynamic
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.util :refer [getx]]
            [rems.workflow.dynamic :refer :all])
  (:import [java.util UUID]
           [org.joda.time DateTime]))

(def ^:private test-time (DateTime. 1000))
(def ^:private command-defaults {:application-id 123
                                 :time test-time})

(defmacro assert-ex
  "Like assert but throw the result with ex-info and not as string. "
  ([x message]
   `(when-not ~x
      (throw (ex-info (str "Assert failed: " ~message "\n" (pr-str '~x))
                      (merge ~message {:expression '~x}))))))


(defmacro try-catch-ex
  "Wraps the code in `try` and `catch` and automatically unwraps the possible exception `ex-data` into regular result."
  [& body]
  `(try
     ~@body
     (catch RuntimeException e#
       (ex-data e#))))

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
  (let [injections {:validate-form-answers (constantly nil)}
        application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :application/id 123
                                    :form/id 1
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}])]
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
                          :accepted-licenses #{1 2}}
                         injections))))
    (testing "only the applicant can save a draft"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/save-draft
                            :actor "non-applicant"
                            :field-values {1 "foo" 2 "bar"}
                            :accepted-licenses #{1 2}}
                           injections)
             (fail-command application
                           {:type :application.command/save-draft
                            :actor "assistant"
                            :field-values {1 "foo" 2 "bar"}
                            :accepted-licenses #{1 2}}
                           injections))))
    (testing "draft cannot be updated after submitting"
      (let [application (apply-events application
                                      [{:event/type :application.event/submitted
                                        :event/time test-time
                                        :actor "applicant"
                                        :application/id 123}])]
        (is (= {:errors [{:type :forbidden}]}
               (fail-command application
                             {:type :application.command/save-draft
                              :actor "applicant"
                              :field-values {1 "updated"}
                              :accepted-licenses #{3}}
                             injections)))))
    (testing "draft can be updated after returning it to applicant"
      (let [application (apply-events application
                                      [{:event/type :application.event/submitted
                                        :event/time test-time
                                        :actor "applicant"
                                        :application/id 123}
                                       {:event/type :application.event/returned
                                        :event/time test-time
                                        :actor "assistant"
                                        :application/id 123}])]
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
                            :accepted-licenses #{3}}
                           injections)))))))

(deftest test-submit
  (let [injections {:validate-form-answers fake-validate-form-answers}
        run-cmd (fn [events command]
                  (let [application (apply-events nil events)]
                    (handle-command command application injections)))

        created-event {:event/type :application.event/created
                       :event/time (DateTime. 1000)
                       :event/actor "applicant"
                       :application/id 1
                       :application/resources [{:catalogue-item/id 10
                                                :resource/ext-id "urn:11"}
                                               {:catalogue-item/id 20
                                                :resource/ext-id "urn:21"}]
                       :application/licenses [{:license/id 30}
                                              {:license/id 31}]
                       :form/id 40
                       :workflow/id 50
                       :workflow/type :workflow/dynamic
                       :workflow.dynamic/handlers #{"handler"}}
        draft-saved-event {:event/type :application.event/draft-saved
                           :event/time (DateTime. 2000)
                           :event/actor "applicant"
                           :application/id 1
                           :application/field-values {41 "foo"
                                                      42 "bar"}
                           :application/accepted-licenses #{30 31}}
        submit-command {:type :application.command/submit
                        :time (DateTime. 3000)
                        :actor "applicant"
                        :application-id 1}]

    (testing "can submit a valid form"
      (is (= {:success true
              :result {:event/type :application.event/submitted
                       :event/time (DateTime. 3000)
                       :event/actor "applicant"
                       :application/id 1}}
             (run-cmd [created-event
                       draft-saved-event]
                      submit-command))))

    (testing "cannot submit when required fields are empty"
      (is (= {:errors [{:type :t.form.validation/required
                        :field-id 41}]}
             (run-cmd [created-event
                       (assoc-in draft-saved-event [:application/field-values 41] "")]
                      submit-command))))

    (testing "cannot submit when not all licenses are accepted"
      (is (= {:errors [{:type :t.form.validation/required
                        :license-id 31}]}
             (run-cmd [created-event
                       (update-in draft-saved-event [:application/accepted-licenses] disj 31)]
                      submit-command))))))

(deftest test-submit-approve-or-reject
  (let [injections {:validate-form-answers fake-validate-form-answers}
        application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :application/id 123
                                    :form/id 1
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/draft-saved
                                    :event/actor "applicant"
                                    :application/id 123
                                    :application/field-values {10 "foo"}
                                    :application/accepted-licenses #{}}])]
    (testing "non-applicant cannot submit"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:application-id 123 :time test-time
                              :actor "not-applicant" :type :application.command/submit}
                             application injections))))
    (testing "cannot submit non-valid forms"
      (let [application (apply-events application [{:event/type :application.event/draft-saved
                                                    :event/actor "applicant"
                                                    :application/id 123
                                                    :application/field-values {10 ""}
                                                    :application/accepted-licenses #{}}])]
        (is (= {:errors [{:type :t.form.validation/required :field-id 10}]}
               (handle-command {:application-id 123 :time test-time
                                :actor "applicant" :type :application.command/submit}
                               application injections)))))
    (let [submitted (apply-command application {:actor "applicant" :type :application.command/submit} injections)]
      (testing "cannot submit twice"
        (is (= {:errors [{:type :forbidden}]}
               (handle-command {:application-id 123 :time test-time
                                :actor "applicant" :type :application.command/submit} submitted injections))))
      (testing "approving"
        (is (= :application.state/approved (:state (apply-command submitted
                                                                  {:actor "assistant" :type :application.command/approve
                                                                   :comment "fine"}
                                                                  injections)))))
      (testing "rejecting"
        (is (= :application.state/rejected (:state (apply-command submitted
                                                                  {:actor "assistant" :type :application.command/reject
                                                                   :comment "bad"}
                                                                  injections))))))))

(deftest test-submit-return-submit-approve-close
  (let [injections {:validate-form-answers (constantly nil)}
        application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :application/id 123
                                    :form/id 1
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}])
        returned-application (apply-commands application
                                             [{:actor "applicant" :type :application.command/submit}
                                              {:actor "assistant" :type :application.command/return :comment "ret"}]
                                             injections)
        approved-application (apply-commands returned-application [{:actor "applicant" :type :application.command/submit}
                                                                   {:actor "assistant" :type :application.command/approve :comment "fine"}]
                                             injections)
        closed-application (apply-command approved-application {:actor "assistant" :type :application.command/close :comment ""}
                                          injections)]
    (is (= :application.state/returned (:state returned-application)))
    (is (= :application.state/approved (:state approved-application)))
    (is (= :application.state/closed (:state closed-application)))))

(deftest test-decision
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :application/id 123
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/submitted
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
      (testing "request decision succesfully"
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
        (testing "succesfully approved"
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
        (testing "successfully rejected"
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
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :application/id 123
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/submitted
                                    :event/actor "applicant"
                                    :application/id 123}
                                   {:event/type :application.event/member-added
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
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :application/id 123
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}])
        injections {:valid-user? #{"somebody" "applicant"}
                    :secure-token (constantly "very-secure")}]
    (testing "invite two members by applicant"
      (is (= [{:name "Member Applicant 1" :email "member1@applicants.com"} {:name "Member Applicant 2" :email "member2@applicants.com"}]
             (:invited-members
              (apply-commands application
                              [{:type :application.command/invite-member :actor "applicant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                               {:type :application.command/invite-member :actor "applicant" :member {:name "Member Applicant 2" :email "member2@applicants.com"}}]
                              injections)))))
    (is (= "very-secure"
           (:invitation/token
            (:result
             (handle-command {:application-id 123 :time test-time
                              :type :application.command/invite-member :actor "applicant"
                              :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                             application
                             injections))))
        "should generate secure token")
    (testing "invite two members by handler"
      (let [application (apply-events application [{:event/type :application.event/submitted
                                                    :event/actor "applicant"
                                                    :application/id 123}])]
        (is (= [{:name "Member Applicant 1" :email "member1@applicants.com"} {:name "Member Applicant 2" :email "member2@applicants.com"}]
               (:invited-members
                (apply-commands application
                                [{:type :application.command/invite-member :actor "assistant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                                 {:type :application.command/invite-member :actor "assistant" :member {:name "Member Applicant 2" :email "member2@applicants.com"}}]
                                injections))))))
    (testing "only applicant or handler can invite members"
      (is (= {:errors [{:type :forbidden}]}
             (handle-command {:application-id 123 :time test-time
                              :type :application.command/invite-member :actor "member1"
                              :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                             application
                             injections))))
    (let [submitted (apply-events application
                                  [{:event/type :application.event/submitted
                                    :event/actor "applicant"
                                    :application/id 123}])]
      (testing "applicant can't invite members to submitted application"
        (is (= {:errors [{:type :forbidden}]}
               (handle-command {:application-id 123 :time test-time
                                :type :application.command/invite-member :actor "applicant"
                                :member {:name "Member Applicant 1" :email "member1@applicants.com"}}
                               submitted
                               injections))))
      (testing "handler can invite members to submitted application"
        (is (= [{:name "Member Applicant 1" :email "member1@applicants.com"}]
               (:invited-members
                (apply-commands submitted
                                [{:type :application.command/invite-member :actor "assistant" :member {:name "Member Applicant 1" :email "member1@applicants.com"}}]
                                injections))))))))

(deftest test-accept-invitation
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :application/id 123
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/member-invited
                                    :event/:actor "applicant"
                                    :application/id 123
                                    :application/member {:name "Some Body" :email "somebody@applicants.com"}
                                    :invitation/token "very-secure"}])
        injections {:valid-user? #{"somebody" "somebody2" "applicant"}}]
    (testing "invitation token is available before use"
      (is (= ["very-secure"]
             (keys (:invitation-tokens application)))))

    (testing "invitation token is not available after use"
      (is (empty?
           (keys (:invitation-tokens
                  (apply-commands application
                                  [{:type :application.command/accept-invitation :actor "somebody" :token "very-secure"}]
                                  injections))))))

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

    (let [application (apply-events application
                                    [{:event/type :application.event/member-added
                                      :event/actor "applicant"
                                      :application/id 123
                                      :application/member {:userid "somebody"}}])]
      (testing "invited member can't join if they are already a member"
        (is (= {:errors [{:type :already-member :application-id (:id application)}]}
               (:result (try-catch-ex
                         (apply-command application
                                        {:type :application.command/accept-invitation :actor "somebody" :token "very-secure"}
                                        injections)))))))

    (testing "invalid token can't be used to join"
      (is (= {:errors [{:type :t.actions.errors/invalid-token :token "wrong-token"}]}
             (:result
              (try-catch-ex
               (apply-commands application
                               [{:type :application.command/accept-invitation :actor "somebody" :token "wrong-token"}]
                               injections))))))

    (testing "token can't be used twice"
      (is (= {:errors [{:type :t.actions.errors/invalid-token :token "very-secure"}]}
             (:result
              (try-catch-ex
               (apply-commands application
                               [{:type :application.command/accept-invitation :actor "somebody" :token "very-secure"}
                                {:type :application.command/accept-invitation :actor "somebody2" :token "very-secure"}]
                               injections))))))

    (let [submitted (apply-events application
                                  [{:event/type :application.event/submitted
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
                                   :event/actor "applicant"
                                   :application/id 123}])]
        (testing "invited member can't join a closed application"
          (is (= {:errors [{:type :forbidden}]}
                 (:result
                  (try-catch-ex
                   (apply-commands closed
                                   [{:type :application.command/accept-invitation :actor "somebody" :token "very-secure"}]
                                   injections))))))))))

(deftest test-remove-member
  (let [application (apply-events nil
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :application/id 123
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/submitted
                                    :event/actor "applicant"
                                    :application/id 123}
                                   {:event/type :application.event/member-added
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
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :application/id 123
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/member-invited
                                    :event/actor "applicant"
                                    :application/id 123
                                    :application/member {:name "Some Body" :email "some@body.com"}
                                    :invitation/token "123456"}
                                   {:event/type :application.event/submitted
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
                                  [{:event/type :application.event/created
                                    :event/actor "applicant"
                                    :application/id 123
                                    :workflow/type :workflow/dynamic
                                    :workflow.dynamic/handlers #{"assistant"}}
                                   {:event/type :application.event/submitted
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
    (let [requested (apply-commands application
                                    [{:actor "assistant" :commenters ["commenter" "commenter2"] :comment "" :type :application.command/request-comment}
                                     ;; Make a new request that should partly override previous
                                     {:actor "assistant" :commenters ["commenter"] :comment "" :type :application.command/request-comment}]
                                    injections)]
      (testing "request comment succesfully"
        (is (= #{"commenter2" "commenter"} (:commenters requested))))
      (testing "only the requested commenter can comment"
        (is (= {:errors [{:type :forbidden}]}
               (handle-command {:application-id 123 :time test-time
                                :actor "commenter3" :comment "..." :type :application.command/comment}
                               requested
                               injections))))
      (testing "comments are linked to different requests"
        (is (not= (get-in requested [:rems.workflow.dynamic/latest-comment-request-by-user "commenter"])
                  (get-in requested [:rems.workflow.dynamic/latest-comment-request-by-user "commenter2"])))
        (is (= (get-in requested [:rems.workflow.dynamic/latest-comment-request-by-user "commenter"])
               (get-in (handle-command {:application-id 123 :time test-time
                                        :actor "commenter" :comment "..." :type :application.command/comment}
                                       requested injections)
                       [:result :application/request-id])))
        (is (= (get-in requested [:rems.workflow.dynamic/latest-comment-request-by-user "commenter2"])
               (get-in (handle-command {:application-id 123 :time test-time
                                        :actor "commenter2" :comment "..." :type :application.command/comment}
                                       requested injections)
                       [:result :application/request-id]))))
      (let [commented (apply-command requested {:actor "commenter" :comment "..." :type :application.command/comment} injections)]
        (testing "succesfully commented"
          (is (= #{"commenter2"} (:commenters commented))))
        (testing "cannot comment twice"
          (is (= {:errors [{:type :forbidden}]}
                 (handle-command {:application-id 123 :time test-time
                                  :actor "commenter" :comment "..." :type :application.command/comment}
                                 commented
                                 injections))))
        (testing "other commenter can still comment"
          (is (= #{} (:commenters (apply-command commented
                                                 {:actor "commenter2" :comment "..." :type :application.command/comment}
                                                 injections)))))))))
