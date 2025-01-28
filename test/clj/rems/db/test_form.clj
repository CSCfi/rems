(ns ^:integration rems.db.test-form
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.cache :as cache]
            [rems.db.form :as form]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-form-template-cache
  (testing "cache reload works"
    (let [org-id (test-helpers/create-organization! {})
          form-id (test-helpers/create-form! {:organization {:organization/id org-id}
                                              :form/internal-name "test-form"
                                              :form/external-title {:en "Test Form"
                                                                    :fi "Testilomake"
                                                                    :sv "Testformulär"}
                                              :form/fields [{:field/type :text
                                                             :field/title {:en "Text field"
                                                                           :fi "Tekstikenttä"
                                                                           :sv "Textfält"}
                                                             :field/optional false}]})]
      ;; force cache reload
      (cache/set-uninitialized! rems.db.form/form-template-cache)

      (is (= {form-id {:form/id form-id
                       :organization {:organization/id org-id}
                       :form/internal-name "test-form"
                       :form/external-title {:fi "Testilomake"
                                             :en "Test Form"
                                             :sv "Testformulär"}
                       :form/title "test-form" ; deprecated
                       :form/fields [{:field/title {:fi "Tekstikenttä"
                                                    :en "Text field"
                                                    :sv "Textfält"}
                                      :field/type :text
                                      :field/id "fld1"
                                      :field/optional false}]
                       :enabled true
                       :archived false}}
             (into {} (cache/entries! rems.db.form/form-template-cache)))))))