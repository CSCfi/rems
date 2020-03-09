(ns rems.application.test-commands
  (:require [clojure.test :refer :all]
            [rems.application.commands :as commands]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.form-validation :as form-validation]
            [rems.permissions :as permissions]
            [rems.util :refer [assert-ex getx]])
  (:import [clojure.lang ExceptionInfo]
           [java.util UUID]
           [org.joda.time DateTime]))

(def ^:private test-time (DateTime. 1000))
(def ^:private app-id 123)
(def ^:private applicant-user-id "applicant")
(def ^:private handler-user-id "assistant")
(def ^:private dummy-created-event {:event/type :application.event/created
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id
                                    :application/external-id "2000/123"
                                    :application/resources []
                                    :application/licenses []
                                    :form/id 1
                                    :workflow/id 1
                                    :workflow/type :workflow/default})
(def ^:private dummy-licenses {1 {:id 1
                                  :licensetype "link"
                                  :localizations {:en {:title "en title"
                                                       :textcontent "en link"
                                                       :attachment-id 1}
                                                  :fi {:title "fi title"
                                                       :textcontent "fi link"
                                                       :attachment-id 2}}}})
(def ^:private dummy-workflows {1 {:workflow {:handlers [{:userid handler-user-id
                                                          :name "user"
                                                          :email "user@example.com"}]}}})
(def ^:private dummy-forms {1 {:form/fields [{:field/id "1"
                                              :field/optional true
                                              :field/type :options
                                              :field/options [{:key "foo" :label "Foo"}
                                                              {:key "bar" :label "Bar"}]}
                                             {:field/id "2"
                                              :field/optional false
                                              :field/visibility {:visibility/type :only-if
                                                                 :visibility/field {:field/id "1"}
                                                                 :visibility/values ["foo"]}}]}})

(defn- dummy-get-catalogue-item [id]
  {:enabled true :archived false :expired false
   :id id :wfid 1 :formid 1})

(defn- dummy-get-config []
  {})

(def ^:private injections
  {:blacklisted? (constantly false)
   :get-workflow dummy-workflows
   :get-form-template dummy-forms
   :get-catalogue-item dummy-get-catalogue-item
   :get-config dummy-get-config
   :get-license dummy-licenses
   :get-user (fn [userid] {:userid userid})
   :get-users-with-role (constantly nil)
   :get-attachments-for-application (constantly nil)})

;; could rework tests to use model/build-application-view instead of this
(defn apply-events [application events]
  (events/validate-events events)
  (-> (reduce model/application-view application events)
      (model/enrich-with-injections injections)))

(defn- set-command-defaults [cmd]
  (-> cmd
      (assoc :time test-time)
      (cond-> (not= :application.command/create (:type cmd))
              (assoc :application-id app-id))))

(defn- fail-command
  ([application cmd]
   (fail-command application cmd nil))
  ([application cmd injections]
   (let [cmd (set-command-defaults cmd)
         result (commands/handle-command cmd application injections)]
     (assert-ex (:errors result) {:cmd cmd :result result})
     result)))

(defn- ok-command
  ([application cmd]
   (ok-command application cmd nil))
  ([application cmd injections]
   (let [cmd (set-command-defaults cmd)
         result (commands/handle-command cmd application injections)]
     (assert-ex (not (:errors result)) {:cmd cmd :result result})
     (let [events (:events result)]
       (events/validate-events events)
       ;; most tests expect only one event, so this avoids having to wrap the expectation to a list
       (if (= 1 (count events))
         (first events)
         events)))))

(defn- apply-command
  ([application cmd]
   (apply-command application cmd nil))
  ([application cmd injections]
   (apply-events application [(ok-command application cmd injections)])))

(defn- apply-commands
  ([application commands]
   (apply-commands application commands nil))
  ([application commands injections]
   (reduce (fn [app cmd] (apply-command app cmd injections))
           application commands)))

;;; Tests

(deftest test-create
  (let [cat-id 10
        cat-id2 20
        licence-id 30
        licence-id2 40
        form-id 50
        wf-id 60
        injections {:get-catalogue-item {cat-id {:id cat-id
                                                 :resid "res1"
                                                 :formid form-id
                                                 :wfid wf-id}
                                         cat-id2 {:id cat-id2
                                                  :resid "res2"
                                                  :formid form-id
                                                  :wfid wf-id}}
                    :get-catalogue-item-licenses {cat-id [{:id licence-id}]
                                                  cat-id2 [{:id licence-id2}]}
                    :get-workflow {wf-id {:workflow {:type :workflow/default}}}
                    :allocate-application-ids! (fn [^DateTime time]
                                                 {:application/id app-id
                                                  :application/external-id (str (.getYear time) "/1")})}]
    (testing "one resource"
      (is (= {:event/type :application.event/created
              :event/actor applicant-user-id
              :event/time (DateTime. 1000)
              :application/id app-id
              :application/external-id "1970/1"
              :application/resources [{:catalogue-item/id cat-id
                                       :resource/ext-id "res1"}]
              :application/licenses [{:license/id licence-id}]
              :form/id form-id
              :workflow/id wf-id
              :workflow/type :workflow/default}
             (ok-command nil {:type :application.command/create
                              :actor applicant-user-id
                              :catalogue-item-ids [cat-id]}
                         injections))))

    (testing "multiple resources"
      (is (= {:event/type :application.event/created
              :event/actor applicant-user-id
              :event/time (DateTime. 1000)
              :application/id app-id
              :application/external-id "1970/1"
              :application/resources [{:catalogue-item/id cat-id
                                       :resource/ext-id "res1"}
                                      {:catalogue-item/id cat-id2
                                       :resource/ext-id "res2"}]
              :application/licenses [{:license/id licence-id}
                                     {:license/id licence-id2}]
              :form/id form-id
              :workflow/id wf-id
              :workflow/type :workflow/default}
             (ok-command nil {:type :application.command/create
                              :actor applicant-user-id
                              :catalogue-item-ids [cat-id cat-id2]}
                         injections))))

    (testing "error: zero catalogue items"
      (is (= {:errors [{:type :must-not-be-empty
                        :key :catalogue-item-ids}]}
             (fail-command nil {:type :application.command/create
                                :actor applicant-user-id
                                :catalogue-item-ids []}
                           injections))))

    (testing "error: non-existing catalogue items"
      (is (= {:errors [{:type :invalid-catalogue-item
                        :catalogue-item-id 999999}]}
             (fail-command nil {:type :application.command/create
                                :actor applicant-user-id
                                :catalogue-item-ids [999999]}
                           injections))))

    (testing "error: catalogue items with different forms"
      (let [injections (assoc-in injections [:get-catalogue-item cat-id2 :formid] 666)]
        (is (= {:errors [{:type :unbundlable-catalogue-items
                          :catalogue-item-ids [10 20]}]}
               (fail-command nil {:type :application.command/create
                                  :actor applicant-user-id
                                  :catalogue-item-ids [cat-id cat-id2]}
                             injections)))))

    (testing "error: catalogue items with different workflows"
      (let [injections (assoc-in injections [:get-catalogue-item cat-id2 :wfid] 666)]
        (is (= {:errors [{:type :unbundlable-catalogue-items
                          :catalogue-item-ids [10 20]}]}
               (fail-command nil {:type :application.command/create
                                  :actor applicant-user-id
                                  :catalogue-item-ids [cat-id cat-id2]}
                             injections)))))

    (testing "cannot execute the create command for an existing application"
      (let [allocated-new-ids? (atom false)
            injections (assoc injections :allocate-application-ids! (fn [_]
                                                                      (reset! allocated-new-ids? true)))
            application (apply-events nil [dummy-created-event])]
        (is (= {:errors [{:type :forbidden}]}
               (fail-command application {:type :application.command/create
                                          :actor applicant-user-id
                                          :catalogue-item-ids [cat-id]}
                             injections)))
        (is (false? @allocated-new-ids?)
            "should not allocate new IDs")))))

(deftest test-save-draft
  (let [application (apply-events nil [dummy-created-event])]
    (testing "saves a draft"
      (is (= {:event/type :application.event/draft-saved
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id
              :application/field-values {"1" "foo" "2" "bar"}}
             (ok-command application
                         {:type :application.command/save-draft
                          :actor applicant-user-id
                          :field-values [{:field "1" :value "foo"}
                                         {:field "2" :value "bar"}]}))))
    (testing "only the applicant can save a draft"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/save-draft
                            :actor "non-applicant"
                            :field-values [{:field "1" :value "foo"}
                                           {:field "2" :value "bar"}]})
             (fail-command application
                           {:type :application.command/save-draft
                            :actor handler-user-id
                            :field-values [{:field "1" :value "foo"}
                                           {:field "2" :value "bar"}]}))))
    (testing "draft cannot be updated after submitting"
      (let [application (apply-events application
                                      [{:event/type :application.event/submitted
                                        :event/time test-time
                                        :event/actor applicant-user-id
                                        :application/id app-id}])]
        (is (= {:errors [{:type :forbidden}]}
               (fail-command application
                             {:type :application.command/save-draft
                              :actor applicant-user-id
                              :field-values [{:field "1" :value "updated"}]})))))
    (testing "draft can be updated after returning it to applicant"
      (let [application (apply-events application
                                      [{:event/type :application.event/submitted
                                        :event/time test-time
                                        :event/actor applicant-user-id
                                        :application/id app-id}
                                       {:event/type :application.event/returned
                                        :event/time test-time
                                        :event/actor handler-user-id
                                        :application/id app-id
                                        :application/comment ""}])]
        (is (= {:event/type :application.event/draft-saved
                :event/time test-time
                :event/actor applicant-user-id
                :application/id app-id
                :application/field-values {"1" "updated"}}
               (ok-command application
                           {:type :application.command/save-draft
                            :actor applicant-user-id
                            :field-values [{:field "1" :value "updated"}]})))))))

(deftest test-accept-licenses
  (let [application (apply-events nil [dummy-created-event])]
    (is (= {:event/type :application.event/licenses-accepted
            :event/time test-time
            :event/actor applicant-user-id
            :application/id app-id
            :application/accepted-licenses #{1 2}}
           (ok-command application
                       {:type :application.command/accept-licenses
                        :actor applicant-user-id
                        :accepted-licenses [1 2]})))))

(deftest test-add-licenses
  (let [application (apply-events nil [dummy-created-event
                                       {:event/type :application.event/submitted
                                        :event/time test-time
                                        :event/actor applicant-user-id
                                        :application/id app-id}])]
    (is (= {:event/type :application.event/licenses-added
            :event/time test-time
            :event/actor handler-user-id
            :event/attachments []
            :application/id app-id
            :application/comment "comment"
            :application/licenses [{:license/id 1} {:license/id 2}]}
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
  (let [cat-1 1
        cat-2-other-license 2
        cat-3-other-workflow 3
        cat-4-other-form 4
        form-1 1
        form-2 2
        wf-1 1
        wf-2 2
        license-1 1
        license-2 2
        application (apply-events nil [dummy-created-event])
        submitted-application (apply-events application
                                            [{:event/type :application.event/submitted
                                              :event/time test-time
                                              :event/actor applicant-user-id
                                              :application/id app-id}])
        approved-application (apply-events submitted-application
                                           [{:event/type :application.event/approved
                                             :event/time test-time
                                             :event/actor handler-user-id
                                             :application/comment "This is good"
                                             :application/id app-id}])
        injections {:get-catalogue-item
                    {cat-1 {:id cat-1 :resid "res1" :formid form-1 :wfid wf-1}
                     cat-2-other-license {:id cat-2-other-license :resid "res2" :formid form-1 :wfid wf-1}
                     cat-3-other-workflow {:id cat-3-other-workflow :resid "res3" :formid form-1 :wfid wf-2}
                     cat-4-other-form {:id cat-4-other-form :resid "res4" :formid form-2 :wfid wf-1}}
                    :get-catalogue-item-licenses
                    {cat-1 [{:id license-1}]
                     cat-2-other-license [{:id license-2}]
                     cat-3-other-workflow [{:id license-1}]
                     cat-4-other-form [{:id license-1}]}}]

    (testing "applicant can add resources to a draft application"
      (is (= {:event/type :application.event/resources-changed
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id
              :application/resources [{:catalogue-item/id cat-1 :resource/ext-id "res1"}
                                      {:catalogue-item/id cat-2-other-license :resource/ext-id "res2"}]
              :application/licenses [{:license/id license-1}
                                     {:license/id license-2}]}
             (ok-command application
                         {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids [cat-1 cat-2-other-license]}
                         injections))))

    (testing "applicant cannot add resources to a submitted application"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command submitted-application
                           {:type :application.command/change-resources
                            :actor applicant-user-id
                            :catalogue-item-ids [cat-1 cat-2-other-license]}
                           injections))))

    (testing "applicant cannot add resources with different workflow"
      (is (= {:errors [{:type :unbundlable-catalogue-items
                        :catalogue-item-ids [cat-1 cat-3-other-workflow]}]}
             (fail-command application
                           {:type :application.command/change-resources
                            :actor applicant-user-id
                            :catalogue-item-ids [cat-1 cat-3-other-workflow]}
                           injections))))

    (testing "applicant cannot add resources with different form"
      (is (= {:errors [{:type :unbundlable-catalogue-items
                        :catalogue-item-ids [cat-1 cat-4-other-form]}]}
             (fail-command application
                           {:type :application.command/change-resources
                            :actor applicant-user-id
                            :catalogue-item-ids [cat-1 cat-4-other-form]}
                           injections))))

    (testing "applicant can replace resources"
      (is (= {:event/type :application.event/resources-changed
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id
              :application/resources [{:catalogue-item/id cat-2-other-license :resource/ext-id "res2"}]
              :application/licenses [{:license/id license-2}]}
             (ok-command application
                         {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids [cat-2-other-license]}
                         injections))))

    (testing "applicant cannot replace resources with different workflow"
      (is (= {:errors [{:type :changes-original-workflow :workflow/id wf-1 :ids [wf-2]}]}
             (fail-command application
                           {:type :application.command/change-resources
                            :actor applicant-user-id
                            :catalogue-item-ids [cat-3-other-workflow]}
                           injections))))

    (testing "applicant cannot replace resources with different form"
      (is (= {:errors [{:type :changes-original-form :form/id form-1 :ids [form-2]}]}
             (fail-command application
                           {:type :application.command/change-resources
                            :actor applicant-user-id
                            :catalogue-item-ids [cat-4-other-form]}
                           injections))))

    (testing "handler can add resources to a submitted application"
      (is (= {:event/type :application.event/resources-changed
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id
              :application/comment "Changed these for you"
              :application/resources [{:catalogue-item/id cat-1 :resource/ext-id "res1"}
                                      {:catalogue-item/id cat-2-other-license :resource/ext-id "res2"}]
              :application/licenses [{:license/id license-1}
                                     {:license/id license-2}]}
             (ok-command submitted-application
                         {:type :application.command/change-resources
                          :actor handler-user-id
                          :comment "Changed these for you"
                          :catalogue-item-ids [cat-1 cat-2-other-license]}
                         injections)))

      (testing "- even with a different workflow or form"
        (is (= {:event/type :application.event/resources-changed
                :event/time test-time
                :event/actor handler-user-id
                :application/id app-id
                :application/comment "Changed these for you"
                :application/resources [{:catalogue-item/id cat-1 :resource/ext-id "res1"}
                                        {:catalogue-item/id cat-3-other-workflow :resource/ext-id "res3"}
                                        {:catalogue-item/id cat-4-other-form :resource/ext-id "res4"}]
                :application/licenses [{:license/id license-1}]}
               (ok-command submitted-application
                           {:type :application.command/change-resources
                            :actor handler-user-id
                            :comment "Changed these for you"
                            :catalogue-item-ids [cat-1 cat-3-other-workflow cat-4-other-form]}
                           injections)))))

    (testing "handler can add resources to an approved application"
      (is (= {:event/type :application.event/resources-changed
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id
              :application/comment "Changed these for you"
              :application/resources [{:catalogue-item/id cat-1 :resource/ext-id "res1"}
                                      {:catalogue-item/id cat-2-other-license :resource/ext-id "res2"}]
              :application/licenses [{:license/id license-1}
                                     {:license/id license-2}]}
             (ok-command approved-application
                         {:type :application.command/change-resources
                          :actor handler-user-id
                          :comment "Changed these for you"
                          :catalogue-item-ids [cat-1 cat-2-other-license]}
                         injections)))

      (testing "- even with different workflow or form"
        (is (= {:event/type :application.event/resources-changed
                :event/time test-time
                :event/actor handler-user-id
                :application/id app-id
                :application/comment "Changed these for you"
                :application/resources [{:catalogue-item/id cat-1 :resource/ext-id "res1"}
                                        {:catalogue-item/id cat-3-other-workflow :resource/ext-id "res3"}
                                        {:catalogue-item/id cat-4-other-form :resource/ext-id "res4"}]
                :application/licenses [{:license/id license-1}]}
               (ok-command approved-application
                           {:type :application.command/change-resources
                            :actor handler-user-id
                            :comment "Changed these for you"
                            :catalogue-item-ids [cat-1 cat-3-other-workflow cat-4-other-form]}
                           injections)))))

    (testing "the catalogue item must exist"
      (is (= {:errors [{:type :invalid-catalogue-item :catalogue-item-id 42}]}
             (fail-command application
                           {:type :application.command/change-resources
                            :actor applicant-user-id
                            :catalogue-item-ids [42]}
                           injections))))

    (testing "there must be at least one catalogue item"
      (is (= {:errors [{:type :must-not-be-empty :key :catalogue-item-ids}]}
             (fail-command application
                           {:type :application.command/change-resources
                            :actor applicant-user-id
                            :catalogue-item-ids []}))))))

(deftest test-submit
  (let [injections {:validate-fields form-validation/validate-fields
                    :get-form-template dummy-forms}
        created-event {:event/type :application.event/created
                       :event/time test-time
                       :event/actor applicant-user-id
                       :application/id app-id
                       :application/external-id "2000/123"
                       :application/resources [{:catalogue-item/id 10
                                                :resource/ext-id "urn:11"}
                                               {:catalogue-item/id 20
                                                :resource/ext-id "urn:21"}]
                       :application/licenses [{:license/id 1}]
                       :form/id 1
                       :workflow/id 50
                       :workflow/type :workflow/default}
        draft-saved-event {:event/type :application.event/draft-saved
                           :event/time test-time
                           :event/actor applicant-user-id
                           :application/id app-id
                           :application/field-values {"1" "foo"
                                                      "2" "bar"}}
        licenses-accepted-event {:event/type :application.event/licenses-accepted
                                 :event/time test-time
                                 :event/actor applicant-user-id
                                 :application/id app-id
                                 :application/accepted-licenses #{1}}
        submit-command {:type :application.command/submit
                        :actor applicant-user-id}
        application-no-licenses (apply-events nil [created-event draft-saved-event])
        application (apply-events application-no-licenses [licenses-accepted-event])]

    (testing "cannot submit a valid form if licenses are not accepted"
      (is (= {:errors [{:type :t.actions.errors/licenses-not-accepted}]}
             (fail-command application-no-licenses submit-command injections))))

    (testing "can submit a valid form when licenses are accepted"
      (is (= {:event/type :application.event/submitted
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id}
             (ok-command application submit-command injections))))

    (testing "required fields"
      (testing "1st field is optional and empty, 2nd field is required but invisible"
        (is (= {:event/type :application.event/submitted
                :event/time test-time
                :event/actor applicant-user-id
                :application/id app-id}
               (-> application
                   (apply-events [(-> draft-saved-event
                                      (assoc-in [:application/field-values "1"] "")
                                      (assoc-in [:application/field-values "2"] ""))])
                   (ok-command submit-command injections)))))
      (testing "1st field is given, 2nd field is required and visible but empty"
        (is (= {:errors [{:type :t.form.validation/required
                          :field-id "2"}]}
               (-> application
                   (apply-events [(assoc-in draft-saved-event [:application/field-values "2"] "")])
                   (fail-command submit-command injections)))))
      (testing "1st field is given, 2nd field is given"
        (is (= {:event/type :application.event/submitted
                :event/time test-time
                :event/actor applicant-user-id
                :application/id app-id}
               (-> application
                   (ok-command submit-command injections))))))

    (testing "cannot submit if catalogue item is disabled"
      (let [disabled (assoc-in application [:application/resources 1 :catalogue-item/enabled] false)]
        (is (= {:errors [{:type :t.actions.errors/disabled-catalogue-item, :catalogue-item-id 20}]}
               (fail-command disabled submit-command injections)))))

    (testing "non-applicant cannot submit"
      (let [application (apply-events application [(assoc licenses-accepted-event :event/actor "non-applicant")])]
        (is (= {:errors [{:type :forbidden}]}
               (fail-command application
                             (assoc submit-command :actor "non-applicant")
                             injections)))))

    (testing "cannot submit twice"
      (is (= {:errors [{:type :forbidden}]}
             (-> application
                 (apply-events [{:event/type :application.event/submitted
                                 :event/time test-time
                                 :event/actor applicant-user-id
                                 :application/id app-id}])
                 (fail-command submit-command injections)))))))

(deftest test-assign-external-id
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id}])]
    (testing "handler can assign id"
      (is (= {:event/type :application.event/external-id-assigned
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id
              :application/external-id "ext123"}
             (ok-command application
                         {:type :application.command/assign-external-id
                          :actor handler-user-id
                          :external-id "ext123"}))))
    (testing "applicant can't assign id"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/assign-external-id
                            :actor applicant-user-id
                            :external-id "ext123"}))))))

(deftest test-approve-or-reject
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id}])]
    (testing "approved successfully"
      (is (= {:event/type :application.event/approved
              :event/time test-time
              :event/actor handler-user-id
              :event/attachments []
              :application/id app-id
              :application/comment "fine"}
             (ok-command application
                         {:type :application.command/approve
                          :actor handler-user-id
                          :comment "fine"}))))
    (testing "rejected successfully"
      (is (= {:event/type :application.event/rejected
              :application/comment "bad"
              :event/time test-time
              :event/actor handler-user-id
              :event/attachments []
              :application/id app-id}
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
                                    :application/id app-id}])]
    (is (= {:event/type :application.event/returned
            :event/time test-time
            :event/actor handler-user-id
            :event/attachments []
            :application/id app-id
            :application/comment "ret"}
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
                                    :application/id app-id}
                                   {:event/type :application.event/approved
                                    :event/time test-time
                                    :event/actor handler-user-id
                                    :application/id app-id
                                    :application/comment ""}])]
    (is (= {:event/type :application.event/closed
            :event/time test-time
            :event/actor handler-user-id
            :event/attachments []
            :application/id app-id
            :application/comment "outdated"}
           (ok-command application
                       {:type :application.command/close
                        :actor handler-user-id
                        :comment "outdated"})))))

(deftest test-revoke
  (let [application (apply-events nil [dummy-created-event
                                       {:event/type :application.event/submitted
                                        :event/time test-time
                                        :event/actor applicant-user-id
                                        :application/id app-id}
                                       {:event/type :application.event/approved
                                        :event/time test-time
                                        :event/actor handler-user-id
                                        :application/id app-id
                                        :application/comment ""}])]
    (is (= {:event/type :application.event/revoked
            :event/time test-time
            :event/actor handler-user-id
            :event/attachments []
            :application/id app-id
            :application/comment "license violated"}
           (ok-command application
                       {:type :application.command/revoke
                        :actor handler-user-id
                        :comment "license violated"}
                       injections)))))

(deftest test-decision
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id}])
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
    (let [event (ok-command application
                            {:type :application.command/request-decision
                             :actor handler-user-id
                             :deciders ["deity"]
                             :comment ""}
                            injections)
          request-id (:application/request-id event)
          requested (apply-events application [event])]
      (testing "decision requested successfully"
        (is (instance? UUID request-id))
        (is (= {:event/type :application.event/decision-requested
                :event/time test-time
                :event/actor handler-user-id
                :event/attachments []
                :application/id app-id
                :application/request-id request-id
                :application/deciders ["deity"]
                :application/comment ""}
               event)))
      (testing "only the requested user can decide"
        (is (= {:errors [{:type :forbidden}]}
               (fail-command requested
                             {:type :application.command/decide
                              :actor "deity2"
                              :decision :approved
                              :comment ""}
                             injections))))
      (let [event (ok-command requested
                              {:type :application.command/decide
                               :actor "deity"
                               :decision :approved
                               :comment ""}
                              injections)
            approved (apply-events requested [event])]
        (testing "decided approved successfully"
          (is (= {:event/type :application.event/decided
                  :event/time test-time
                  :event/actor "deity"
                  :event/attachments []
                  :application/id app-id
                  :application/request-id request-id
                  :application/decision :approved
                  :application/comment ""}
                 event)))
        (testing "cannot approve twice"
          (is (= {:errors [{:type :forbidden}]}
                 (fail-command approved
                               {:type :application.command/decide
                                :actor "deity"
                                :decision :approved
                                :comment ""}
                               injections)))))
      (let [event (ok-command requested
                              {:type :application.command/decide
                               :actor "deity"
                               :decision :rejected
                               :comment ""}
                              injections)
            rejected (apply-events requested [event])]
        (testing "decided rejected successfully"
          (is (= {:event/type :application.event/decided
                  :event/time test-time
                  :event/actor "deity"
                  :event/attachments []
                  :application/id app-id
                  :application/request-id request-id
                  :application/decision :rejected
                  :application/comment ""}
                 event)))
        (testing "cannot reject twice"
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
                                    :application/id app-id}
                                   {:event/type :application.event/member-added
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id
                                    :application/member {:userid "somebody"}}])
        injections {:valid-user? #{"member1" "member2" "somebody" applicant-user-id}}]
    (testing "handler can add members"
      (is (= {:event/type :application.event/member-added
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id
              :application/member {:userid "member1"}}
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
      (is (= {:event/type :application.event/member-invited
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id
              :application/member {:name "Member Applicant 1"
                                   :email "member1@applicants.com"}
              :invitation/token "very-secure"}
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
                                                    :application/id app-id}])]
        (is (= {:event/type :application.event/member-invited
                :event/time test-time
                :event/actor handler-user-id
                :application/id app-id
                :application/member {:name "Member Applicant 1"
                                     :email "member1@applicants.com"}
                :invitation/token "very-secure"}
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
                                    :application/id app-id}])]
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
                                    :application/id app-id
                                    :application/member {:name "Some Body" :email "somebody@applicants.com"}
                                    :invitation/token "very-secure"}])
        injections {:valid-user? #{"somebody" "somebody2" applicant-user-id}}]

    (testing "invited member can join draft"
      (is (= {:event/type :application.event/member-joined
              :event/time test-time
              :event/actor "somebody"
              :application/id app-id
              :invitation/token "very-secure"}
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
                                        :application/id app-id
                                        :application/member {:userid "somebody"}}])]
        (is (= {:errors [{:type :already-member :application-id app-id}]}
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
                                        :application/id app-id
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
                                    :application/id app-id}])]
      (testing "invited member can join submitted application"
        (is (= {:event/type :application.event/member-joined
                :event/actor "somebody"
                :event/time test-time
                :application/id app-id
                :invitation/token "very-secure"}
               (ok-command application
                           {:type :application.command/accept-invitation
                            :actor "somebody"
                            :token "very-secure"}
                           injections))))

      (let [closed (apply-events submitted
                                 [{:event/type :application.event/closed
                                   :event/time test-time
                                   :event/actor applicant-user-id
                                   :application/id app-id
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
                                    :application/id app-id}
                                   {:event/type :application.event/member-added
                                    :event/time test-time
                                    :event/actor handler-user-id
                                    :application/id app-id
                                    :application/member {:userid "somebody"}}])
        injections {:valid-user? #{"somebody" applicant-user-id handler-user-id}}]
    (testing "applicant can remove members"
      (is (= {:event/type :application.event/member-removed
              :event/time test-time
              :event/actor applicant-user-id
              :event/attachments []
              :application/id app-id
              :application/member {:userid "somebody"}
              :application/comment "some comment"}
             (ok-command application
                         {:type :application.command/remove-member
                          :actor applicant-user-id
                          :member {:userid "somebody"}
                          :comment "some comment"}
                         injections))))
    (testing "handler can remove members"
      (is (= {:event/type :application.event/member-removed
              :event/time test-time
              :event/actor handler-user-id
              :event/attachments []
              :application/id app-id
              :application/member {:userid "somebody"}
              :application/comment ""}
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
                                    :application/id app-id
                                    :application/member {:name "Some Body" :email "some@body.com"}
                                    :invitation/token "123456"}
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id}])
        injections {}]
    (testing "uninvite member by applicant"
      (is (= {:event/type :application.event/member-uninvited
              :event/time test-time
              :event/actor applicant-user-id
              :event/attachments []
              :application/id app-id
              :application/member {:name "Some Body" :email "some@body.com"}
              :application/comment ""}
             (ok-command application
                         {:type :application.command/uninvite-member
                          :actor applicant-user-id
                          :member {:name "Some Body" :email "some@body.com"}
                          :comment ""}
                         injections))))
    (testing "uninvite member by handler"
      (is (= {:event/type :application.event/member-uninvited
              :event/time test-time
              :event/actor handler-user-id
              :event/attachments []
              :application/id app-id
              :application/member {:name "Some Body" :email "some@body.com"}
              :application/comment ""}
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

(deftest test-review
  (let [reviewer "reviewer"
        reviewer2 "reviewer2"
        reviewer3 "reviewer3"
        application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id}])
        injections {:valid-user? #{reviewer reviewer2 reviewer3}}]
    (testing "required :valid-user? injection"
      (is (= {:errors [{:type :missing-injection :injection :valid-user?}]}
             (fail-command application
                           {:type :application.command/request-review
                            :actor handler-user-id
                            :reviewers [reviewer]
                            :comment ""}
                           {}))))
    (testing "reviewers must not be empty"
      (is (= {:errors [{:type :must-not-be-empty :key :reviewers}]}
             (fail-command application
                           {:type :application.command/request-review
                            :actor handler-user-id
                            :reviewers []
                            :comment ""}
                           {}))))
    (testing "reviewers must be a valid users"
      (is (= {:errors [{:type :t.form.validation/invalid-user :userid "invaliduser"}
                       {:type :t.form.validation/invalid-user :userid "invaliduser2"}]}
             (fail-command application
                           {:type :application.command/request-review
                            :actor handler-user-id
                            :reviewers ["invaliduser" reviewer "invaliduser2"]
                            :comment ""}
                           injections))))
    (testing "reviewing before ::request-review should fail"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/review
                            :actor reviewer
                            :comment ""}
                           injections))))
    (let [event-1 (ok-command application
                              {:type :application.command/request-review
                               :actor handler-user-id
                               :reviewers [reviewer reviewer2]
                               :comment ""}
                              injections)
          request-id-1 (:application/request-id event-1)
          application (apply-events application [event-1])
          ;; Make a new request that should partly override previous
          event-2 (ok-command application
                              {:type :application.command/request-review
                               :actor handler-user-id
                               :reviewers [reviewer]
                               :comment ""}
                              injections)
          request-id-2 (:application/request-id event-2)
          application (apply-events application [event-2])]
      (testing "review requested successfully"
        (is (instance? UUID request-id-1))
        (is (= {:event/type :application.event/review-requested
                :application/request-id request-id-1
                :application/reviewers [reviewer reviewer2]
                :application/comment ""
                :event/attachments []
                :event/time test-time
                :event/actor handler-user-id
                :application/id app-id}
               event-1))
        (is (instance? UUID request-id-2))
        (is (= {:event/type :application.event/review-requested
                :application/request-id request-id-2
                :application/reviewers [reviewer]
                :application/comment ""
                :event/attachments []
                :event/time test-time
                :event/actor handler-user-id
                :application/id app-id}
               event-2)))
      (testing "only the requested reviewer can review"
        (is (= {:errors [{:type :forbidden}]}
               (fail-command application
                             {:type :application.command/review
                              :actor reviewer3
                              :comment "..."}
                             injections))))
      (testing "reviews are linked to different requests"
        (is (= request-id-2
               (:application/request-id
                (ok-command application
                            {:type :application.command/review
                             :actor reviewer
                             :comment "..."}
                            injections))))
        (is (= request-id-1
               (:application/request-id
                (ok-command application
                            {:type :application.command/review
                             :actor reviewer2
                             :comment "..."}
                            injections)))))
      (let [event (ok-command application
                              {:type :application.command/review
                               :actor reviewer
                               :comment "..."}
                              injections)
            application (apply-events application [event])]
        (testing "reviewed succesfully"
          (is (= {:event/type :application.event/reviewed
                  :event/time test-time
                  :event/actor reviewer
                  :event/attachments []
                  :application/id app-id
                  :application/request-id request-id-2
                  :application/comment "..."}
                 event)))
        (testing "cannot review twice"
          (is (= {:errors [{:type :forbidden}]}
                 (fail-command application
                               {:type :application.command/review
                                :actor reviewer
                                :comment "..."}
                               injections))))
        (testing "other reviewer can still review"
          (is (= {:event/type :application.event/reviewed
                  :event/time test-time
                  :event/actor reviewer2
                  :event/attachments []
                  :application/id app-id
                  :application/request-id request-id-1
                  :application/comment "..."}
                 (ok-command application
                             {:type :application.command/review
                              :actor reviewer2
                              :comment "..."}
                             injections))))))))

(deftest test-remark
  (let [reviewer "reviewer"
        application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id}])
        valid-attachment 1234
        wrong-application-attachment 1235
        wrong-user-attachment 1236
        unknown-attachment 1237
        injections {:valid-user? #{reviewer}
                    :get-attachment-metadata
                    {valid-attachment {:application/id (:application/id application)
                                       :attachment/id valid-attachment
                                       :attachment/user handler-user-id}
                     wrong-application-attachment {:application/id (inc (:application/id application))
                                                   :attachment/id wrong-application-attachment
                                                   :attachment/user handler-user-id}
                     wrong-user-attachment {:application/id (:application/id application)
                                            :attachment/id wrong-user-attachment
                                            :attachment/user "carl"}}}]
    (testing "handler can remark"
      (let [event (ok-command application
                              {:type :application.command/remark
                               :actor handler-user-id
                               :comment "handler's remark"
                               :attachments [valid-attachment]
                               :public false}
                              injections)
            application (apply-events application [event])]
        (is (= {:event/type :application.event/remarked
                :event/time test-time
                :event/actor handler-user-id
                :application/id app-id
                :application/comment "handler's remark"
                :application/public false
                :event/attachments [valid-attachment]}
               event))))
    (testing "invalid attachments"
      (is (= {:errors [{:type :invalid-attachments
                        :attachments [wrong-application-attachment wrong-user-attachment unknown-attachment]}]}
             (fail-command application
                           {:type :application.command/remark
                            :actor handler-user-id
                            :comment "handler's remark"
                            :attachments [valid-attachment
                                          wrong-application-attachment wrong-user-attachment unknown-attachment]
                            :public false}
                           injections))))
    (testing "applicants cannot remark"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/remark
                            :actor applicant-user-id
                            :comment ""
                            :public false}
                           injections))))
    (testing "reviewer cannot remark before becoming reviewer"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/remark
                            :actor reviewer
                            :comment ""
                            :public false}
                           injections))))
    (let [event-1 (ok-command application
                              {:type :application.command/request-review
                               :actor handler-user-id
                               :reviewers [reviewer]
                               :comment ""}
                              injections)
          application (apply-events application [event-1])
          event-2 (ok-command application
                              {:type :application.command/remark
                               :actor reviewer
                               :comment "first remark"
                               :public false}
                              injections)
          application (apply-events application [event-2])]
      (testing "reviewer can remark before"
        (is (= {:event/type :application.event/remarked
                :event/time test-time
                :event/actor reviewer
                :event/attachments []
                :application/id app-id
                :application/comment "first remark"
                :application/public false}
               event-2))
        (let [event-1 (ok-command application
                                  {:type :application.command/review
                                   :actor reviewer
                                   :comment "..."}
                                  injections)
              application (apply-events application [event-1])
              event-2 (ok-command application
                                  {:type :application.command/remark
                                   :actor reviewer
                                   :comment "second remark"
                                   :public false}
                                  injections)
              application (apply-events application [event-2])]
          (testing "and after reviewing"
            (is (= {:event/type :application.event/remarked
                    :event/time test-time
                    :event/actor reviewer
                    :event/attachments []
                    :application/id app-id
                    :application/comment "second remark"
                    :application/public false}
                   event-2))))))))

(deftest test-copy-as-new
  (let [old-app-id 100
        new-app-id 200
        created-event {:event/type :application.event/created
                       :event/time test-time
                       :event/actor applicant-user-id
                       :application/id old-app-id
                       :application/external-id "2018/55"
                       :application/resources [{:catalogue-item/id 10
                                                :resource/ext-id "urn:11"}
                                               {:catalogue-item/id 20
                                                :resource/ext-id "urn:21"}]
                       :application/licenses []
                       :form/id 40
                       :workflow/id 50
                       :workflow/type :workflow/default}
        injections {:get-catalogue-item {10 {:id 10
                                             :resid "urn:11"
                                             :formid 40
                                             :wfid 50}
                                         20 {:id 20
                                             :resid "urn:21"
                                             :formid 40
                                             :wfid 50}}
                    :get-catalogue-item-licenses {10 []
                                                  20 []}
                    :get-workflow {50 {:workflow {:type :workflow/default}}}
                    :allocate-application-ids! (fn [_time]
                                                 {:application/id new-app-id
                                                  :application/external-id "2019/66"})}
        application (apply-events nil [created-event
                                       {:event/type :application.event/draft-saved
                                        :event/time test-time
                                        :event/actor applicant-user-id
                                        :application/id old-app-id
                                        :application/field-values {"1" "foo" "2" "bar"}}])]
    (testing "creates a new application with the same form answers"
      (is (= [{:event/type :application.event/created
               :event/time test-time
               :event/actor applicant-user-id
               :application/id new-app-id
               :application/external-id "2019/66"
               :application/resources [{:catalogue-item/id 10
                                        :resource/ext-id "urn:11"}
                                       {:catalogue-item/id 20
                                        :resource/ext-id "urn:21"}]
               :application/licenses []
               :form/id 40
               :workflow/id 50
               :workflow/type :workflow/default}
              {:event/type :application.event/draft-saved
               :event/time test-time
               :event/actor applicant-user-id
               :application/id new-app-id
               :application/field-values {"1" "foo" "2" "bar"}}
              {:event/type :application.event/copied-from
               :event/time test-time
               :event/actor applicant-user-id
               :application/id new-app-id
               :application/copied-from {:application/id old-app-id
                                         :application/external-id "2018/55"}}
              {:event/type :application.event/copied-to
               :event/time test-time
               :event/actor applicant-user-id
               :application/id old-app-id
               :application/copied-to {:application/id new-app-id
                                       :application/external-id "2019/66"}}]
             (ok-command application
                         {:type :application.command/copy-as-new
                          :actor applicant-user-id}
                         injections))))))

(deftest test-handle-command
  (let [application (model/application-view nil {:event/type :application.event/created
                                                 :event/actor "applicant"
                                                 :workflow/type :workflow/default})
        command {:application-id 123 :time (DateTime. 1000)
                 :type :application.command/save-draft
                 :field-values []
                 :actor "applicant"}]
    (testing "executes command when user is authorized"
      (is (not (:errors (commands/handle-command command application {})))))
    (testing "fails when command fails validation"
      (is (thrown-with-msg? ExceptionInfo #"Value does not match schema"
                            (commands/handle-command (assoc command :time 3) application {}))))
    (testing "fails when user is not authorized"
      ;; the permission checks should happen before executing the command handler
      ;; and only depend on the roles and permissions
      (let [application (permissions/remove-role-from-user application :applicant "applicant")
            result (commands/handle-command command application {})]
        (is (= {:errors [{:type :forbidden}]} result))))))
