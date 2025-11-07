(ns ^:integration rems.db.test-organizations
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [rems.cache :as cache]
            [rems.db.organizations]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-organization-cache
  (testing "cache reload works"
    (let [org-id (test-helpers/create-organization! {:organization/id "test-org"
                                                     :organization/name {:fi "Testiorganisaatio"
                                                                         :en "Test Organization"
                                                                         :sv "Testorganisation"}
                                                     :organization/short-name {:fi "Testi"
                                                                               :en "Test"
                                                                               :sv "Test"}
                                                     :organization/owners [{:userid "owner"}]})]
      ;; force cache reload
      (cache/set-uninitialized! rems.db.organizations/organization-cache)

      (is (= {org-id {:organization/id org-id
                      :organization/name {:fi "Testiorganisaatio"
                                          :en "Test Organization"
                                          :sv "Testorganisation"}
                      :organization/short-name {:fi "Testi"
                                                :en "Test"
                                                :sv "Test"}
                      :organization/owners [{:userid "owner"}]
                      :organization/review-emails []
                      :enabled true
                      :archived false}}
             (into {} (cache/entries! rems.db.organizations/organization-cache)))))))