(ns ^:integration rems.api.test-administration
  "Tests for invariants across administration APIs."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.rpl.specter :refer [ALL transform]]
            [rems.api.testing :refer [api-call api-fixture]]
            [rems.db.workflow]
            [rems.db.testing :refer [owners-fixture +test-api-key+]]))

(use-fixtures
  :each
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
                                    +test-api-key+ "owner"))]
    (is license-id)
    (is resource-id)
    (is form-id)
    (is workflow-id)
    (is catalogue-id)

    (testing "can disable a resource"
      (is (:success (api-call :put "/api/resources/enabled" {:id resource-id :enabled false} +test-api-key+ "owner"))))

    (testing "can't archive resource if it is part of an active catalogue item"
      (is (= {:success false
              :errors [{:type "t.administration.errors/in-use-by"
                        :catalogue-items [{:catalogue-item/id catalogue-id :localizations {}}]}]}
             (api-call :put "/api/resources/archived" {:id resource-id :archived true} +test-api-key+ "owner"))))

    (testing "can disable a form"
      (is (:success (api-call :put "/api/forms/enabled" {:id form-id :enabled false} +test-api-key+ "owner"))))

    (testing "can't archive a form that's in use"
      (is (= {:success false
              :errors [{:type "t.administration.errors/in-use-by"
                        :catalogue-items [{:catalogue-item/id catalogue-id :localizations {}}]}]}
             (api-call :put "/api/forms/archived" {:id form-id :archived true} +test-api-key+ "owner")))
      (is (= {:success false
              :errors [{:type "t.administration.errors/in-use-by"
                        :workflows [{:workflow/id workflow-id :title "default workflow"}]
                        :catalogue-items [{:catalogue-item/id catalogue-id :localizations {}}]}]}
             (api-call :put "/api/forms/archived" {:id wf-form-id :archived true} +test-api-key+ "owner"))))

    (testing "can disable a workflow"
      (is (:success (api-call :put "/api/workflows/enabled" {:id workflow-id :enabled false} +test-api-key+ "owner"))))

    (testing "can't archive a workflow that's in use"
      (is (= {:success false
              :errors [{:type "t.administration.errors/in-use-by"
                        :catalogue-items [{:catalogue-item/id catalogue-id :localizations {}}]}]}
             (api-call :put "/api/workflows/archived" {:id workflow-id :archived true} +test-api-key+ "owner"))))

    (testing "can disable a license"
      (is (:success (api-call :put "/api/licenses/enabled" {:id license-id :enabled false} +test-api-key+ "owner"))))

    (testing "can't archive a license that's in use"
      (is (= {:success false
              :errors [{:type "t.administration.errors/in-use-by"
                        :resources [{:resource/id resource-id :resid "test"}]
                        :workflows [{:workflow/id workflow-id :title "default workflow"}]
                        :catalogue-items [{:catalogue-item/id catalogue-id :localizations {}}]}]}
             (api-call :put "/api/licenses/archived" {:id license-id :archived true} +test-api-key+ "owner"))))

    (testing "can archive a catalogue item"
      (is (:success (api-call :put "/api/catalogue-items/archived" {:id catalogue-id :archived true} +test-api-key+ "owner"))))

    (testing "can archive a resource that's not in use"
      (is (:success (api-call :put "/api/resources/archived" {:id resource-id :archived true} +test-api-key+ "owner"))))

    (testing "can archive a workflow that's not in use"
      (is (:success (api-call :put "/api/workflows/archived" {:id workflow-id :archived true} +test-api-key+ "owner"))))

    (testing "can archive a form that's not in use"
      (is (:success (api-call :put "/api/forms/archived" {:id form-id :archived true} +test-api-key+ "owner")))
      (is (:success (api-call :put "/api/forms/archived" {:id wf-form-id :archived true} +test-api-key+ "owner"))))

    (testing "cannot unarchive a catalogue item with dependencies that are archived"
      (let [response (api-call :put "/api/catalogue-items/archived" {:id catalogue-id :archived false} +test-api-key+ "owner")]
        (is (= {:success false
                :errors [{:type "t.administration.errors/dependencies-archived",
                          :resources [{:resource/id resource-id, :resid "test"}]
                          :workflows [{:workflow/id workflow-id, :title "default workflow"}]
                          :forms #{{:form/id form-id
                                    :form/internal-name "form update test"
                                    :form/external-title {:fi "fi form update test"
                                                          :en "en form update test"
                                                          :sv "sv form update test"}}
                                   {:form/id wf-form-id
                                    :form/internal-name "workflow form update test"
                                    :form/external-title {:en "en workflow form update test"
                                                          :fi "fi workflow form update test"
                                                          :sv "sv workflow form update test"}}}}]}
               (->> response
                    (transform [:errors ALL :forms] set))))))

    (testing "cannot unarchive a workflow with a form that's archived"
      (is (= {:success false
              :errors [{:type "t.administration.errors/dependencies-archived"
                        :forms [{:form/id wf-form-id
                                 :form/internal-name "workflow form update test"
                                 :form/external-title {:fi "fi workflow form update test"
                                                       :en "en workflow form update test"
                                                       :sv "sv workflow form update test"}}]}]}
             (api-call :put "/api/workflows/archived" {:id workflow-id :archived false} +test-api-key+ "owner"))))

    (testing "can archive a license that's not in use"
      (is (= {:success true} (api-call :put "/api/licenses/archived" {:id license-id :archived true} +test-api-key+ "owner"))))

    (testing "cannot unarchive a resource with an archived license"
      (is (= {:success false
              :errors [{:type "t.administration.errors/dependencies-archived"
                        :licenses [{:license/id license-id :localizations {}}]}]}
             (api-call :put "/api/resources/archived" {:id resource-id :archived false} +test-api-key+ "owner"))))))
