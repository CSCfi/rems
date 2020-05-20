(ns ^:integration rems.api.test-administration
  "Tests for invariants across administration APIs."
  (:require [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.api.testing :refer :all]))

(use-fixtures
  :once
  api-fixture)

(def api-key "42")
(def user-id "owner")


(deftest test-archiving-disabling
  (let [api-key "42"
        user-id "owner"
        license-id (:id (api-call :post "/api/licenses/create" {:licensetype "text" :organization {:organization/id "abc"} :localizations {}}
                                  api-key user-id))
        resource-id (:id (api-call :post "/api/resources/create" {:resid "test" :organization {:organization/id "abc"} :licenses [license-id]}
                                   api-key user-id))
        form-id (:id (api-call :post "/api/forms/create" {:form/organization {:organization/id "abc"} :form/title "form update test" :form/fields []}
                               api-key user-id))
        wf-form-id (:id (api-call :post "/api/forms/create" {:form/organization {:organization/id "abc"} :form/title "workflow form update test" :form/fields []}
                                  api-key user-id))
        workflow-id (:id (api-call :post "/api/workflows/create"
                                   {:organization {:organization/id "abc"} :title "default workflow"
                                    :forms [{:form/id wf-form-id}]
                                    :type :workflow/default :handlers [user-id]}
                                   api-key user-id))
        catalogue-id (:id (api-call :post "/api/catalogue-items/create"
                                    {:form form-id
                                     :resid resource-id
                                     :wfid workflow-id
                                     :organization {:organization/id "abc"}
                                     :localizations {}}
                                    api-key user-id))

        resource-archived! #(api-call :put "/api/resources/archived"
                                      {:id resource-id
                                       :archived %}
                                      api-key user-id)

        resource-enabled! #(api-call :put "/api/resources/enabled"
                                     {:id resource-id
                                      :enabled %}
                                     api-key user-id)

        catalogue-item-archived! #(api-call :put "/api/catalogue-items/archived"
                                            {:id catalogue-id
                                             :archived %}
                                            api-key user-id)

        catalogue-item-enabled! #(api-call :put "/api/catalogue-items/enabled"
                                           {:id catalogue-id
                                            :enabled %}
                                           api-key user-id)

        form-archived! #(api-call :put "/api/forms/archived"
                                  {:id %1
                                   :archived %2}
                                  api-key user-id)

        form-enabled! #(api-call :put "/api/forms/enabled"
                                 {:id %1
                                  :enabled %2}
                                 api-key user-id)

        license-archived! #(api-call :put "/api/licenses/archived"
                                     {:id license-id
                                      :archived %}
                                     api-key user-id)

        license-enabled! #(api-call :put "/api/licenses/enabled"
                                    {:id license-id
                                     :enabled %}
                                    api-key user-id)

        workflow-archived! #(api-call :put "/api/workflows/archived"
                                      {:id workflow-id
                                       :archived %}
                                      api-key user-id)

        workflow-enabled! #(api-call :put "/api/workflows/enabled"
                                     {:id workflow-id
                                      :enabled %}
                                     api-key user-id)]
    (is license-id)
    (is resource-id)
    (is form-id)
    (is workflow-id)
    (is catalogue-id)

    ;; no api for this yet
    (db/create-workflow-license! {:wfid workflow-id :licid license-id})

    (testing "can disable a resource"
      (is (:success (resource-enabled! false))))

    (testing "can't archive resource if it is part of an active catalogue item"
      (is (= {:success false
              :errors [{:type "t.administration.errors/resource-in-use"
                        :catalogue-items [{:id catalogue-id :localizations {}}]}]}
             (resource-archived! true))))

    (testing "can disable a form"
      (is (:success (form-enabled! form-id false))))

    (testing "can't archive a form that's in use"
      (is (= {:success false
              :errors [{:type "t.administration.errors/form-in-use"
                        :catalogue-items [{:id catalogue-id :localizations {}}]}]}
             (form-archived! form-id true)))
      (is (= {:success false
              :errors [{:type "t.administration.errors/form-in-use"
                        :workflows [{:id workflow-id :title "default workflow"}]}]}
             (form-archived! wf-form-id true))))

    (testing "can disable a workflow"
      (is (:success (workflow-enabled! false))))

    (testing "can't archive a workflow that's in use"
      (is (= {:success false
              :errors [{:type "t.administration.errors/workflow-in-use"
                        :catalogue-items [{:id catalogue-id :localizations {}}]}]}
             (workflow-archived! true))))

    (testing "can disable a license"
      (is (:success (license-enabled! false))))

    (testing "can't archive a license that's in use"
      (is (= {:success false
              :errors [{:type "t.administration.errors/license-in-use"
                        :resources [{:id resource-id :resid "test"}]
                        :workflows [{:id workflow-id :title "default workflow"}]}]}
             (license-archived! true))))

    (testing "can archive a catalogue item"
      (is (:success (catalogue-item-archived! true))))

    (testing "can archive a resource that's not in use"
      (is (:success (resource-archived! true))))

    (testing "can archive a workflow that's not in use"
      (is (:success (workflow-archived! true))))

    (testing "can archive a form that's not in use"
      (is (:success (form-archived! form-id true)))
      (is (:success (form-archived! wf-form-id true))))

    (testing "cannot unarchive a catalogue item with dependencies that are archived"
      (is (= {:success false
              :errors [{:type "t.administration.errors/dependencies-archived",
                        :resources [{:id resource-id, :resid "test"}]
                        :workflows [{:id workflow-id, :title "default workflow"}]
                        :forms [{:form/id form-id, :form/title "form update test"}]}]}
             (catalogue-item-archived! false))))

    (testing "cannot unarchive a workflow with a form that's archived"
      (is (= {:success false
              :errors [{:type "t.administration.errors/dependencies-archived"
                        :forms [{:form/id wf-form-id :form/title "workflow form update test"}]}]}
             (workflow-archived! false))))

    (testing "can archive a license that's not in use"
      (is (= {:success true} (license-archived! true))))

    (testing "cannot unarchive a resource with an archived license"
      (is (= {:success false
              :errors [{:type "t.administration.errors/dependencies-archived"
                        :licenses [{:id license-id :localizations {}}]}]}
             (resource-archived! false))))))
