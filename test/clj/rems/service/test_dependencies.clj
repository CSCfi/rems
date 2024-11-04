(ns ^:integration rems.service.test-dependencies
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.common.dependency :as dep]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.service.dependencies :as dependencies]))

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
        wf-2 (test-helpers/create-workflow! {:forms [{:form/id wf-form}
                                                     {:form/id shared-form}]
                                             :licenses [shared-license]})
        category-1 (test-helpers/create-category! {:category/title {:fi "Testikategoria"
                                                                    :en "Test category"
                                                                    :sv "Test kategori"}
                                                   :category/description {:fi "Kuvaus"
                                                                          :en "Description"
                                                                          :sv "Rubrik"}
                                                   :category/children []})
        category-2 (test-helpers/create-category! {:category/title {:en "Test category"}
                                                   :category/description {:en "Description"}
                                                   :category/children [{:category/id category-1}]})
        cat-1 (test-helpers/create-catalogue-item! {:resource-id res-1
                                                    :form-id cat-form
                                                    :workflow-id wf-1
                                                    :categories [{:category/id category-1}]})
        cat-2 (test-helpers/create-catalogue-item! {:resource-id res-1
                                                    :form-id shared-form
                                                    :workflow-id wf-2})
        cat-without-form (test-helpers/create-catalogue-item! {:resource-id res-1
                                                               :form-id nil
                                                               :workflow-id wf-2})
        dep-graph (dependencies/db-dependency-graph)]

    (testing "get resource dependencies and dependents"
      (is (= #{{:license/id shared-license}
               {:license/id shared-resource-license}
               {:organization/id "default"}}
             (dep/get-all-dependencies dep-graph {:resource/id res-1})))
      (is (= #{{:catalogue-item/id cat-1}
               {:catalogue-item/id cat-2}
               {:catalogue-item/id cat-without-form}}
             (dep/get-all-dependents dep-graph {:resource/id res-1}))))

    (testing "get catalogue item dependencies and dependents"
      (let [immediate-deps #{{:category/id category-1}
                             {:form/id cat-form}
                             {:organization/id "default"}
                             {:resource/id res-1}
                             {:workflow/id wf-1}}]
        (is (= immediate-deps
               (dep/get-dependencies dep-graph {:catalogue-item/id cat-1})))
        (testing "transitive dependencies"
          (is (= (into immediate-deps
                       #{{:form/id shared-form}
                         {:license/id shared-license}
                         {:license/id shared-resource-license}})
                 (dep/get-all-dependencies dep-graph {:catalogue-item/id cat-1})))))
      (is (= #{}
             (dep/get-all-dependents dep-graph {:catalogue-item/id cat-1}))))

    (testing "all dependencies"
      (is (= {{:license/id shared-license} #{{:organization/id "default"}}
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
                                           {:organization/id "default"}
                                           {:category/id category-1}}
              {:catalogue-item/id cat-2} #{{:resource/id res-1}
                                           {:form/id shared-form}
                                           {:workflow/id wf-2}
                                           {:organization/id "default"}}
              {:catalogue-item/id cat-without-form} #{{:resource/id res-1}
                                                      {:workflow/id wf-2}
                                                      {:organization/id "default"}}
              {:category/id category-2} #{{:category/id category-1}}}
             (:dependencies dep-graph))))

    (testing "all dependents"
      (is (= {{:license/id shared-license} #{{:resource/id res-1}
                                             {:resource/id res-2}
                                             {:workflow/id wf-2}}
              {:license/id shared-resource-license} #{{:resource/id res-1}
                                                      {:resource/id res-2}}
              {:license/id resource-license} #{{:resource/id res-2}}
              {:resource/id res-1} #{{:catalogue-item/id cat-1}
                                     {:catalogue-item/id cat-2}
                                     {:catalogue-item/id cat-without-form}}
              {:form/id shared-form} #{{:workflow/id wf-1}
                                       {:workflow/id wf-2}
                                       {:catalogue-item/id cat-2}}
              {:form/id cat-form} #{{:catalogue-item/id cat-1}}
              {:form/id wf-form} #{{:workflow/id wf-2}}
              {:workflow/id wf-1} #{{:catalogue-item/id cat-1}}
              {:workflow/id wf-2} #{{:catalogue-item/id cat-2}
                                    {:catalogue-item/id cat-without-form}}
              {:organization/id "default"} #{{:catalogue-item/id cat-1}
                                             {:catalogue-item/id cat-2}
                                             {:catalogue-item/id cat-without-form}
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
                                             {:workflow/id wf-2}}
              {:category/id category-1} #{{:catalogue-item/id cat-1}
                                          {:category/id category-2}}}
             (:dependents dep-graph))))))
