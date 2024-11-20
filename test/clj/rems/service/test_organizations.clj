(ns ^:integration rems.service.test-organizations
  (:require [clojure.test :refer :all]
            [rems.service.organizations]
            [rems.db.organizations]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.testing-util :refer [with-user]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- status-flags [id]
  (with-user "owner"
    (-> (rems.db.organizations/get-organization-by-id-raw id)
        (select-keys [:enabled :archived]))))

(deftest organization-enabled-archived-test
  (test-helpers/create-user! {:userid "owner"} :owner)
  (with-user "owner"
    (let [org-id1 (test-helpers/create-organization! {:organization/id "test-org-1"})
          org-id2 (test-helpers/create-organization! {:organization/id "test-org-2"})]

      (testing "with invalid data"
        (is (thrown? clojure.lang.ExceptionInfo
                     (rems.service.organizations/add-organization! {:organization/id "invalid-org"
                                                                    :organization/short-name {:en "I" :fi "I" :sv "I"}
                                                                    :organization/name {:en "I" :fi "I" :sv "I"}
                                                                    :organization/invalid "should not work"}))
            "can't include invalid fields")

        (is (thrown? clojure.lang.ExceptionInfo
                     (with-user "owner"
                       (rems.service.organizations/edit-organization!
                        {:organization/id "test-org-1"
                         :organization/short-name {:en "I" :fi "I" :sv "I"}
                         :organization/name {:en "I" :fi "I" :sv "I"}
                         :organization/invalid "should not work"})))
            "can't edit invalid fields in"))

      (testing "new organizations are enabled and not archived"
        (is (= {:enabled true
                :archived false}
               (status-flags org-id1))))

      ;; reset all to false for the following tests
      (rems.service.organizations/set-organization-enabled! {:organization/id org-id1
                                                             :enabled false})
      (rems.service.organizations/set-organization-archived! {:organization/id org-id1
                                                              :archived false})

      (testing "enable"
        (rems.service.organizations/set-organization-enabled! {:organization/id org-id1
                                                               :enabled true})
        (is (= {:enabled true
                :archived false}
               (status-flags org-id1))))

      (testing "disable"
        (rems.service.organizations/set-organization-enabled! {:organization/id org-id1
                                                               :enabled false})
        (is (= {:enabled false
                :archived false}
               (status-flags org-id1))))

      (testing "archive"
        (rems.service.organizations/set-organization-archived! {:organization/id org-id1
                                                                :archived true})
        (is (= {:enabled false
                :archived true}
               (status-flags org-id1))))

      (testing "unarchive"
        (rems.service.organizations/set-organization-archived! {:organization/id org-id1
                                                                :archived false})
        (is (= {:enabled false
                :archived false}
               (status-flags org-id1))))

      (testing "does not affect unrelated organizations"
        (rems.service.organizations/set-organization-enabled! {:organization/id org-id1
                                                               :enabled true})
        (rems.service.organizations/set-organization-archived! {:organization/id org-id1
                                                                :archived true})
        (rems.service.organizations/set-organization-enabled! {:organization/id org-id2
                                                               :enabled false})
        (rems.service.organizations/set-organization-archived! {:organization/id org-id2
                                                                :archived false})
        (is (= {:enabled true
                :archived true}
               (status-flags org-id1)))
        (is (= {:enabled false
                :archived false}
               (status-flags org-id2)))

        (is (:success (rems.service.organizations/set-organization-archived! {:organization/id org-id1
                                                                              :archived false})))
        (is (:success (rems.service.organizations/set-organization-archived! {:organization/id org-id2
                                                                              :archived true})))

        (is (= {:enabled true
                :archived false}
               (status-flags org-id1)))
        (is (= {:enabled false
                :archived true}
               (status-flags org-id2)))))))
