(ns ^:integration rems.api.services.test-dependencies
  (:require [clojure.test :refer :all]
            [rems.api.services.dependencies :as dependencies]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-dependencies
  (let [shared-license (test-data/create-license! {})
        shared-resource-license (test-data/create-license! {})
        resource-license (test-data/create-license! {})
        unused-license (test-data/create-license! {})
        res-1 (test-data/create-resource! {:resource-ext-id "res1"
                                           :license-ids [shared-license shared-resource-license]})
        res-2 (test-data/create-resource! {:resource-ext-id "res2"
                                           :license-ids [shared-license shared-resource-license resource-license]})
        shared-form (test-data/create-form! {})
        wf-form (test-data/create-form! {})
        cat-form (test-data/create-form! {})
        unused-form (test-data/create-form! {})
        wf-1 (test-data/create-workflow! {:forms [{:form/id shared-form}]})
        wf-2 (test-data/create-workflow! {:forms [{:form/id wf-form} {:form/id shared-form}]})
        cat-1 (test-data/create-catalogue-item! {:resource-id res-1
                                                 :form-id cat-form
                                                 :workflow-id wf-1})
        cat-2 (test-data/create-catalogue-item! {:resource-id res-1
                                                 :form-id shared-form
                                                 :workflow-id wf-2})]
    ;; TODO no public way to set workflow licenses for now
    (db/create-workflow-license! {:wfid wf-2 :licid shared-license})

    (testing "dependencies"
      (is (= {:dependencies
              {{:resource/id res-1} #{{:license/id shared-license :archived false :enabled true}
                                      {:license/id shared-resource-license :archived false :enabled true}}
               {:resource/id res-2} #{{:license/id shared-license :archived false :enabled true}
                                      {:license/id shared-resource-license :archived false :enabled true}
                                      {:license/id resource-license :archived false :enabled true}}
               {:workflow/id wf-1} #{{:form/id shared-form :archived false :enabled true}}
               {:workflow/id wf-2} #{{:form/id shared-form :archived false :enabled true}
                                     {:form/id wf-form :archived false :enabled true}
                                     {:license/id shared-license :archived false :enabled true}}
               {:catalogue-item/id cat-1} #{{:resource/id res-1 :archived false :enabled true}
                                            {:form/id cat-form :archived false :enabled true}
                                            {:workflow/id wf-1 :archived false :enabled true}}
               {:catalogue-item/id cat-2} #{{:resource/id res-1 :archived false :enabled true}
                                            {:form/id shared-form :archived false :enabled true}
                                            {:workflow/id wf-2 :archived false :enabled true}}}
              :reverse-dependencies
              {{:license/id shared-license} #{{:resource/id res-1 :archived false :enabled true}
                                              {:resource/id res-2 :archived false :enabled true}
                                              {:workflow/id wf-2 :archived false :enabled true}}
               {:license/id shared-resource-license} #{{:resource/id res-1 :archived false :enabled true}
                                                       {:resource/id res-2 :archived false :enabled true}}
               {:license/id resource-license} #{{:resource/id res-2 :archived false :enabled true}}
               {:resource/id res-1} #{{:catalogue-item/id cat-1 :archived false :enabled true}
                                      {:catalogue-item/id cat-2 :archived false :enabled true}}
               {:form/id shared-form} #{{:workflow/id wf-1 :archived false :enabled true}
                                        {:workflow/id wf-2 :archived false :enabled true}
                                        {:catalogue-item/id cat-2 :archived false :enabled true}}
               {:form/id cat-form} #{{:catalogue-item/id cat-1 :archived false :enabled true}}
               {:form/id wf-form} #{{:workflow/id wf-2 :archived false :enabled true}}
               {:workflow/id wf-1} #{{:catalogue-item/id cat-1 :archived false :enabled true}}
               {:workflow/id wf-2} #{{:catalogue-item/id cat-2 :archived false :enabled true}}}}
             (dependencies/dependencies))))

    (testing "enrich-dependency"
      (is (= {:id cat-1 :enabled true :archived false}
             (select-keys (dependencies/enrich-dependency {:catalogue-item/id cat-1})
                          [:id :enabled :archived])))
      (is (= {:id shared-license :enabled true :archived false}
             (select-keys (dependencies/enrich-dependency {:license/id shared-license})
                          [:id :enabled :archived])))
      (is (= {:id res-1 :enabled true :archived false}
             (select-keys (dependencies/enrich-dependency {:resource/id res-1})
                          [:id :enabled :archived])))
      (is (= {:form/id shared-form :enabled true :archived false}
             (select-keys (dependencies/enrich-dependency {:form/id shared-form})
                          [:form/id :enabled :archived])))
      (is (= {:id wf-1 :enabled true :archived false}
             (select-keys (dependencies/enrich-dependency {:workflow/id wf-1})
                          [:id :enabled :archived]))))))
