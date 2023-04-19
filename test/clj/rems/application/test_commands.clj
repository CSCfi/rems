(ns rems.application.test-commands
  (:require [clojure.test :refer :all]
            [rems.application.commands :as commands]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.permissions :as permissions]
            [rems.util :refer [assert-ex getx]]
            [rems.testing-util :refer [with-fixed-time]]
            [clj-time.core :as time])
  (:import [clojure.lang ExceptionInfo]
           [java.util UUID]
           [org.joda.time DateTime]))

(def test-time (DateTime. 1000))
(def app-id 123)
(def new-app-id 456)
(def new-external-id "2019/66")
(def applicant-user-id "applicant")
(def handler-user-id "assistant")
(def decider-user-id "decider")
(def reviewer-user-id "reviewer")
(def review-request-id (UUID/randomUUID))
(def decision-request-id (UUID/randomUUID))

(def dummy-licenses
  {1 {:id 1
      :licensetype "link"
      :localizations {:en {:title "en title"
                           :textcontent "en link"
                           :attachment-id 1}
                      :fi {:title "fi title"
                           :textcontent "fi link"
                           :attachment-id 2}}}
   2 {:id 2
      :licensetype "link"
      :localizations {:en {:title "en title"
                           :textcontent "en link"}
                      :fi {:title "fi title"
                           :textcontent "fi link"}}}
   3 {:id 3
      :licensetype "link"
      :localizations {:en {:title "en title"
                           :textcontent "en link"}
                      :fi {:title "fi title"
                           :textcontent "fi link"}}}})

(defn dummy-get-workflow [id]
  (get {1 {:workflow {:type :workflow/default
                      :handlers [{:userid handler-user-id
                                  :name "user"
                                  :email "user@example.com"}]}}
        2 {:workflow {:type :workflow/default
                      :handlers [{:userid handler-user-id
                                  :name "user"
                                  :email "user@example.com"}]
                      :forms [{:form/id 1} {:form/id 3} {:form/id 4}]}}
        3 {:workflow {:type :workflow/decider
                      :handlers [{:userid handler-user-id
                                  :name "user"
                                  :email "user@example.com"}]}}}
       id))

(def dummy-forms
  {1 {:form/id 1
      :form/fields [{:field/id "1"
                     :field/optional true
                     :field/type :option
                     :field/options [{:key "foo" :label "Foo"}
                                     {:key "bar" :label "Bar"}]}
                    {:field/id "2"
                     :field/optional false
                     :field/visibility {:visibility/type :only-if
                                        :visibility/field {:field/id "1"}
                                        :visibility/values ["foo"]}}]}
   2 {:form/id 2
      :form/fields [{:field/id "1"
                     :field/type :text
                     :field/optional false}]}
   3 {:form/id 3
      :form/fields [{:field/id "text"
                     :field/type :text}
                    {:field/id "attachment"
                     :field/type :attachment}]}
   4 {:form/id 4
      :form/fields [{:field/id "text"
                     :field/type :text}]}
   7 {:form/id 7
      :form/fields [{:field/id "7"
                     :field/type :option
                     :field/optional true
                     :field/options [{:key "y" :label "y"}
                                     {:key "n" :label "n"}]}
                    {:field/id "8"
                     :field/type :email
                     :field/optional false
                     :field/visibility {:visibility/type :only-if
                                        :visibility/field {:field/id "7"}
                                        :visibility/values ["y"]}}]}})

(defn dummy-get-catalogue-item [id]
  (when (< id 10000)
    (some->> id
             (getx {1 {:resid "res1"}
                    2 {:resid "res2"}
                    3 {:resid "res3" :formid 2}
                    4 {:resid "res4" :wfid 2}
                    5 {:resid "res5" :formid 2 :wfid 2}
                    6 {:resid "res5" :formid nil}
                    7 {:resid "res-disabled" :enabled false}
                    42 nil})
             (merge {:enabled true :archived false :expired false
                     :id id :wfid 1 :formid 1}))))

(defn dummy-get-catalogue-item-licenses [id]
  (getx {1 [{:license/id 1}]
         2 [{:license/id 2}]
         3 [{:license/id 1}
            {:license/id 2}
            {:license/id 3}]
         4 []
         5 []
         6 []} id))

(def application-injections
  {:get-attachments-for-application {app-id [{:attachment/id 1
                                              :attachment/user handler-user-id}
                                             {:attachment/id 3
                                              :attachment/user reviewer-user-id}
                                             {:attachment/id 5
                                              :attachment/user decider-user-id}]}
   :get-form-template (fn [id] (getx dummy-forms id))
   :get-catalogue-item dummy-get-catalogue-item
   :get-config (constantly {})
   :get-license dummy-licenses
   :get-user (fn [userid] {:userid userid})
   :get-users-with-role (constantly nil)
   :get-workflow dummy-get-workflow
   :blacklisted? (constantly false)
   :get-attachment-metadata {1 {:application/id app-id
                                :attachment/id 1
                                :attachment/user handler-user-id}
                             2 {:application/id (inc app-id)
                                :attachment/id 2
                                :attachment/user handler-user-id}
                             3 {:application/id app-id
                                :attachment/id 3
                                :attachment/user reviewer-user-id}
                             4 {:application/id app-id
                                :attachment/id 4
                                :attachment/user handler-user-id}
                             5 {:application/id app-id
                                :attachment/id 5
                                :attachment/user decider-user-id}}
   :get-catalogue-item-licenses dummy-get-catalogue-item-licenses})

(def allocated-new-ids? (atom false))

(def command-injections
  (merge application-injections
         {:secure-token (constantly "very-secure")
          :allocate-application-ids! (fn [_time]
                                       (reset! allocated-new-ids? true)
                                       {:application/id new-app-id
                                        :application/external-id new-external-id})
          :copy-attachment! (fn [_new-app-id attachment-id]
                              (+ attachment-id 100))
          :valid-user? #{applicant-user-id handler-user-id decider-user-id reviewer-user-id "somebody" "somebody2" "member1" "member2" "reviewer2" "reviewer3"}
          :find-userid identity}))

(defn patch-event-ids [events]
  (->> events
       (map-indexed (fn [idx event] (assoc event :event/id idx)))))

(defn build-application-view [events & [injections]]
  (-> events
      (patch-event-ids) ; event ids are normally generated by db, but not available here
      (events/validate-events)
      (model/build-application-view (or injections application-injections))))

(defn set-command-defaults [cmd]
  (cond-> cmd
    true
    (assoc :time test-time)

    (not= :application.command/create (:type cmd))
    (assoc :application-id app-id)))

(defn fail-command [cmd & [application injections]]
  (let [cmd (set-command-defaults cmd)
        result (->> (or injections command-injections)
                    (commands/handle-command cmd application))]
    (assert-ex (:errors result) {:cmd cmd :result result})
    result))

(defn ok-command [cmd & [application injections]]
  (let [cmd (set-command-defaults cmd)
        result (->> (or injections command-injections)
                    (commands/handle-command cmd application))]
    (assert-ex (not (:errors result)) {:cmd cmd :result result})
    (let [events (:events result)]
      (events/validate-events events)
      ;; most tests expect only one event, so this avoids having to wrap the expectation to a list
      (if (= 1 (count events))
        (first events)
        events))))

(defn ok-command-with-warnings [cmd & [application injections]]
  (let [cmd (set-command-defaults cmd)
        result (->> (or injections command-injections)
                    (commands/handle-command cmd application))]
    (assert-ex (not (:errors result)) {:cmd cmd :result result})
    (let [events (:events result)]
      (events/validate-events events)
      {:warnings (:warnings result)
       :events events})))

;;; dummy events

(def dummy-created-event {:event/type :application.event/created
                          :event/time test-time
                          :event/actor applicant-user-id
                          :application/id app-id
                          :application/external-id "2000/123"
                          :application/resources [{:catalogue-item/id 1
                                                   :resource/ext-id "res1"}]
                          :application/licenses []
                          :application/forms [{:form/id 1}]
                          :workflow/id 1
                          :workflow/type :workflow/default})
(def dummy-submitted-event {:event/type :application.event/submitted
                            :event/time test-time
                            :event/actor applicant-user-id
                            :application/id app-id})
(def dummy-closed-event {:event/type :application.event/closed
                         :event/time test-time
                         :event/actor applicant-user-id
                         :application/id app-id
                         :application/comment ""})
(def dummy-returned-event {:event/type :application.event/returned
                           :event/time test-time
                           :event/actor handler-user-id
                           :application/id app-id
                           :application/comment ""})
(def dummy-approved-event {:event/type :application.event/approved
                           :event/time test-time
                           :event/actor handler-user-id
                           :application/comment "This is good"
                           :application/id app-id})
(def dummy-draft-saved-event {:event/type :application.event/draft-saved
                              :event/time test-time
                              :event/actor applicant-user-id
                              :application/id app-id
                              :application/field-values []})
(def dummy-licenses-accepted-event {:event/type :application.event/licenses-accepted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id
                                    :application/accepted-licenses #{1}})
(def dummy-member-added-event {:event/type :application.event/member-added
                               :event/time test-time
                               :event/actor applicant-user-id
                               :application/id app-id
                               :application/member {:userid "somebody"}})
(def dummy-member-invited-event {:event/type :application.event/member-invited
                                 :event/time test-time
                                 :event/actor applicant-user-id
                                 :application/id app-id
                                 :application/member {:name "Some Body" :email "somebody@applicants.com"}
                                 :invitation/token "very-secure"})
(def dummy-member-joined-event {:event/type :application.event/member-joined
                                :event/time test-time
                                :event/actor "somebody"
                                :application/id app-id
                                :invitation/token "very-secure"})
(def dummy-review-requested-event {:event/type :application.event/review-requested
                                   :event/time test-time
                                   :event/actor handler-user-id
                                   :application/id app-id
                                   :application/request-id review-request-id
                                   :application/reviewers [reviewer-user-id]
                                   :application/comment ""})
(def dummy-reviewed-event {:event/type :application.event/reviewed
                           :event/time test-time
                           :event/actor reviewer-user-id
                           :application/id app-id
                           :application/request-id review-request-id
                           :application/comment ""})
(def dummy-decision-requested-event {:event/type :application.event/decision-requested
                                     :event/time test-time
                                     :event/actor handler-user-id
                                     :application/id app-id
                                     :application/request-id decision-request-id
                                     :application/deciders [decider-user-id]
                                     :application/comment ""})
(def dummy-decided-event {:event/type :application.event/decided
                          :event/time test-time
                          :event/actor decider-user-id
                          :application/id app-id
                          :application/request-id decision-request-id
                          :application/decision :approved
                          :application/comment ""})
(def dummy-remarked-event {:event/type :application.event/remarked
                           :event/time test-time
                           :event/actor handler-user-id
                           :application/id app-id
                           :application/comment "handler's remark"
                           :application/public false
                           :event/attachments [{:attachment/id 1}]})

;;; Tests

(deftest test-create
  (testing "one resource"
    (is (= {:event/type :application.event/created
            :event/actor applicant-user-id
            :event/time (DateTime. 1000)
            :application/id new-app-id
            :application/external-id new-external-id
            :application/resources [{:catalogue-item/id 1
                                     :resource/ext-id "res1"}]
            :application/licenses [{:license/id 1}]
            :application/forms [{:form/id 1}]
            :workflow/id 1
            :workflow/type :workflow/default}
           (ok-command {:type :application.command/create
                        :actor applicant-user-id
                        :catalogue-item-ids [1]}))))
  (testing "multiple resources"
    (is (= {:event/type :application.event/created
            :event/actor applicant-user-id
            :event/time (DateTime. 1000)
            :application/id new-app-id
            :application/external-id new-external-id
            :application/resources [{:catalogue-item/id 1
                                     :resource/ext-id "res1"}
                                    {:catalogue-item/id 2
                                     :resource/ext-id "res2"}]
            :application/licenses [{:license/id 1}
                                   {:license/id 2}]
            :application/forms [{:form/id 1}]
            :workflow/id 1
            :workflow/type :workflow/default}
           (ok-command {:type :application.command/create
                        :actor applicant-user-id
                        :catalogue-item-ids [1 2]}))))

  (testing "no forms"
    (is (= {:event/type :application.event/created
            :event/actor applicant-user-id
            :event/time (DateTime. 1000)
            :application/id new-app-id
            :application/external-id new-external-id
            :application/resources [{:catalogue-item/id 6
                                     :resource/ext-id "res5"}]
            :application/licenses []
            :application/forms []
            :workflow/id 1
            :workflow/type :workflow/default}
           (ok-command {:type :application.command/create
                        :actor applicant-user-id
                        :catalogue-item-ids [6]}))))

  (testing "error: invalid actor"
    (is (= {:errors [{:userid "does-not-exist", :type :t.form.validation/invalid-user}]}
           (fail-command {:type :application.command/create
                          :actor "does-not-exist"
                          :catalogue-item-ids [1]}))))

  (testing "error: zero catalogue items"
    (is (= {:errors [{:type :must-not-be-empty
                      :key :catalogue-item-ids}]}
           (fail-command {:type :application.command/create
                          :actor applicant-user-id
                          :catalogue-item-ids []}))))

  (testing "error: non-existing catalogue items"
    (is (= {:errors [{:type :invalid-catalogue-item
                      :catalogue-item-id 999999}]}
           (fail-command {:type :application.command/create
                          :actor applicant-user-id
                          :catalogue-item-ids [999999]}))))

  (testing "error: disabled catalogue item"
    (is (= {:errors [{:type :disabled-catalogue-item
                      :catalogue-item-id 7}]}
           (fail-command {:type :application.command/create
                          :actor applicant-user-id
                          :catalogue-item-ids [1 7]}))))

  (testing "catalogue items with different forms"
    (is (= {:event/type :application.event/created
            :event/actor applicant-user-id
            :event/time (DateTime. 1000)
            :application/id new-app-id
            :application/external-id new-external-id
            :application/resources [{:catalogue-item/id 1
                                     :resource/ext-id "res1"}
                                    {:catalogue-item/id 3
                                     :resource/ext-id "res3"}]
            :application/licenses [{:license/id 1}
                                   {:license/id 2}
                                   {:license/id 3}]
            :application/forms [{:form/id 1} {:form/id 2}]
            :workflow/id 1
            :workflow/type :workflow/default}
           (ok-command {:type :application.command/create
                        :actor applicant-user-id
                        :catalogue-item-ids [1 3]}))))

  (testing "workflow form, multiple catalogue items with different forms, workflow has duplicated catalogue item form"
    (is (= {:event/type :application.event/created
            :event/actor applicant-user-id
            :event/time (DateTime. 1000)
            :application/id new-app-id
            :application/external-id new-external-id
            :application/resources [{:catalogue-item/id 4 :resource/ext-id "res4"}
                                    {:catalogue-item/id 5 :resource/ext-id "res5"}]
            :application/licenses []
            ;; NB: form/id 1 is also a workflow form
            :application/forms [{:form/id 1} {:form/id 3} {:form/id 4} {:form/id 2}] ; wf forms first, then catalogue item forms
            :workflow/id 2
            :workflow/type :workflow/default}
           (ok-command {:type :application.command/create
                        :actor applicant-user-id
                        :catalogue-item-ids [4 5]}))))

  (testing "error: catalogue items with different workflows"
    (is (= {:errors [{:type :unbundlable-catalogue-items
                      :catalogue-item-ids [1 4]}]}
           (fail-command {:type :application.command/create
                          :actor applicant-user-id
                          :catalogue-item-ids [1 4]}))))

  (testing "cannot execute the create command for an existing application"
    (reset! allocated-new-ids? false)
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/create
                          :actor applicant-user-id
                          :catalogue-item-ids [1]}
                         (build-application-view [dummy-created-event]))))
    (is (false? @allocated-new-ids?) "should not allocate new IDs")))

(deftest test-save-draft
  (testing "saves a draft"
    (is (= {:event/type :application.event/draft-saved
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/field-values [{:form 1 :field "1" :value "foo"}
                                       {:form 1 :field "2" :value "bar"}]}
           (ok-command {:type :application.command/save-draft
                        :actor applicant-user-id
                        :field-values [{:form 1 :field "1" :value "foo"}
                                       {:form 1 :field "2" :value "bar"}]}
                       (build-application-view [dummy-created-event])))))
  (testing "saves a draft"
    (is (= {:event/type :application.event/draft-saved
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/field-values [{:form 1 :field "1" :value "foo"}
                                       {:form 1 :field "2" :value "bar"}]}
           (ok-command {:type :application.command/save-draft
                        :actor applicant-user-id
                        :field-values [{:form 1 :field "1" :value "foo"}
                                       {:form 1 :field "2" :value "bar"}]}
                       (build-application-view [dummy-created-event])))))

  (testing "saves a draft even when validations fail"
    (is (= {:warnings [{:field-id "1" :form-id 1 :type :t.form.validation/invalid-value}]
            :events [{:event/type :application.event/draft-saved
                      :event/time test-time
                      :event/actor applicant-user-id
                      :application/id app-id
                      :application/field-values [{:form 1 :field "1" :value "nonexistent_option"}]}]}
           (ok-command-with-warnings {:type :application.command/save-draft
                                      :actor applicant-user-id
                                      :field-values [{:form 1 :field "1" :value "nonexistent_option"}]}
                                     (build-application-view [dummy-created-event])))))

  (testing "validation of conditional fields"
    (is (= {:warnings [{:field-id "8" :form-id 7 :type :t.form.validation/invalid-email}]
            :events [{:event/type :application.event/draft-saved
                      :event/time test-time
                      :event/actor applicant-user-id
                      :application/id app-id
                      :application/field-values [{:form 7 :field "7" :value "y"}
                                                 {:form 7 :field "8" :value "invalid_email"}]}]}
           (ok-command-with-warnings {:type :application.command/save-draft
                                      :actor applicant-user-id
                                      :field-values [{:form 7 :field "7" :value "y"}
                                                     {:form 7 :field "8" :value "invalid_email"}]}
                                     (build-application-view [(merge dummy-created-event {:application/forms [{:form/id 7}]})])))
        "visible field should not accept invalid values")
    (is (= {:warnings [{:field-id "8" :form-id 7 :type :t.form.validation/invalid-email}]
            :events [{:event/type :application.event/draft-saved
                      :event/time test-time
                      :event/actor applicant-user-id
                      :application/id app-id
                      :application/field-values [{:form 7 :field "7" :value "n"}]}]} ; invisible field value is not stored
           (ok-command-with-warnings {:type :application.command/save-draft
                                      :actor applicant-user-id
                                      :field-values [{:form 7 :field "7" :value "n"}
                                                     {:form 7 :field "8" :value "invalid_email"}]}
                                     (build-application-view [(merge dummy-created-event {:application/forms [{:form/id 7}]})])))
        "invisible should not accept invalid values")
    (is (= {:event/type :application.event/draft-saved
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/field-values [{:form 7, :field "7", :value "y"}
                                       {:form 7 :field "8" :value "valid@example.com"}]}
           (ok-command {:type :application.command/save-draft
                        :actor applicant-user-id
                        :field-values [{:form 7 :field "7" :value "y"}
                                       {:form 7 :field "8" :value "valid@example.com"}]}
                       (build-application-view [(merge dummy-created-event {:application/forms [{:form/id 7}]})])))
        "answers to visible fields should get stored")
    (is (= {:event/type :application.event/draft-saved
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/field-values [{:form 7, :field "7", :value "n"}]}
           (ok-command {:type :application.command/save-draft
                        :actor applicant-user-id
                        :field-values [{:form 7 :field "7" :value "n"}
                                       {:form 7 :field "8" :value "valid@example.com"}]}
                       (build-application-view [(merge dummy-created-event {:application/forms [{:form/id 7}]})])))
        "answers to invisible fields should get dropped"))

  (testing "only the applicant can save a draft"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/save-draft
                          :actor "somebody"
                          :field-values [{:form 1 :field "1" :value "foo"}
                                         {:form 1 :field "2" :value "bar"}]}
                         (build-application-view [dummy-created-event]))
           (fail-command {:type :application.command/save-draft
                          :actor handler-user-id
                          :field-values [{:form 1 :field "1" :value "foo"}
                                         {:form 1 :field "2" :value "bar"}]}
                         (build-application-view [dummy-created-event])))))
  (testing "draft cannot be updated after submitting"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/save-draft
                          :actor applicant-user-id
                          :field-values [{:form 1 :field "1" :value "bar"}]}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "draft can be updated after returning it to applicant"
    (is (= {:event/type :application.event/draft-saved
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/field-values [{:form 1 :field "1" :value "bar"}]}
           (ok-command {:type :application.command/save-draft
                        :actor applicant-user-id
                        :field-values [{:form 1 :field "1" :value "bar"}]}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-returned-event]))))))

(deftest test-accept-licenses
  (is (= {:event/type :application.event/licenses-accepted
          :event/time test-time
          :event/actor applicant-user-id
          :application/id app-id
          :application/accepted-licenses #{1 2}}
         (ok-command {:type :application.command/accept-licenses
                      :actor applicant-user-id
                      :accepted-licenses [1 2]}
                     (build-application-view [dummy-created-event])))))

(deftest test-add-licenses
  (is (= {:event/type :application.event/licenses-added
          :event/time test-time
          :event/actor handler-user-id
          :application/id app-id
          :application/comment "comment"
          :application/licenses [{:license/id 1} {:license/id 2}]}
         (ok-command {:type :application.command/add-licenses
                      :actor handler-user-id
                      :comment "comment"
                      :licenses [1 2]}
                     (build-application-view [dummy-created-event
                                              dummy-submitted-event]))))
  (is (= {:errors [{:type :must-not-be-empty :key :licenses}]}
         (fail-command {:type :application.command/add-licenses
                        :actor handler-user-id
                        :comment "comment"
                        :licenses []}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event])))))

(deftest test-change-resources
  (testing "applicant can add resources with different form to draft application"
    (is (= {:event/type :application.event/resources-changed
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/forms [{:form/id 1} ; workflow form must persist
                                {:form/id 2}]
            :application/resources [{:catalogue-item/id 1 :resource/ext-id "res1"}
                                    {:catalogue-item/id 2 :resource/ext-id "res2"}
                                    {:catalogue-item/id 3 :resource/ext-id "res3"}]
            :application/licenses [{:license/id 1}
                                   {:license/id 2}
                                   {:license/id 3}]}
           (ok-command {:type :application.command/change-resources
                        :actor applicant-user-id
                        :catalogue-item-ids [1 2 3]}
                       (build-application-view [dummy-created-event])))))

  (testing "applicant can replace resources of a draft application"
    (is (= {:event/type :application.event/resources-changed
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/forms [{:form/id 1}]
            :application/resources [{:catalogue-item/id 2 :resource/ext-id "res2"}]
            :application/licenses [{:license/id 2}]}
           (ok-command {:type :application.command/change-resources
                        :actor applicant-user-id
                        :catalogue-item-ids [2]}
                       (build-application-view [dummy-created-event])))))

  (testing "applicant cannot add resources to a submitted application"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids [1 2]}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))

  (testing "applicant cannot add resources with different workflow"
    (is (= {:errors [{:type :unbundlable-catalogue-items
                      :catalogue-item-ids [1 4]}]}
           (fail-command {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids [1 4]}
                         (build-application-view [dummy-created-event])))))

  (testing "applicant cannot replace resources with different workflow"
    (is (= {:errors [{:type :changes-original-workflow :workflow/id 1 :ids [2]}]}
           (fail-command {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids [4]}
                         (build-application-view [dummy-created-event])))))

  (testing "applicant can replace resources with different form"
    (is (= {:event/type :application.event/resources-changed
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/forms [{:form/id 2}]
            :application/resources [{:catalogue-item/id 3 :resource/ext-id "res3"}]
            :application/licenses [{:license/id 1}
                                   {:license/id 2}
                                   {:license/id 3}]}
           (ok-command {:type :application.command/change-resources
                        :actor applicant-user-id
                        :catalogue-item-ids [3]}
                       (build-application-view [dummy-created-event])))))

  (doseq [[state events] [["submitted" [dummy-created-event dummy-submitted-event]]
                          ["approved" [dummy-created-event dummy-submitted-event dummy-approved-event]]]]
    (testing (str "handler can add resources to " state "application")
      (is (= {:event/type :application.event/resources-changed
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id
              :application/comment "Changed these for you"
              :application/forms [{:form/id 1}]
              :application/resources [{:catalogue-item/id 1 :resource/ext-id "res1"}
                                      {:catalogue-item/id 2 :resource/ext-id "res2"}]
              :application/licenses [{:license/id 1}
                                     {:license/id 2}]}
             (ok-command {:type :application.command/change-resources
                          :actor handler-user-id
                          :comment "Changed these for you"
                          :catalogue-item-ids [1 2]}
                         (build-application-view events))))
      (testing "- even with a different workflow or form"
        (is (= {:event/type :application.event/resources-changed
                :event/time test-time
                :event/actor handler-user-id
                :application/id app-id
                :application/comment "Changed these for you"
                :application/forms [{:form/id 1} {:form/id 2}]
                :application/resources [{:catalogue-item/id 1 :resource/ext-id "res1"}
                                        {:catalogue-item/id 3 :resource/ext-id "res3"}
                                        {:catalogue-item/id 4 :resource/ext-id "res4"}]
                :application/licenses [{:license/id 1}
                                       {:license/id 2}
                                       {:license/id 3}]}
               (ok-command {:type :application.command/change-resources
                            :actor handler-user-id
                            :comment "Changed these for you"
                            :catalogue-item-ids [1 3 4]}
                           (build-application-view events)))))))

  (testing "the catalogue item must exist"
    (is (= {:errors [{:type :invalid-catalogue-item :catalogue-item-id 42}]}
           (fail-command {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids [42]}
                         (build-application-view [dummy-created-event])))))

  (testing "there must be at least one catalogue item"
    (is (= {:errors [{:type :must-not-be-empty :key :catalogue-item-ids}]}
           (fail-command {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids []}
                         (build-application-view [dummy-created-event]))))))

(deftest test-submit
  (let [created-event (merge dummy-created-event {:application/resources [{:catalogue-item/id 1
                                                                           :resource/ext-id "res1"}
                                                                          {:catalogue-item/id 2
                                                                           :resource/ext-id "res2"}]
                                                  :application/licenses [{:license/id 1}]})]
    (testing "cannot submit a valid form if licenses are not accepted"
      (is (= {:errors [{:type :t.actions.errors/licenses-not-accepted}]}
             (fail-command {:type :application.command/submit
                            :actor applicant-user-id}
                           (build-application-view [created-event
                                                    dummy-draft-saved-event])))))

    (testing "can submit a valid form when licenses are accepted"
      (is (= {:event/type :application.event/submitted
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id}
             (ok-command {:type :application.command/submit
                          :actor applicant-user-id}
                         (build-application-view [created-event
                                                  dummy-draft-saved-event
                                                  dummy-licenses-accepted-event])))))

    (testing "can submit two valid forms when licenses are accepted"
      (is (= {:event/type :application.event/submitted
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id}
             (ok-command {:type :application.command/submit
                          :actor applicant-user-id}
                         (let [forms {:application/forms [{:form/id 1} {:form/id 2}]}
                               fields {:application/field-values [{:form 2 :field "1" :value "baz"}]}]
                           (build-application-view [(merge dummy-created-event forms)
                                                    (merge dummy-draft-saved-event fields)
                                                    dummy-licenses-accepted-event]))))))

    (testing "required fields"
      (testing "1st field is optional and empty, 2nd field is required but invisible"
        (is (= {:event/type :application.event/submitted
                :event/time test-time
                :event/actor applicant-user-id
                :application/id app-id}
               (ok-command {:type :application.command/submit
                            :actor applicant-user-id}
                           (build-application-view [created-event
                                                    dummy-draft-saved-event
                                                    dummy-licenses-accepted-event
                                                    (merge dummy-draft-saved-event {:application/field-values [{:form 1 :field "1" :value ""}
                                                                                                               {:form 1 :field "2" :value "present"}]})])))
            "submit succeeds even if application is inconsistent and contains an answer for an invisible field")
        (is (= {:event/type :application.event/submitted
                :event/time test-time
                :event/actor applicant-user-id
                :application/id app-id}
               (ok-command {:type :application.command/submit
                            :actor applicant-user-id}
                           (build-application-view [created-event
                                                    dummy-draft-saved-event
                                                    dummy-licenses-accepted-event
                                                    (merge dummy-draft-saved-event {:application/field-values [{:form 1 :field "1" :value ""}
                                                                                                               {:form 1 :field "2" :value ""}]})])))))
      (testing "1st field is given, 2nd field is required and visible but empty"
        (is (= {:errors [{:type :t.form.validation/required
                          :form-id 1
                          :field-id "2"}]}
               (fail-command {:type :application.command/submit
                              :actor applicant-user-id}
                             (build-application-view [created-event
                                                      dummy-draft-saved-event
                                                      dummy-licenses-accepted-event
                                                      (merge dummy-draft-saved-event {:application/field-values [{:form 1 :field "1" :value "foo"}
                                                                                                                 {:form 1 :field "2" :value ""}]})])))))
      (testing "1st field is given, 2nd field is given"
        (is (= {:event/type :application.event/submitted
                :event/time test-time
                :event/actor applicant-user-id
                :application/id app-id}
               (ok-command {:type :application.command/submit
                            :actor applicant-user-id}
                           (build-application-view [created-event
                                                    dummy-draft-saved-event
                                                    dummy-licenses-accepted-event])))))

      (testing "cannot submit if one of two forms has required fields"
        (is (= {:errors [{:type :t.form.validation/required
                          :form-id 2
                          :field-id "1"}]}
               (fail-command {:type :application.command/submit
                              :actor applicant-user-id}
                             (build-application-view [(merge created-event {:application/forms [{:form/id (:form/id (dummy-forms 1))}
                                                                                                {:form/id (:form/id (dummy-forms 2))}]})
                                                      dummy-licenses-accepted-event
                                                      (merge dummy-draft-saved-event {:application/field-values [{:form 1 :field "1" :value "foo"}
                                                                                                                 {:form 1 :field "2" :value "bar"}
                                                                                                                 {:form 2 :field "1" :value ""}]})]))))
        (is (= {:errors [{:type :t.form.validation/required
                          :form-id 1
                          :field-id "2"}]}
               (fail-command {:type :application.command/submit
                              :actor applicant-user-id}
                             (build-application-view [(merge created-event {:application/forms [{:form/id (:form/id (dummy-forms 1))}
                                                                                                {:form/id (:form/id (dummy-forms 2))}]})
                                                      dummy-licenses-accepted-event
                                                      (merge dummy-draft-saved-event {:application/field-values [{:form 1 :field "1" :value "foo"}
                                                                                                                 {:form 1 :field "2" :value ""}
                                                                                                                 {:form 2 :field "1" :value "baz"}]})]))))))

    (testing "can submit draft even if catalogue item is disabled"
      (is (= {:event/type :application.event/submitted
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id}
             (ok-command {:type :application.command/submit
                          :actor applicant-user-id}
                         (build-application-view [(merge-with into created-event {:application/resources [{:catalogue-item/id 7
                                                                                                           :resource/ext-id "res-disabled"}]})
                                                  dummy-draft-saved-event
                                                  dummy-licenses-accepted-event])))))

    (testing "non-applicant cannot submit"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command {:type :application.command/submit
                            :actor "somebody"}
                           (build-application-view [created-event
                                                    dummy-draft-saved-event
                                                    dummy-licenses-accepted-event
                                                    (merge dummy-licenses-accepted-event {:event/actor "somebody"})])))))

    (testing "cannot submit twice"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command {:type :application.command/submit
                            :actor applicant-user-id}
                           (build-application-view [created-event
                                                    dummy-draft-saved-event
                                                    dummy-licenses-accepted-event
                                                    dummy-submitted-event])))))))

(deftest test-return-resubmit
  (testing "handler can return application"
    (is (= {:event/type :application.event/returned
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/comment ""}
           (ok-command {:type :application.command/return
                        :actor handler-user-id
                        :comment ""}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event])))))
  (testing "applicant can resubmit returned application"
    (is (= {:event/type :application.event/submitted
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id}
           (ok-command {:type :application.command/submit
                        :actor applicant-user-id}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-returned-event])))))
  (testing "applicant can resubmit even when catalogue item is disabled"
    (let [created-event (-> dummy-created-event
                            (update :application/resources conj {:catalogue-item/id 7
                                                                 :resource/ext-id "res-disabled"}))]
      (is (= {:event/type :application.event/submitted
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id}
             (ok-command {:type :application.command/submit
                          :actor applicant-user-id}
                         (build-application-view [created-event
                                                  dummy-submitted-event
                                                  dummy-returned-event])))))))

(deftest test-assign-external-id
  (testing "handler can assign id"
    (is (= {:event/type :application.event/external-id-assigned
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/external-id "ext123"}
           (ok-command {:type :application.command/assign-external-id
                        :actor handler-user-id
                        :external-id "ext123"}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event])))))
  (testing "applicant can't assign id"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/assign-external-id
                          :actor applicant-user-id
                          :external-id "ext123"}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event]))))))

(deftest test-approve-or-reject
  (testing "approved successfully"
    (is (= {:event/type :application.event/approved
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/comment "fine"}
           (ok-command {:type :application.command/approve
                        :actor handler-user-id
                        :comment "fine"}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event])))))
  (testing "approved with end-date"
    (is (= {:event/type :application.event/approved
            :event/time test-time
            :event/actor handler-user-id
            :entitlement/end (time/plus (DateTime. 1234) (time/days 1))
            :application/id app-id
            :application/comment "fine"}
           (with-fixed-time (DateTime. 1234)
             (fn []
               (ok-command {:type :application.command/approve
                            :actor handler-user-id
                            :entitlement-end (time/plus (DateTime. 1234) (time/days 1))
                            :comment "fine"}
                           (build-application-view [dummy-created-event
                                                    dummy-submitted-event])))))))
  (testing "rejected successfully"
    (is (= {:event/type :application.event/rejected
            :application/comment "bad"
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id}
           (ok-command {:type :application.command/reject
                        :actor handler-user-id
                        :comment "bad"}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event])))))
  (testing "throws with past entitlement end-date"
    (is (= {:errors [{:type :t.actions.errors/entitlement-end-not-in-future}]}
           (fail-command {:type :application.command/approve
                          :actor handler-user-id
                          :entitlement-end (DateTime. 1234)}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event]))))))

(deftest test-close
  (testing "handler can close approved application"
    (is (= {:event/type :application.event/closed
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/comment "outdated"}
           (ok-command {:type :application.command/close
                        :actor handler-user-id
                        :comment "outdated"}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-approved-event])))))
  (testing "applicant can close returned application"
    (is (= {:event/type :application.event/closed
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/comment "outdated"}
           (ok-command {:type :application.command/close
                        :actor applicant-user-id
                        :comment "outdated"}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-returned-event])))))
  (testing "handler can close returned application"
    (is (= {:event/type :application.event/closed
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/comment "outdated"}
           (ok-command {:type :application.command/close
                        :actor handler-user-id
                        :comment "outdated"}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-returned-event]))))))

(deftest test-revoke
  (is (= {:event/type :application.event/revoked
          :event/time test-time
          :event/actor handler-user-id
          :application/id app-id
          :application/comment "license violated"}
         (ok-command {:type :application.command/revoke
                      :actor handler-user-id
                      :comment "license violated"}
                     (build-application-view [dummy-created-event
                                              dummy-submitted-event
                                              dummy-approved-event])))))

(deftest test-decision
  (testing "required :valid-user? injection"
    (is (= {:errors [{:type :missing-injection :injection :valid-user?}]}
           (fail-command {:type :application.command/request-decision
                          :actor handler-user-id
                          :deciders [decider-user-id]
                          :comment "pls"}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])
                         (dissoc command-injections :valid-user?)))))
  (testing "decider must be a valid user"
    (is (= {:errors [{:type :t.form.validation/invalid-user :userid "deity"}]}
           (fail-command {:type :application.command/request-decision
                          :actor handler-user-id
                          :deciders ["deity"]
                          :comment "pls"}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "deciding before ::request-decision should fail"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/decide
                          :actor decider-user-id
                          :decision :approved
                          :comment "pls"}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "decision requested successfully"
    (let [decision-requested-event (ok-command {:type :application.command/request-decision
                                                :actor handler-user-id
                                                :deciders [decider-user-id]
                                                :comment ""}
                                               (build-application-view [dummy-created-event
                                                                        dummy-submitted-event]))]
      (is (instance? UUID (:application/request-id decision-requested-event)))
      (is (= {:event/type :application.event/decision-requested
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id
              :application/request-id (:application/request-id decision-requested-event)
              :application/deciders [decider-user-id]
              :application/comment ""}
             decision-requested-event))))
  (testing "only the requested user can decide"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/decide
                          :actor handler-user-id
                          :decision :approved
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-decision-requested-event])))))
  (testing "decided approved successfully"
    (is (= {:event/type :application.event/decided
            :event/time test-time
            :event/actor decider-user-id
            :application/id app-id
            :application/request-id decision-request-id
            :application/decision :approved
            :application/comment ""}
           (ok-command {:type :application.command/decide
                        :actor decider-user-id
                        :decision :approved
                        :comment ""}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-decision-requested-event])))))
  (testing "cannot decide approve twice"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/decide
                          :actor decider-user-id
                          :decision :approved
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-decision-requested-event
                                                  dummy-decided-event])))))
  (testing "decided rejected successfully"
    (is (= {:event/type :application.event/decided
            :event/time test-time
            :event/actor decider-user-id
            :application/id app-id
            :application/request-id decision-request-id
            :application/decision :rejected
            :application/comment ""}
           (ok-command {:type :application.command/decide
                        :actor decider-user-id
                        :decision :rejected
                        :comment ""}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-decision-requested-event])))))
  (testing "cannot decide reject twice"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/decide
                          :actor decider-user-id
                          :decision :rejected
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-decision-requested-event
                                                  (merge dummy-decided-event {:application/decision :rejected})])))))
  (testing "other decisions are not possible"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Value does not match schema"
                          (fail-command {:type :application.command/decide
                                         :actor decider-user-id
                                         :decision :foobar
                                         :comment ""}
                                        (build-application-view [dummy-created-event
                                                                 dummy-submitted-event
                                                                 dummy-decision-requested-event]))))))

(deftest test-add-member
  (testing "handler can add members"
    (is (= {:event/type :application.event/member-added
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/member {:userid "member1"}}
           (ok-command {:type :application.command/add-member
                        :actor handler-user-id
                        :member {:userid "member1"}}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event])))))
  (testing "added members can see the application"
    (is (-> (build-application-view [dummy-created-event
                                     dummy-submitted-event
                                     dummy-member-added-event])
            (model/see-application? "somebody"))))
  (testing "only handler can add members"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/add-member
                          :actor applicant-user-id
                          :member {:userid "member1"}}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-member-added-event]))
           (fail-command {:type :application.command/add-member
                          :actor "member1"
                          :member {:userid "member2"}}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-member-added-event])))))
  (testing "only valid users can be added"
    (is (= {:errors [{:type :t.form.validation/invalid-user :userid "does-not-exist"}]}
           (fail-command {:type :application.command/add-member
                          :actor handler-user-id
                          :member {:userid "does-not-exist"}}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-member-added-event]))))))

(deftest test-invite-member
  (testing "applicant can invite members"
    (is (= {:event/type :application.event/member-invited
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/member {:name "Member Applicant 1"
                                 :email "member1@applicants.com"}
            :invitation/token "very-secure"}
           (ok-command {:type :application.command/invite-member
                        :actor applicant-user-id
                        :member {:name "Member Applicant 1"
                                 :email "member1@applicants.com"}}
                       (build-application-view [dummy-created-event])))))
  (testing "other users cannot invite members"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/invite-member
                          :actor "member1"
                          :member {:name "Member Applicant 1"
                                   :email "member1@applicants.com"}}
                         (build-application-view [dummy-created-event])))))
  (testing "applicant can't invite members to submitted application"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/invite-member
                          :actor applicant-user-id
                          :member {:name "Member Applicant 1"
                                   :email "member1@applicants.com"}}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "handler can invite members to submitted application"
    (is (= {:event/type :application.event/member-invited
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/member {:name "Member Applicant 1"
                                 :email "member1@applicants.com"}
            :invitation/token "very-secure"}
           (ok-command {:type :application.command/invite-member
                        :actor handler-user-id
                        :member {:name "Member Applicant 1"
                                 :email "member1@applicants.com"}}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event]))))))

(deftest test-invite-reviewer-decider
  (doseq [user #{applicant-user-id "member1"}]
    (testing (str user " cannot invite reviewer or decider to draft application")
      (is (= {:errors [{:type :forbidden}]}
             (fail-command {:type :application.command/invite-reviewer
                            :actor user
                            :reviewer {:name "A Reviewer"
                                       :email "reviewer@applicants.com"}}
                           (build-application-view [dummy-created-event]))
             (fail-command {:type :application.command/invite-decider
                            :actor user
                            :decider {:name "A Decider"
                                      :email "decider@applicants.com"}}
                           (build-application-view [dummy-created-event])))))
    (testing (str user " cannot invite reviewer or decider to submitted application")
      (is (= {:errors [{:type :forbidden}]}
             (fail-command {:type :application.command/invite-reviewer
                            :actor user
                            :reviewer {:name "A Reviewer"
                                       :email "reviewer@applicants.com"}}
                           (build-application-view [dummy-created-event
                                                    dummy-submitted-event]))
             (fail-command {:type :application.command/invite-decider
                            :actor user
                            :decider {:name "A Decider"
                                      :email "decider@applicants.com"}}
                           (build-application-view [dummy-created-event
                                                    dummy-submitted-event]))))))
  (testing "handler can invite reviewer to submitted application"
    (is (= {:event/type :application.event/reviewer-invited
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/reviewer {:name "A Reviewer"
                                   :email "reviewer@applicants.com"}
            :application/comment "please review"
            :invitation/token "very-secure"}
           (ok-command {:type :application.command/invite-reviewer
                        :actor handler-user-id
                        :comment "please review"
                        :reviewer {:name "A Reviewer"
                                   :email "reviewer@applicants.com"}}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event])))))
  (testing "handler can invite decider to submitted application"
    (is (= {:event/type :application.event/decider-invited
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/decider {:name "A Decider"
                                  :email "decider@applicants.com"}
            :application/comment "please decide"
            :invitation/token "very-secure"}
           (ok-command {:type :application.command/invite-decider
                        :actor handler-user-id
                        :comment "please decide"
                        :decider {:name "A Decider"
                                  :email "decider@applicants.com"}}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event]))))))

(deftest test-accept-invitation
  (testing "invited member"
    (testing "can join draft"
      (is (= {:event/type :application.event/member-joined
              :event/time test-time
              :event/actor "somebody"
              :application/id app-id
              :invitation/token "very-secure"}
             (ok-command {:type :application.command/accept-invitation
                          :actor "somebody"
                          :token "very-secure"}
                         (build-application-view [dummy-created-event
                                                  dummy-member-invited-event])))))

    (testing "can't join if they are already a member"
      (is (= {:errors [{:type :t.actions.errors/already-member :userid "somebody" :application-id app-id}]}
             (fail-command {:type :application.command/accept-invitation
                            :actor "somebody"
                            :token "very-secure"}
                           (build-application-view [dummy-created-event
                                                    dummy-member-invited-event
                                                    dummy-member-added-event])))))

    (testing "can't use invalid token"
      (is (= {:errors [{:type :t.actions.errors/invalid-token :token "wrong-token"}]}
             (fail-command {:type :application.command/accept-invitation
                            :actor "somebody"
                            :token "wrong-token"}
                           (build-application-view [dummy-created-event
                                                    dummy-member-invited-event])))))

    (testing "can't use token twice"
      (is (= {:errors [{:type :t.actions.errors/invalid-token :token "very-secure"}]}
             (fail-command {:type :application.command/accept-invitation
                            :actor "somebody2"
                            :token "very-secure"}
                           (build-application-view [dummy-created-event
                                                    dummy-member-invited-event
                                                    dummy-member-joined-event])))))

    (testing "can join submitted application"
      (is (= {:event/type :application.event/member-joined
              :event/actor "somebody"
              :event/time test-time
              :application/id app-id
              :invitation/token "very-secure"}
             (ok-command {:type :application.command/accept-invitation
                          :actor "somebody"
                          :token "very-secure"}
                         (build-application-view [dummy-created-event
                                                  dummy-member-invited-event
                                                  dummy-submitted-event])))))

    (testing "can't join a closed application"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command {:type :application.command/accept-invitation
                            :actor "somebody"
                            :token "very-secure"}
                           (build-application-view [dummy-created-event
                                                    dummy-member-invited-event
                                                    dummy-submitted-event
                                                    dummy-closed-event]))))))
  (testing "invited reviewer"
    (let [reviewer-invited-event {:event/type :application.event/reviewer-invited
                                  :event/time test-time
                                  :event/actor handler-user-id
                                  :application/id app-id
                                  :application/reviewer {:name "Some Body" :email "somebody@applicants.com"}
                                  :invitation/token "very-secure"}]
      (testing "can join submitted application"
        (let [event (ok-command {:type :application.command/accept-invitation
                                 :actor "somebody"
                                 :token "very-secure"}
                                (build-application-view [dummy-created-event
                                                         dummy-submitted-event
                                                         reviewer-invited-event]))]
          (is (= {:event/type :application.event/reviewer-joined
                  :event/time test-time
                  :event/actor "somebody"
                  :application/id app-id
                  :invitation/token "very-secure"
                  :application/request-id (:application/request-id event)}
                 event))
          (is (instance? UUID (:application/request-id event)))))
      (testing "can't use invalid token"
        (is (= {:errors [{:type :t.actions.errors/invalid-token :token "wrong-token"}]}
               (fail-command {:type :application.command/accept-invitation
                              :actor "somebody"
                              :token "wrong-token"}
                             (build-application-view [dummy-created-event
                                                      dummy-submitted-event
                                                      reviewer-invited-event])))))
      (testing "can't use token twice"
        (let [reviewer-joined-event {:event/type :application.event/reviewer-joined
                                     :event/time test-time
                                     :event/actor "somebody"
                                     :application/id app-id
                                     :application/request-id (UUID/randomUUID)
                                     :invitation/token "very-secure"}]
          (is (= {:errors [{:type :t.actions.errors/invalid-token :token "very-secure"}]}
                 (fail-command {:type :application.command/accept-invitation
                                :actor "somebody2"
                                :token "very-secure"}
                               (build-application-view [dummy-created-event
                                                        dummy-submitted-event
                                                        reviewer-invited-event
                                                        reviewer-joined-event]))))))))
  (testing "invited decider"
    (let [decider-invited-event {:event/type :application.event/decider-invited
                                 :event/time test-time
                                 :event/actor handler-user-id
                                 :application/id app-id
                                 :application/decider {:name "Some Body" :email "somebody@applicants.com"}
                                 :invitation/token "very-secure"}]
      (testing "can join submitted application"
        (let [event (ok-command {:type :application.command/accept-invitation
                                 :actor "somebody"
                                 :token "very-secure"}
                                (build-application-view [dummy-created-event
                                                         dummy-submitted-event
                                                         decider-invited-event]))]
          (is (= {:event/type :application.event/decider-joined
                  :event/time test-time
                  :event/actor "somebody"
                  :application/id app-id
                  :invitation/token "very-secure"
                  :application/request-id (:application/request-id event)}
                 event))
          (is (instance? UUID (:application/request-id event)))))
      (testing "can't use invalid token"
        (is (= {:errors [{:type :t.actions.errors/invalid-token :token "wrong-token"}]}
               (fail-command {:type :application.command/accept-invitation
                              :actor "somebody"
                              :token "wrong-token"}
                             (build-application-view [dummy-created-event
                                                      dummy-submitted-event
                                                      decider-invited-event])))))
      (testing "can't use token twice"
        (let [decider-joined-event {:event/type :application.event/decider-joined
                                    :event/time test-time
                                    :event/actor "somebody"
                                    :application/id app-id
                                    :application/request-id (UUID/randomUUID)
                                    :invitation/token "very-secure"}]
          (is (= {:errors [{:type :t.actions.errors/invalid-token :token "very-secure"}]}
                 (fail-command {:type :application.command/accept-invitation
                                :actor "somebody2"
                                :token "very-secure"}
                               (build-application-view [dummy-created-event
                                                        dummy-submitted-event
                                                        decider-invited-event
                                                        decider-joined-event])))))))))

(deftest test-remove-member
  (testing "applicant can remove members"
    (is (= {:event/type :application.event/member-removed
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/member {:userid "somebody"}
            :application/comment "some comment"}
           (ok-command {:type :application.command/remove-member
                        :actor applicant-user-id
                        :member {:userid "somebody"}
                        :comment "some comment"}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-member-added-event])))))
  (testing "handler can remove members"
    (is (= {:event/type :application.event/member-removed
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            ;; NB no comment
            :application/member {:userid "somebody"}}
           (ok-command {:type :application.command/remove-member
                        :actor handler-user-id
                        :member {:userid "somebody"}}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-member-added-event])))))
  (testing "applicant cannot be removed"
    (is (= {:errors [{:type :cannot-remove-applicant}]}
           (fail-command {:type :application.command/remove-member
                          :actor applicant-user-id
                          :member {:userid applicant-user-id}}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-member-added-event]))
           (fail-command {:type :application.command/remove-member
                          :actor handler-user-id
                          :member {:userid applicant-user-id}}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-member-added-event])))))
  (testing "non-members cannot be removed"
    (is (= {:errors [{:type :user-not-member :user {:userid "notamember"}}]}
           (fail-command {:type :application.command/remove-member
                          :actor handler-user-id
                          :member {:userid "notamember"}}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-member-added-event])))))
  (testing "removed members cannot see the application"
    (let [member-added-application (build-application-view [dummy-created-event
                                                            dummy-submitted-event
                                                            dummy-member-added-event])]
      (is (model/see-application? member-added-application "somebody"))
      (let [member-removed-event (ok-command {:type :application.command/remove-member
                                              :actor applicant-user-id
                                              :member {:userid "somebody"}}
                                             member-added-application)]
        (is (not (-> (build-application-view [dummy-created-event
                                              dummy-submitted-event
                                              dummy-member-added-event
                                              member-removed-event])
                     (model/see-application? "somebody"))))))))

(deftest test-uninvite-member
  (testing "uninvite member by applicant"
    (is (= {:event/type :application.event/member-uninvited
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/member {:name "Some Body" :email "somebody@applicants.com"}}
           (ok-command {:type :application.command/uninvite-member
                        :actor applicant-user-id
                        :member {:name "Some Body" :email "somebody@applicants.com"}}
                       (build-application-view [dummy-created-event
                                                dummy-member-invited-event
                                                dummy-submitted-event])))))
  (testing "uninvite member by handler"
    (is (= {:event/type :application.event/member-uninvited
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/member {:name "Some Body" :email "somebody@applicants.com"}
            :application/comment ""}
           (ok-command {:type :application.command/uninvite-member
                        :actor handler-user-id
                        :member {:name "Some Body" :email "somebody@applicants.com"}
                        :comment ""}
                       (build-application-view [dummy-created-event
                                                dummy-member-invited-event
                                                dummy-submitted-event])))))
  (testing "only invited members can be uninvited"
    (is (= {:errors [{:type :user-not-member :user {:name "Not Member" :email "not@member.com"}}]}
           (fail-command {:type :application.command/uninvite-member
                          :actor handler-user-id
                          :member {:name "Not Member" :email "not@member.com"}
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-member-invited-event
                                                  dummy-submitted-event]))))))

(deftest test-change-applicant
  (testing "handler can promote existing member"
    (is (= {:event/type :application.event/applicant-changed
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/applicant {:userid "somebody"}
            :application/comment ""}
           (ok-command {:type :application.command/change-applicant
                        :actor handler-user-id
                        :member {:userid "somebody"}
                        :comment ""}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-member-added-event])))))
  (testing "applicant can't promote"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/change-applicant
                          :actor applicant-user-id
                          :member {:userid "somebody"}
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-member-added-event])))))
  (testing "member can't promote themself"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/change-applicant
                          :actor "somebody"
                          :member {:userid "somebody"}
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-member-added-event])))))
  (testing "handler can't promote non-member"
    (is (= {:errors [{:type :user-not-member :user {:userid "unknown"}}]}
           (fail-command {:type :application.command/change-applicant
                          :actor handler-user-id
                          :member {:userid "unknown"}
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-member-added-event])))))
  (testing "handler can't promote current applicant"
    (is (= {:errors [{:type :user-not-member :user {:userid "applicant"}}]}
           (fail-command {:type :application.command/change-applicant
                          :actor handler-user-id
                          :member {:userid applicant-user-id}
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-member-added-event]))))))

(deftest test-review
  (testing "required :valid-user? injection"
    (is (= {:errors [{:type :missing-injection :injection :valid-user?}]}
           (fail-command {:type :application.command/request-review
                          :actor handler-user-id
                          :reviewers ["reviewer"]
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])
                         (dissoc command-injections :valid-user?)))))
  (testing "reviewers must not be empty"
    (is (= {:errors [{:type :must-not-be-empty :key :reviewers}]}
           (fail-command {:type :application.command/request-review
                          :actor handler-user-id
                          :reviewers []
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "reviewers must be a valid users"
    (is (= {:errors [{:type :t.form.validation/invalid-user :userid "invaliduser"}
                     {:type :t.form.validation/invalid-user :userid "invaliduser2"}]}
           (fail-command {:type :application.command/request-review
                          :actor handler-user-id
                          :reviewers ["invaliduser" "reviewer" "invaliduser2"]
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "reviewing before ::request-review should fail"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/review
                          :actor "reviewer"
                          :comment ""}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "can request review"
    (let [review-requested-event (ok-command {:type :application.command/request-review
                                              :actor handler-user-id
                                              :reviewers ["reviewer" "reviewer2"]
                                              :comment ""}
                                             (build-application-view [dummy-created-event
                                                                      dummy-submitted-event]))]
      (is (= {:event/type :application.event/review-requested
              :application/request-id (:application/request-id review-requested-event)
              :application/reviewers ["reviewer" "reviewer2"]
              :application/comment ""
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id}
             review-requested-event))))
  (testing "second review request partly overrides previous request"
    (let [review-requested-event (ok-command {:type :application.command/request-review
                                              :actor handler-user-id
                                              :reviewers ["reviewer2"]
                                              :comment ""}
                                             (build-application-view [dummy-created-event
                                                                      dummy-submitted-event
                                                                      dummy-review-requested-event]))]
      (is (= {:event/type :application.event/review-requested
              :application/request-id (:application/request-id review-requested-event)
              :application/reviewers ["reviewer2"]
              :application/comment ""
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id}
             review-requested-event))))
  (testing "only the requested reviewer can review"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/review
                          :actor "reviewer3"
                          :comment "..."}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-review-requested-event])))))
  (testing "review succeeds"
    (is (= {:event/type :application.event/reviewed
            :event/time test-time
            :event/actor "reviewer"
            :application/id app-id
            :application/request-id review-request-id
            :application/comment "..."}
           (ok-command {:type :application.command/review
                        :actor "reviewer"
                        :comment "..."}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-review-requested-event])))))
  (testing "cannot review twice"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/review
                          :actor "reviewer"
                          :comment "..."}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-review-requested-event
                                                  dummy-reviewed-event])))))
  (testing "second reviewer can review after first reviewer"
    (let [review-request-id-2 (UUID/randomUUID)]
      (is (= {:event/type :application.event/reviewed
              :event/time test-time
              :event/actor "reviewer2"
              :application/id app-id
              :application/request-id review-request-id-2
              :application/comment "..."}
             (ok-command {:type :application.command/review
                          :actor "reviewer2"
                          :comment "..."}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-review-requested-event
                                                  (merge dummy-review-requested-event
                                                         {:application/request-id review-request-id-2
                                                          :application/reviewers ["reviewer2"]})
                                                  dummy-reviewed-event])))))))

(deftest test-remark
  (testing "invalid attachments"
    (is (= {:errors [{:type :invalid-attachments
                      :attachments [2 3 1337]}]}
           (fail-command {:type :application.command/remark
                          :actor handler-user-id
                          :comment "handler's remark"
                          :attachments [{:attachment/id 2}
                                        {:attachment/id 3}
                                        {:attachment/id 1337}]
                          :public false}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "handler can remark"
    (is (= {:event/type :application.event/remarked
            :event/time test-time
            :event/actor handler-user-id
            :application/id app-id
            :application/comment "handler's remark"
            :application/public false
            :event/attachments [{:attachment/id 1}]}
           (ok-command {:type :application.command/remark
                        :actor handler-user-id
                        :comment "handler's remark"
                        :attachments [{:attachment/id 1}]
                        :public false}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event])))))
  (testing "reviewer cannot remark before becoming reviewer"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/remark
                          :actor "reviewer"
                          :comment ""
                          :public false}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "reviewer can remark"
    (is (= {:event/type :application.event/remarked
            :event/time test-time
            :event/actor reviewer-user-id
            :application/id app-id
            :application/comment ""
            :application/public false}
           (ok-command {:type :application.command/remark
                        :actor reviewer-user-id
                        :comment ""
                        :public false}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-review-requested-event])))))
  (testing "reviewer can remark after reviewing"
    (is (= {:event/type :application.event/remarked
            :event/time test-time
            :event/actor reviewer-user-id
            :application/id app-id
            :application/comment ""
            :application/public false}
           (ok-command {:type :application.command/remark
                        :actor reviewer-user-id
                        :comment ""
                        :public false}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-review-requested-event
                                                dummy-reviewed-event]))))))

(deftest test-copy-as-new
  (let [created-event (merge dummy-created-event {:application/external-id "2018/55"
                                                  :application/resources [{:catalogue-item/id 1 :resource/ext-id "res1"}
                                                                          {:catalogue-item/id 2 :resource/ext-id "res2"}]
                                                  :application/forms [{:form/id 3}]})
        draft-saved-event (merge dummy-draft-saved-event {:application/field-values [{:form 3 :field "text" :value "1"}
                                                                                     {:form 3 :field "attachment" :value "2"}]})]
    (testing "creates a new draft-application with the same form answers"
      (testing "for a draft it is just another draft"
        (is (= [{:event/type :application.event/created
                 :event/time test-time
                 :event/actor applicant-user-id
                 :application/id new-app-id
                 :application/external-id new-external-id
                 :application/resources [{:catalogue-item/id 1 :resource/ext-id "res1"}
                                         {:catalogue-item/id 2 :resource/ext-id "res2"}]
                 :application/licenses [{:license/id 1} {:license/id 2}]
                 :application/forms [{:form/id 1}]
                 :workflow/id 1
                 :workflow/type :workflow/default}
                {:event/type :application.event/draft-saved
                 :event/time test-time
                 :event/actor applicant-user-id
                 :application/id new-app-id
                 :application/field-values [{:form 3 :field "text" :value "1"}
                                            {:form 3 :field "attachment" :value "102"}]}]
               (ok-command {:type :application.command/copy-as-new
                            :actor applicant-user-id}
                           (build-application-view [created-event
                                                    draft-saved-event])))))

      (testing "for a submitted application it points to a previous draft application"
        (is (= [{:event/type :application.event/created
                 :event/time test-time
                 :event/actor applicant-user-id
                 :application/id new-app-id
                 :application/external-id new-external-id
                 :application/resources [{:catalogue-item/id 1
                                          :resource/ext-id "res1"}
                                         {:catalogue-item/id 2
                                          :resource/ext-id "res2"}]
                 :application/licenses [{:license/id 1} {:license/id 2}]
                 :application/forms [{:form/id 1}]
                 :workflow/id 1
                 :workflow/type :workflow/default}
                {:event/type :application.event/draft-saved
                 :event/time test-time
                 :event/actor applicant-user-id
                 :application/id new-app-id
                 :application/field-values [{:form 3 :field "text" :value "1"}
                                            {:form 3 :field "attachment" :value "102"}]}
                {:event/type :application.event/copied-from
                 :event/time test-time
                 :event/actor applicant-user-id
                 :application/id new-app-id
                 :application/copied-from {:application/id app-id
                                           :application/external-id "2018/55"}}
                {:event/type :application.event/copied-to
                 :event/time test-time
                 :event/actor applicant-user-id
                 :application/id app-id
                 :application/copied-to {:application/id new-app-id
                                         :application/external-id new-external-id}}]
               (ok-command {:type :application.command/copy-as-new
                            :actor applicant-user-id}
                           (build-application-view [created-event
                                                    draft-saved-event
                                                    dummy-submitted-event]))))))))

(deftest test-delete
  (testing "handler cannot delete application"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/delete
                          :actor handler-user-id}
                         (build-application-view [dummy-created-event])))))
  (testing "applicant cannot delete submitted application"
    (is (= {:errors [{:type :forbidden}]}
           (fail-command {:type :application.command/delete
                          :actor applicant-user-id}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "applicant can delete draft application"
    (is (= {:event/type :application.event/deleted
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id}
           (ok-command {:type :application.command/delete
                        :actor applicant-user-id}
                       (build-application-view [dummy-created-event]))))))

(deftest test-handle-command
  (let [application (build-application-view [dummy-created-event])
        command {:application-id 123 :time (DateTime. 1000)
                 :type :application.command/save-draft
                 :field-values []
                 :actor applicant-user-id}]
    (testing "executes command when user is authorized"
      (is (not (:errors (commands/handle-command command application command-injections)))))
    (testing "fails when command fails validation"
      (is (thrown-with-msg? ExceptionInfo #"Value does not match schema"
                            (commands/handle-command (assoc command :time 3) application {}))))
    (testing "fails when user is not authorized"
      ;; the permission checks should happen before executing the command handler
      ;; and only depend on the roles and permissions
      (let [application (permissions/remove-role-from-user application :applicant "applicant")
            result (commands/handle-command command application command-injections)]
        (is (= {:errors [{:type :forbidden}]} result))))))

(deftest test-redact-attachments-command
  (testing "fail if redacted attachments is empty"
    (is (= {:errors [{:type :empty-redact-attachments}]}
           (fail-command {:type :application.command/redact-attachments
                          :actor handler-user-id
                          :comment "should fail"
                          :public false
                          :redacted-attachments []
                          :attachments []}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event])))))
  (testing "fail if redacted attachment does not exist"
    (is (= {:errors [{:type :invalid-redact-attachments
                      :attachments [1]}]}
           (fail-command {:type :application.command/redact-attachments
                          :actor handler-user-id
                          :comment "should fail"
                          :public false
                          :redacted-attachments [{:attachment/id 1}]
                          :attachments []}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event]
                                                 (-> application-injections
                                                     (assoc :get-attachments-for-application {app-id []})))))))
  (testing "fail if replacing attachment does not exist"
    (is (= {:errors [{:type :invalid-attachments
                      :attachments [2]}]}
           (fail-command {:type :application.command/redact-attachments
                          :actor handler-user-id
                          :comment "should fail"
                          :public false
                          :redacted-attachments [{:attachment/id 1}]
                          :attachments [{:attachment/id 2}]}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  dummy-remarked-event]
                                                 (-> application-injections
                                                     (assoc :get-attachment-metadata {})))))))
  (testing "handler can redact"
    (is (= {:event/type :application.event/attachments-redacted
            :event/time test-time
            :event/actor handler-user-id
            :event/attachments [{:attachment/id 4}]
            :event/redacted-attachments [{:attachment/id 1}
                                         {:attachment/id 3}
                                         {:attachment/id 5}]
            :application/id app-id
            :application/comment "i've got the power"
            :application/public false}
           (ok-command {:type :application.command/redact-attachments
                        :actor handler-user-id
                        :comment "i've got the power"
                        :public false
                        :redacted-attachments [{:attachment/id 1}
                                               {:attachment/id 3}
                                               {:attachment/id 5}]
                        :attachments [{:attachment/id 4}]}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                (merge dummy-remarked-event {:event/actor handler-user-id
                                                                             :event/attachments [{:attachment/id 1}]})
                                                dummy-review-requested-event
                                                (merge dummy-remarked-event {:event/actor reviewer-user-id
                                                                             :event/attachments [{:attachment/id 3}]})
                                                dummy-decision-requested-event
                                                (merge dummy-remarked-event {:event/actor decider-user-id
                                                                             :event/attachments [{:attachment/id 5}]})])))))
  (testing "fails when handler redacts decider attachment in decider workflow"
    (is (= {:errors [{:type :forbidden-redact-attachments
                      :attachments [5]}]}
           (fail-command {:type :application.command/redact-attachments
                          :actor handler-user-id
                          :comment ""
                          :public false
                          :redacted-attachments [{:attachment/id 5}]
                          :attachments []}
                         (build-application-view [(merge dummy-created-event {:workflow/id 3
                                                                              :workflow/type :workflow/decider})
                                                  dummy-submitted-event
                                                  dummy-decision-requested-event
                                                  (merge dummy-remarked-event {:event/actor decider-user-id
                                                                               :event/attachments [{:attachment/id 5}]})])))))
  (testing "reviewer can redact"
    (is (= {:event/type :application.event/attachments-redacted
            :event/time test-time
            :event/actor reviewer-user-id
            :event/attachments []
            :event/redacted-attachments [{:attachment/id 3}]
            :application/id app-id
            :application/comment "accidental upload"
            :application/public false}
           (ok-command {:type :application.command/redact-attachments
                        :actor reviewer-user-id
                        :comment "accidental upload"
                        :public false
                        :redacted-attachments [{:attachment/id 3}]
                        :attachments []}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-review-requested-event
                                                (merge dummy-remarked-event {:event/actor reviewer-user-id
                                                                             :event/attachments [{:attachment/id 3}]})])))))
  (testing "fails when reviewer redacts other users attachments"
    (is (= {:errors [{:type :forbidden-redact-attachments
                      :attachments [1 5]}]}
           (fail-command {:type :application.command/redact-attachments
                          :actor reviewer-user-id
                          :comment ""
                          :public false
                          :redacted-attachments [{:attachment/id 1}
                                                 {:attachment/id 3}
                                                 {:attachment/id 5}]
                          :attachments []}
                         (build-application-view [dummy-created-event
                                                  dummy-submitted-event
                                                  (merge dummy-remarked-event {:event/actor handler-user-id
                                                                               :event/attachments [{:attachment/id 1}]})
                                                  dummy-review-requested-event
                                                  (merge dummy-remarked-event {:event/actor reviewer-user-id
                                                                               :event/attachments [{:attachment/id 3}]})
                                                  dummy-decision-requested-event
                                                  (merge dummy-remarked-event {:event/actor decider-user-id
                                                                               :event/attachments [{:attachment/id 5}]})])))))
  (testing "decider can redact"
    (is (= {:event/type :application.event/attachments-redacted
            :event/time test-time
            :event/actor decider-user-id
            :event/attachments []
            :event/redacted-attachments [{:attachment/id 5}]
            :application/id app-id
            :application/comment ""
            :application/public false}
           (ok-command {:type :application.command/redact-attachments
                        :actor decider-user-id
                        :comment ""
                        :public false
                        :redacted-attachments [{:attachment/id 5}]
                        :attachments []}
                       (build-application-view [dummy-created-event
                                                dummy-submitted-event
                                                dummy-decision-requested-event
                                                (merge dummy-remarked-event {:event/actor decider-user-id
                                                                             :event/attachments [{:attachment/id 5}]})])))))
  (testing "fails when decider redacts other users attachments"
    (doseq [created-event [dummy-created-event
                           (merge dummy-created-event {:workflow/id 3 :workflow/type :workflow/decider})]]
      (is (= {:errors [{:type :forbidden-redact-attachments
                        :attachments [1 3]}]}
             (fail-command {:type :application.command/redact-attachments
                            :actor decider-user-id
                            :comment ""
                            :public false
                            :redacted-attachments [{:attachment/id 1}
                                                   {:attachment/id 3}
                                                   {:attachment/id 5}]
                            :attachments []}
                           (build-application-view [created-event ; default and decider workflow
                                                    dummy-submitted-event
                                                    (merge dummy-remarked-event {:event/actor handler-user-id
                                                                                 :event/attachments [{:attachment/id 1}]})
                                                    dummy-review-requested-event
                                                    (merge dummy-remarked-event {:event/actor reviewer-user-id
                                                                                 :event/attachments [{:attachment/id 3}]})
                                                    dummy-decision-requested-event
                                                    (merge dummy-remarked-event {:event/actor decider-user-id
                                                                                 :event/attachments [{:attachment/id 5}]})])))))))

