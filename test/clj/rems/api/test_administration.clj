(ns ^:integration rems.api.test-administration
  "Tests for invariants across administration APIs."
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.core :as db]
            [rems.db.workflow :as workflow]
            [rems.db.testing :refer [owners-fixture +test-api-key+]]))

(use-fixtures
  :once
  api-fixture
  owners-fixture)

(deftest test-archiving-disabling
  (let [license-id (:id (api-call :post "/api/licenses/create" {:licensetype "text" :organization {:organization/id "organization2"} :localizations {}}
                                  +test-api-key+ "owner"))
        resource-id (:id (api-call :post "/api/resources/create" {:resid "test" :organization {:organization/id "organization2"} :licenses [license-id]}
                                   +test-api-key+ "owner"))
        form-id (:id (api-call :post "/api/forms/create" {:organization {:organization/id "organization2"}
                                                          :form/internal-name "form update test"
                                                          :form/external-title {:en "en form update test"
                                                                                :fi "fi form update test"
                                                                                :sv "sv form update test"}
                                                          :form/fields []}
                               +test-api-key+ "owner"))
        wf-form-id (:id (api-call :post "/api/forms/create" {:organization {:organization/id "organization2"}
                                                             :form/internal-name "workflow form update test"
                                                             :form/external-title {:en "en workflow form update test"
                                                                                   :fi "fi workflow form update test"
                                                                                   :sv "sv workflow form update test"}
                                                             :form/fields []}
                                  +test-api-key+ "owner"))
        workflow-id (:id (api-call :post "/api/workflows/create"
                                   {:organization {:organization/id "organization2"} :title "default workflow"
                                    :forms [{:form/id wf-form-id}]
                                    :type :workflow/default
                                    :handlers ["owner"]
                                    :licenses [{:license/id license-id}]}
                                   +test-api-key+ "owner"))
        catalogue-id (:id (api-call :post "/api/catalogue-items/create"
                                    {:form form-id
                                     :resid resource-id
                                     :wfid workflow-id
                                     :organization {:organization/id "organization2"}
                                     :localizations {}}
                                    +test-api-key+ "owner"))

        resource-archived! #(api-call :put "/api/resources/archived"
                                      {:id resource-id
                                       :archived %}
                                      +test-api-key+ "owner")

        resource-enabled! #(api-call :put "/api/resources/enabled"
                                     {:id resource-id
                                      :enabled %}
                                     +test-api-key+ "owner")

        catalogue-item-archived! #(api-call :put "/api/catalogue-items/archived"
                                            {:id catalogue-id
                                             :archived %}
                                            +test-api-key+ "owner")

        catalogue-item-enabled! #(api-call :put "/api/catalogue-items/enabled"
                                           {:id catalogue-id
                                            :enabled %}
                                           +test-api-key+ "owner")

        form-archived! #(api-call :put "/api/forms/archived"
                                  {:id %1
                                   :archived %2}
                                  +test-api-key+ "owner")

        form-enabled! #(api-call :put "/api/forms/enabled"
                                 {:id %1
                                  :enabled %2}
                                 +test-api-key+ "owner")

        license-archived! #(api-call :put "/api/licenses/archived"
                                     {:id license-id
                                      :archived %}
                                     +test-api-key+ "owner")

        license-enabled! #(api-call :put "/api/licenses/enabled"
                                    {:id license-id
                                     :enabled %}
                                    +test-api-key+ "owner")

        workflow-archived! #(api-call :put "/api/workflows/archived"
                                      {:id workflow-id
                                       :archived %}
                                      +test-api-key+ "owner")

        workflow-enabled! #(api-call :put "/api/workflows/enabled"
                                     {:id workflow-id
                                      :enabled %}
                                     +test-api-key+ "owner")]
    (is license-id)
    (is resource-id)
    (is form-id)
    (is workflow-id)
    (is catalogue-id)

    (testing "can disable a resource"
      (is (:success (resource-enabled! false))))

    (testing "can't archive resource if it is part of an active catalogue item"
      (is (= {:success false
              :errors [{:type "t.administration.errors/in-use-by"
                        :catalogue-items [{:id catalogue-id :localizations {}}]}]}
             (resource-archived! true))))

    (testing "can disable a form"
      (is (:success (form-enabled! form-id false))))

    (testing "can't archive a form that's in use"
      (is (= {:success false
              :errors [{:type "t.administration.errors/in-use-by"
                        :catalogue-items [{:id catalogue-id :localizations {}}]}]}
             (form-archived! form-id true)))
      (is (= {:success false
              :errors [{:type "t.administration.errors/in-use-by"
                        :workflows [{:id workflow-id :title "default workflow"}]}]}
             (form-archived! wf-form-id true))))

    (testing "can disable a workflow"
      (is (:success (workflow-enabled! false))))

    (testing "can't archive a workflow that's in use"
      (is (= {:success false
              :errors [{:type "t.administration.errors/in-use-by"
                        :catalogue-items [{:id catalogue-id :localizations {}}]}]}
             (workflow-archived! true))))

    (testing "can disable a license"
      (is (:success (license-enabled! false))))

    (testing "can't archive a license that's in use"
      (is (= {:success false
              :errors [{:type "t.administration.errors/in-use-by"
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
                        :forms [{:form/id form-id
                                 :form/internal-name "form update test"
                                 :form/external-title {:fi "fi form update test"
                                                       :en "en form update test"
                                                       :sv "sv form update test"}}]}]}
             (catalogue-item-archived! false))))

    (testing "cannot unarchive a workflow with a form that's archived"
      (is (= {:success false
              :errors [{:type "t.administration.errors/dependencies-archived"
                        :forms [{:form/id wf-form-id
                                 :form/internal-name "workflow form update test"
                                 :form/external-title {:fi "fi workflow form update test"
                                                       :en "en workflow form update test"
                                                       :sv "sv workflow form update test"}}]}]}
             (workflow-archived! false))))

    (testing "can archive a license that's not in use"
      (is (= {:success true} (license-archived! true))))

    (testing "cannot unarchive a resource with an archived license"
      (is (= {:success false
              :errors [{:type "t.administration.errors/dependencies-archived"
                        :licenses [{:id license-id :localizations {}}]}]}
             (resource-archived! false))))))
