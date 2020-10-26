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
(def ^:private new-app-id 456)
(def ^:private new-external-id "2019/66")
(def ^:private applicant-user-id "applicant")
(def ^:private handler-user-id "assistant")
(def ^:private dummy-created-event {:event/type :application.event/created
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id
                                    :application/external-id "2000/123"
                                    :application/resources []
                                    :application/licenses []
                                    :application/forms [{:form/id 1}]
                                    :workflow/id 1
                                    :workflow/type :workflow/default})
(def ^:private dummy-licenses
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

(defn- dummy-get-workflow [id]
  (getx {1 {:workflow {:type :workflow/default
                       :handlers [{:userid handler-user-id
                                   :name "user"
                                   :email "user@example.com"}]}}
         2 {:workflow {:type :workflow/default
                       :handlers [{:userid handler-user-id
                                   :name "user"
                                   :email "user@example.com"}]
                       :forms [{:form/id 3} {:form/id 4}]}}}
        id))
(defn- dummy-get-form-template [id]
  (getx {1 {:form/id 1
            :form/fields [{:field/id "1"
                           :field/optional true
                           :field/visible true
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
                           :field/optional false}]}
         3 {:form/id 3
            :form/fields [{:field/id "text"
                           :field/type :text}
                          {:field/id "attachment"
                           :field/type :attachment}]}
         4 {:form/id 4
            :form/fields [{:field/id "text"
                           :field/type :text}]}}
        id))

(defn- dummy-get-catalogue-item [id]
  (when (< id 10000)
    (merge {:enabled true :archived false :expired false
            :id id :wfid 1 :formid 1}
           (getx {1 {:resid "res1"}
                  2 {:resid "res2"}
                  3 {:resid "res3"
                     :formid 2}
                  4 {:resid "res4"
                     :wfid 2}
                  5 {:resid "res5"
                     :formid 2
                     :wfid 2}}
                 id))))

(defn- dummy-get-catalogue-item-licenses [id]
  (getx {1 [{:id 1}]
         2 [{:id 2}]
         3 [{:id 1}
            {:id 2}
            {:id 3}]
         4 []
         5 []} id))

(defn- dummy-get-config []
  {})

(def allocated-new-ids? (atom false))
(def ^:private injections
  {:blacklisted? (constantly false)
   :get-workflow dummy-get-workflow
   :get-form-template dummy-get-form-template
   :get-catalogue-item dummy-get-catalogue-item
   :get-catalogue-item-licenses dummy-get-catalogue-item-licenses
   :get-config dummy-get-config
   :get-license dummy-licenses
   :get-user (fn [userid] {:userid userid})
   :get-users-with-role (constantly nil)
   :get-attachments-for-application (constantly nil)
   :allocate-application-ids! (fn [_time]
                                (reset! allocated-new-ids? true)
                                {:application/id new-app-id
                                 :application/external-id new-external-id})
   :copy-attachment! (fn [_new-app-id attachment-id]
                       (+ attachment-id 100))})

;; could rework tests to use model/build-application-view instead of this
(defn apply-events [application events]
  (events/validate-events events)
  (-> (reduce model/application-view application events)
      (model/enrich-with-injections injections)))

(defn- set-command-defaults [cmd]
  (cond-> cmd
    true
    (assoc :time test-time)

    (not= :application.command/create (:type cmd))
    (assoc :application-id app-id)))

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
           (ok-command nil {:type :application.command/create
                            :actor applicant-user-id
                            :catalogue-item-ids [1]}
                       injections))))
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
           (ok-command nil {:type :application.command/create
                            :actor applicant-user-id
                            :catalogue-item-ids [1 2]}
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
           (ok-command nil {:type :application.command/create
                            :actor applicant-user-id
                            :catalogue-item-ids [1 3]}
                       injections))))

  (testing "workflow form, multiple catalogue items with different forms"
    (is (= {:event/type :application.event/created
            :event/actor applicant-user-id
            :event/time (DateTime. 1000)
            :application/id new-app-id
            :application/external-id new-external-id
            :application/resources [{:catalogue-item/id 4 :resource/ext-id "res4"}
                                    {:catalogue-item/id 5 :resource/ext-id "res5"}]
            :application/licenses []
            :application/forms [{:form/id 3} {:form/id 4} {:form/id 1} {:form/id 2}]
            :workflow/id 2
            :workflow/type :workflow/default}
           (ok-command nil {:type :application.command/create
                            :actor applicant-user-id
                            :catalogue-item-ids [4 5]}
                       injections))))

  (testing "error: catalogue items with different workflows"
    (is (= {:errors [{:type :unbundlable-catalogue-items
                      :catalogue-item-ids [1 4]}]}
           (fail-command nil {:type :application.command/create
                              :actor applicant-user-id
                              :catalogue-item-ids [1 4]}
                         injections))))

  (testing "cannot execute the create command for an existing application"
    (reset! allocated-new-ids? false)
    (let [application (apply-events nil [dummy-created-event])]
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application {:type :application.command/create
                                        :actor applicant-user-id
                                        :catalogue-item-ids [1]}
                           injections)))
      (is (false? @allocated-new-ids?) "should not allocate new IDs"))))

(deftest test-save-draft
  (let [application (apply-events nil [dummy-created-event])]
    (testing "saves a draft"
      (is (= {:event/type :application.event/draft-saved
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id
              :application/field-values [{:form 1 :field "1" :value "foo"}
                                         {:form 1 :field "2" :value "bar"}]}
             (ok-command application
                         {:type :application.command/save-draft
                          :actor applicant-user-id
                          :field-values [{:form 1 :field "1" :value "foo"}
                                         {:form 1 :field "2" :value "bar"}]}))))
    (testing "saves a draft"
      (is (= {:event/type :application.event/draft-saved
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id
              :application/field-values [{:form 1 :field "1" :value "foo"}
                                         {:form 1 :field "2" :value "bar"}]}
             (ok-command application
                         {:type :application.command/save-draft
                          :actor applicant-user-id
                          :field-values [{:form 1 :field "1" :value "foo"}
                                         {:form 1 :field "2" :value "bar"}]}))))

    (testing "does not save a draft when validations fail"
      (is (= {:errors [{:field-id "1", :type :t.form.validation/invalid-value :form-id 1}]}
             (fail-command application
                           {:type :application.command/save-draft
                            :actor applicant-user-id
                            :field-values [{:form 1 :field "1" :value "nonexistent_option"}]}))))

    (testing "only the applicant can save a draft"
      (is (= {:errors [{:type :forbidden}]}
             (fail-command application
                           {:type :application.command/save-draft
                            :actor "non-applicant"
                            :field-values [{:form 1 :field "1" :value "foo"}
                                           {:form 1 :field "2" :value "bar"}]})
             (fail-command application
                           {:type :application.command/save-draft
                            :actor handler-user-id
                            :field-values [{:form 1 :field "1" :value "foo"}
                                           {:form 1 :field "2" :value "bar"}]}))))
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
                              :field-values [{:form 1 :field "1" :value "bar"}]})))))
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
                :application/field-values [{:form 1 :field "1" :value "bar"}]}
               (ok-command application
                           {:type :application.command/save-draft
                            :actor applicant-user-id
                            :field-values [{:form 1 :field "1" :value "bar"}]})))))))

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
              :application/forms [{:form/id 1}]
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

    (testing "applicant can add resources with different form"
      (is (= {:event/type :application.event/resources-changed
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id
              :application/forms [{:form/id 1} {:form/id 2}]
              :application/resources [{:catalogue-item/id cat-1 :resource/ext-id "res1"}
                                      {:catalogue-item/id cat-4-other-form :resource/ext-id "res4"}]
              :application/licenses [{:license/id license-1}]}
             (ok-command application
                         {:type :application.command/change-resources
                          :actor applicant-user-id
                          :catalogue-item-ids [cat-1 cat-4-other-form]}
                         injections))))

    (testing "applicant can replace resources"
      (is (= {:event/type :application.event/resources-changed
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id
              :application/forms [{:form/id 1}]
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

    (testing "applicant can replace resources with different form"
      (is (= {:event/type :application.event/resources-changed
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id
              :application/forms [{:form/id 2}]
              :application/resources [{:catalogue-item/id cat-4-other-form :resource/ext-id "res4"}]
              :application/licenses [{:license/id license-1}]}
             (ok-command application
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
              :application/forms [{:form/id 1}]
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
                :application/forms [{:form/id 1} {:form/id 2}]
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
              :application/forms [{:form/id 1}]
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
                :application/forms [{:form/id 1} {:form/id 2}]
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
  (let [injections {:get-form-template dummy-get-form-template}
        created-event {:event/type :application.event/created
                       :event/time test-time
                       :event/actor applicant-user-id
                       :application/id app-id
                       :application/external-id "2000/123"
                       :application/resources [{:catalogue-item/id 1
                                                :resource/ext-id "res1"}
                                               {:catalogue-item/id 2
                                                :resource/ext-id "res2"}]
                       :application/licenses [{:license/id 1}]
                       :application/forms [{:form/id 1}]
                       :workflow/id 1
                       :workflow/type :workflow/default}
        draft-saved-event {:event/type :application.event/draft-saved
                           :event/time test-time
                           :event/actor applicant-user-id
                           :application/id app-id
                           :application/field-values [{:form 1 :field "1" :value "foo"}
                                                      {:form 1 :field "2" :value "bar"}]}
        licenses-accepted-event {:event/type :application.event/licenses-accepted
                                 :event/time test-time
                                 :event/actor applicant-user-id
                                 :application/id app-id
                                 :application/accepted-licenses #{1}}
        submit-command {:type :application.command/submit
                        :actor applicant-user-id}
        application-no-licenses (apply-events nil [created-event draft-saved-event])
        application (apply-events application-no-licenses [licenses-accepted-event])
        created-event2 (assoc created-event :application/forms [{:form/id 1} {:form/id 2}])
        draft-saved-event2 (update draft-saved-event :application/field-values conj {:form 2 :field "1" :value "baz"})
        application2 (apply-events nil [created-event2 draft-saved-event2 licenses-accepted-event])]

    (testing "cannot submit a valid form if licenses are not accepted"
      (is (= {:errors [{:type :t.actions.errors/licenses-not-accepted}]}
             (fail-command application-no-licenses submit-command injections))))

    (testing "can submit a valid form when licenses are accepted"
      (is (= {:event/type :application.event/submitted
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id}
             (ok-command application submit-command injections))))

    (testing "can submit two valid forms when licenses are accepted"
      (is (= {:event/type :application.event/submitted
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id}
             (ok-command application2 submit-command injections))))

    (testing "required fields"
      (testing "1st field is optional and empty, 2nd field is required but invisible"
        (is (= {:event/type :application.event/submitted
                :event/time test-time
                :event/actor applicant-user-id
                :application/id app-id}
               (-> application
                   (apply-events [(assoc draft-saved-event :application/field-values [{:form 1 :field "1" :value ""}
                                                                                      {:form 1 :field "2" :value ""}])])
                   (ok-command submit-command injections)))))
      (testing "1st field is given, 2nd field is required and visible but empty"
        (is (= {:errors [{:type :t.form.validation/required
                          :form-id 1
                          :field-id "2"}]}
               (-> application
                   (apply-events [(assoc draft-saved-event :application/field-values [{:form 1 :field "2" :value ""}])])
                   (fail-command submit-command injections)))))
      (testing "1st field is given, 2nd field is given"
        (is (= {:event/type :application.event/submitted
                :event/time test-time
                :event/actor applicant-user-id
                :application/id app-id}
               (-> application
                   (ok-command submit-command injections)))))

      (testing "cannot submit if one of two forms has required fields"
        (is (= {:errors [{:field-id "1" :type :t.form.validation/required :form-id 2}]}
               (-> application2
                   (apply-events [(assoc draft-saved-event2 :application/field-values [{:form 1 :field "1" :value "foo"}
                                                                                       {:form 1 :field "2" :value "bar"}
                                                                                       {:form 2 :field "1" :value ""}])])
                   (fail-command submit-command injections))))
        (is (= {:errors [{:field-id "2" :type :t.form.validation/required :form-id 1}]}
               (-> application2
                   (apply-events [(assoc draft-saved-event2 :application/field-values [{:form 1 :field "1" :value "foo"}
                                                                                       {:form 1 :field "2" :value ""}
                                                                                       {:form 2 :field "1" :value "baz"}])])
                   (fail-command submit-command injections))))))

    (testing "cannot submit draft if catalogue item is disabled"
      (let [disabled (assoc-in application [:application/resources 1 :catalogue-item/enabled] false)]
        (is (= {:errors [{:type :t.actions.errors/disabled-catalogue-item, :catalogue-item-id 2}]}
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

(deftest test-return-resubmit
  (testing "return"
    (let [application (apply-events nil
                                    [(assoc dummy-created-event
                                            :application/resources [{:catalogue-item/id 1
                                                                     :resource/ext-id "res1"}])
                                     {:event/type :application.event/submitted
                                      :event/time test-time
                                      :event/actor applicant-user-id
                                      :application/id app-id}])
          returned-event (ok-command application
                                     {:type :application.command/return
                                      :actor handler-user-id
                                      :comment "ret"})
          submit-injections {:get-form-template dummy-get-form-template}
          submit-command {:type :application.command/submit
                          :actor applicant-user-id}]
      (is (= {:event/type :application.event/returned
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id
              :application/comment "ret"}
             returned-event))
      (testing "resubmit"
        (let [returned (apply-events application [returned-event])]
          (is (= {:event/type :application.event/submitted
                  :event/time test-time
                  :event/actor applicant-user-id
                  :application/id app-id}
                 (ok-command returned submit-command submit-injections)))
          (testing "succeeds even when catalogue item is disabled"
            (let [disabled (assoc-in returned [:application/resources 0 :catalogue-item/enabled] false)]
              (is (= {:event/type :application.event/submitted
                      :event/time test-time
                      :event/actor applicant-user-id
                      :application/id app-id}
                     (ok-command disabled submit-command submit-injections))))))))))


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
              :application/id app-id
              :application/comment "fine"}
             (ok-command application
                         {:type :application.command/approve
                          :actor handler-user-id
                          :comment "fine"}))))
    (testing "approved with end-date"
      (is (= {:event/type :application.event/approved
              :event/time test-time
              :event/actor handler-user-id
              :entitlement/end (DateTime. 1234)
              :application/id app-id
              :application/comment "fine"}
             (ok-command application
                         {:type :application.command/approve
                          :actor handler-user-id
                          :entitlement-end (DateTime. 1234)
                          :comment "fine"}))))
    (testing "rejected successfully"
      (is (= {:event/type :application.event/rejected
              :application/comment "bad"
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id}
             (ok-command application
                         {:type :application.command/reject
                          :actor handler-user-id
                          :comment "bad"}))))))

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
    (testing "handler can close approved application"
      (is (= {:event/type :application.event/closed
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id
              :application/comment "outdated"}
             (ok-command application
                         {:type :application.command/close
                          :actor handler-user-id
                          :comment "outdated"})))))
  (let [application (apply-events nil
                                  [dummy-created-event
                                   {:event/type :application.event/submitted
                                    :event/time test-time
                                    :event/actor applicant-user-id
                                    :application/id app-id}
                                   {:event/type :application.event/returned
                                    :event/time test-time
                                    :event/actor handler-user-id
                                    :application/id app-id
                                    :application/comment ""}])]
    (testing "applicant can close returned application"
      (is (= {:event/type :application.event/closed
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id
              :application/comment "outdated"}
             (ok-command application
                         {:type :application.command/close
                          :actor applicant-user-id
                          :comment "outdated"}))))
    (testing "handler can close returned application"
      (is (= {:event/type :application.event/closed
              :event/time test-time
              :event/actor handler-user-id
              :application/id app-id
              :application/comment "outdated"}
             (ok-command application
                         {:type :application.command/close
                          :actor handler-user-id
                          :comment "outdated"}))))))

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
        (is (= {:event/type :application.event/member-invited
                :event/time test-time
                :event/actor handler-user-id
                :application/id app-id
                :application/member {:name "Member Applicant 1"
                                     :email "member1@applicants.com"}
                :invitation/token "very-secure"}
               (ok-command submitted
                           {:type :application.command/invite-member
                            :actor handler-user-id
                            :member {:name "Member Applicant 1"
                                     :email "member1@applicants.com"}}
                           injections)))))))

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
        (is (= {:errors [{:type :t.actions.errors/already-member :userid "somebody" :application-id app-id}]}
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
              :application/id app-id
              ;; NB no comment
              :application/member {:userid "somebody"}}
             (ok-command application
                         {:type :application.command/remove-member
                          :actor handler-user-id
                          :member {:userid "somebody"}}
                         injections))))
    (testing "applicant cannot be removed"
      (is (= {:errors [{:type :cannot-remove-applicant}]}
             (fail-command application
                           {:type :application.command/remove-member
                            :actor applicant-user-id
                            :member {:userid applicant-user-id}}
                           injections)
             (fail-command application
                           {:type :application.command/remove-member
                            :actor handler-user-id
                            :member {:userid applicant-user-id}}
                           injections))))
    (testing "non-members cannot be removed"
      (is (= {:errors [{:type :user-not-member :user {:userid "notamember"}}]}
             (fail-command application
                           {:type :application.command/remove-member
                            :actor handler-user-id
                            :member {:userid "notamember"}}
                           injections))))
    (testing "removed members cannot see the application"
      (is (-> application
              (model/see-application? "somebody")))
      (is (not (-> application
                   (apply-commands [{:type :application.command/remove-member
                                     :actor applicant-user-id
                                     :member {:userid "somebody"}}]
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
              :application/id app-id
              :application/member {:name "Some Body" :email "some@body.com"}}
             (ok-command application
                         {:type :application.command/uninvite-member
                          :actor applicant-user-id
                          :member {:name "Some Body" :email "some@body.com"}}
                         injections))))
    (testing "uninvite member by handler"
      (is (= {:event/type :application.event/member-uninvited
              :event/time test-time
              :event/actor handler-user-id
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
                :event/time test-time
                :event/actor handler-user-id
                :application/id app-id}
               event-1))
        (is (instance? UUID request-id-2))
        (is (= {:event/type :application.event/review-requested
                :application/request-id request-id-2
                :application/reviewers [reviewer]
                :application/comment ""
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
        valid-attachment-id 1234
        wrong-application-attachment-id 1235
        wrong-user-attachment-id 1236
        unknown-attachment-id 1237
        injections {:valid-user? #{reviewer}
                    :get-attachment-metadata
                    {valid-attachment-id {:application/id (:application/id application)
                                          :attachment/id valid-attachment-id
                                          :attachment/user handler-user-id}
                     wrong-application-attachment-id {:application/id (inc (:application/id application))
                                                      :attachment/id wrong-application-attachment-id
                                                      :attachment/user handler-user-id}
                     wrong-user-attachment-id {:application/id (:application/id application)
                                               :attachment/id wrong-user-attachment-id
                                               :attachment/user "carl"}}}]
    (testing "handler can remark"
      (let [event (ok-command application
                              {:type :application.command/remark
                               :actor handler-user-id
                               :comment "handler's remark"
                               :attachments [{:attachment/id valid-attachment-id}]
                               :public false}
                              injections)
            application (apply-events application [event])]
        (is (= {:event/type :application.event/remarked
                :event/time test-time
                :event/actor handler-user-id
                :application/id app-id
                :application/comment "handler's remark"
                :application/public false
                :event/attachments [{:attachment/id valid-attachment-id}]}
               event))))
    (testing "invalid attachments"
      (is (= {:errors [{:type :invalid-attachments
                        :attachments [wrong-application-attachment-id wrong-user-attachment-id unknown-attachment-id]}]}
             (fail-command application
                           {:type :application.command/remark
                            :actor handler-user-id
                            :comment "handler's remark"
                            :attachments [{:attachment/id valid-attachment-id}
                                          {:attachment/id wrong-application-attachment-id}
                                          {:attachment/id wrong-user-attachment-id}
                                          {:attachment/id unknown-attachment-id}]
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
                    :application/id app-id
                    :application/comment "second remark"
                    :application/public false}
                   event-2))))))))

(deftest test-copy-as-new
  (let [created-event {:event/type :application.event/created
                       :event/time test-time
                       :event/actor applicant-user-id
                       :application/id app-id
                       :application/external-id "2018/55"
                       :application/resources [{:catalogue-item/id 1
                                                :resource/ext-id "res1"}
                                               {:catalogue-item/id 2
                                                :resource/ext-id "res2"}]
                       :application/licenses []
                       :application/forms [{:form/id 3}]
                       :workflow/id 1
                       :workflow/type :workflow/default}
        application (apply-events nil [created-event
                                       {:event/type :application.event/draft-saved
                                        :event/time test-time
                                        :event/actor applicant-user-id
                                        :application/id app-id
                                        :application/field-values [{:form 3 :field "text" :value "1"}
                                                                   {:form 3 :field "attachment" :value "2"}]}])]
    (testing "creates a new application with the same form answers"
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
             (ok-command application
                         {:type :application.command/copy-as-new
                          :actor applicant-user-id}
                         injections))))))

(deftest test-delete
  (let [draft-application (apply-events nil [dummy-created-event])
        submitted-application (apply-events draft-application [{:event/type :application.event/submitted
                                                                :event/time test-time
                                                                :event/actor applicant-user-id
                                                                :application/id app-id}])]
    (testing "forbidden"
      (is (= {:errors [{:type :forbidden}]} (fail-command draft-application
                                                          {:type :application.command/delete
                                                           :actor handler-user-id}
                                                          injections)))
      (is (= {:errors [{:type :forbidden}]} (fail-command submitted-application
                                                          {:type :application.command/delete
                                                           :actor applicant-user-id}
                                                          injections))))
    (testing "success"
      (is (= {:event/type :application.event/deleted
              :event/time test-time
              :event/actor applicant-user-id
              :application/id app-id}
             (ok-command draft-application
                         {:type :application.command/delete
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
