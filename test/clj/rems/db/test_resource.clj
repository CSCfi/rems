(ns ^:integration rems.db.test-resource
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.cache :as cache]
            [rems.db.resource]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-resource-cache
  (testing "cache reload works"
    (let [org-id (test-helpers/create-organization! {})
          res-id (test-helpers/create-resource! {:resource-ext-id "test-resource"
                                                 :organization {:organization/id org-id}
                                                 :resid "https://example.org/resource"})]
      ;; force cache reload
      (cache/set-uninitialized! rems.db.resource/resource-cache)

      (is (= {res-id {:id res-id
                      :organization {:organization/id org-id}
                      :resid "test-resource"
                      :enabled true
                      :archived false}}
             (into {} (cache/entries! rems.db.resource/resource-cache)))))))