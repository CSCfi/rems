(ns ^:integration rems.api.services.test-dependencies
  (:require [clojure.test :refer :all]
            [rems.api.services.dependencies :as dependencies]
            [rems.db.core :as db]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-dependencies
  (let [shared-license (test-helpers/create-license! {})
        shared-resource-license (test-helpers/create-license! {})
        resource-license (test-helpers/create-license! {})
        unused-license (test-helpers/create-license! {})
        res-1 (test-helpers/create-resource! {:resource-ext-id "res1"
                                              :license-ids [shared-license shared-resource-license]})
        res-2 (test-helpers/create-resource! {:resource-ext-id "res2"
                                              :license-ids [shared-license shared-resource-license resource-license]})
        shared-form (test-helpers/create-form! {})
        wf-form (test-helpers/create-form! {})
        cat-form (test-helpers/create-form! {})
        unused-form (test-helpers/create-form! {})
        wf-1 (test-helpers/create-workflow! {:forms [{:form/id shared-form}]})
        wf-2 (test-helpers/create-workflow! {:forms [{:form/id wf-form} {:form/id shared-form}]})
        cat-1 (test-helpers/create-catalogue-item! {:resource-id res-1
                                                    :form-id cat-form
                                                    :workflow-id wf-1})
        cat-2 (test-helpers/create-catalogue-item! {:resource-id res-1
                                                    :form-id shared-form
                                                    :workflow-id wf-2})]
    ;; TODO no public way to set workflow licenses for now
    (db/create-workflow-license! {:wfid wf-2 :licid shared-license})

    (testing "dependencies"
      (is (= {:dependencies
              {{:license/id shared-license} #{{:organization/id "default"}}
               {:license/id shared-resource-license} #{{:organization/id "default"}}
               {:license/id resource-license} #{{:organization/id "default"}}
               {:license/id unused-license} #{{:organization/id "default"}}
               {:form/id shared-form} #{{:organization/id "default"}}
               {:form/id wf-form} #{{:organization/id "default"}}
               {:form/id cat-form} #{{:organization/id "default"}}
               {:form/id unused-form} #{{:organization/id "default"}}
               {:resource/id res-1} #{{:license/id shared-license}
                                      {:license/id shared-resource-license}
                                      {:organization/id "default"}}
               {:resource/id res-2} #{{:license/id shared-license}
                                      {:license/id shared-resource-license}
                                      {:license/id resource-license}
                                      {:organization/id "default"}}
               {:workflow/id wf-1} #{{:form/id shared-form}
                                     {:organization/id "default"}}
               {:workflow/id wf-2} #{{:form/id shared-form}
                                     {:form/id wf-form}
                                     {:license/id shared-license}
                                     {:organization/id "default"}}
               {:catalogue-item/id cat-1} #{{:resource/id res-1}
                                            {:form/id cat-form}
                                            {:workflow/id wf-1}
                                            {:organization/id "default"}}
               {:catalogue-item/id cat-2} #{{:resource/id res-1}
                                            {:form/id shared-form}
                                            {:workflow/id wf-2}
                                            {:organization/id "default"}}}
              :reverse-dependencies
              {{:license/id shared-license} #{{:resource/id res-1}
                                              {:resource/id res-2}
                                              {:workflow/id wf-2}}
               {:license/id shared-resource-license} #{{:resource/id res-1}
                                                       {:resource/id res-2}}
               {:license/id resource-license} #{{:resource/id res-2}}
               {:resource/id res-1} #{{:catalogue-item/id cat-1}
                                      {:catalogue-item/id cat-2}}
               {:form/id shared-form} #{{:workflow/id wf-1}
                                        {:workflow/id wf-2}
                                        {:catalogue-item/id cat-2}}
               {:form/id cat-form} #{{:catalogue-item/id cat-1}}
               {:form/id wf-form} #{{:workflow/id wf-2}}
               {:workflow/id wf-1} #{{:catalogue-item/id cat-1}}
               {:workflow/id wf-2} #{{:catalogue-item/id cat-2}}
               {:organization/id "default"} #{{:catalogue-item/id cat-1}
                                              {:catalogue-item/id cat-2}
                                              {:form/id shared-form}
                                              {:form/id wf-form}
                                              {:form/id cat-form}
                                              {:form/id unused-form}
                                              {:resource/id res-1}
                                              {:resource/id res-2}
                                              {:license/id shared-license}
                                              {:license/id shared-resource-license}
                                              {:license/id resource-license}
                                              {:license/id unused-license}
                                              {:workflow/id wf-1}
                                              {:workflow/id wf-2}}}}
             (#'dependencies/dependencies))))

    (testing "enrich-dependency"
      (is (= {:id cat-1 :enabled true :archived false}
             (select-keys (#'dependencies/enrich-dependency {:catalogue-item/id cat-1})
                          [:id :enabled :archived])))
      (is (= {:id shared-license :enabled true :archived false}
             (select-keys (#'dependencies/enrich-dependency {:license/id shared-license})
                          [:id :enabled :archived])))
      (is (= {:id res-1 :enabled true :archived false}
             (select-keys (#'dependencies/enrich-dependency {:resource/id res-1})
                          [:id :enabled :archived])))
      (is (= {:form/id shared-form :enabled true :archived false}
             (select-keys (#'dependencies/enrich-dependency {:form/id shared-form})
                          [:form/id :enabled :archived])))
      (is (= {:id wf-1 :enabled true :archived false}
             (select-keys (#'dependencies/enrich-dependency {:workflow/id wf-1})
                          [:id :enabled :archived]))))))
