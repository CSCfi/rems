(ns ^:integration rems.service.test-organizations
  (:require [clojure.test :refer :all]
            [rems.service.organizations :as organizations]
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
                     (organizations/add-organization! {:organization/id "invalid-org"
                                                       :organization/short-name {:en "I" :fi "I" :sv "I"}
                                                       :organization/name {:en "I" :fi "I" :sv "I"}
                                                       :organization/invalid "should not work"}))
            "can't include invalid fields")

        (is (thrown? clojure.lang.ExceptionInfo
                     (with-user "owner"
                       (organizations/edit-organization!
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
      (organizations/set-organization-enabled! {:organization/id org-id1
                                                :enabled false})
      (organizations/set-organization-archived! {:organization/id org-id1
                                                 :archived false})

      (testing "enable"
        (organizations/set-organization-enabled! {:organization/id org-id1
                                                  :enabled true})
        (is (= {:enabled true
                :archived false}
               (status-flags org-id1))))

      (testing "disable"
        (organizations/set-organization-enabled! {:organization/id org-id1
                                                  :enabled false})
        (is (= {:enabled false
                :archived false}
               (status-flags org-id1))))

      (testing "archive"
        (organizations/set-organization-archived! {:organization/id org-id1
                                                   :archived true})
        (is (= {:enabled false
                :archived true}
               (status-flags org-id1))))

      (testing "unarchive"
        (organizations/set-organization-archived! {:organization/id org-id1
                                                   :archived false})
        (is (= {:enabled false
                :archived false}
               (status-flags org-id1))))

      (testing "does not affect unrelated organizations"
        (organizations/set-organization-enabled! {:organization/id org-id1
                                                  :enabled true})
        (organizations/set-organization-archived! {:organization/id org-id1
                                                   :archived true})
        (organizations/set-organization-enabled! {:organization/id org-id2
                                                  :enabled false})
        (organizations/set-organization-archived! {:organization/id org-id2
                                                   :archived false})
        (is (= {:enabled true
                :archived true}
               (status-flags org-id1)))
        (is (= {:enabled false
                :archived false}
               (status-flags org-id2)))

        (is (:success (organizations/set-organization-archived! {:organization/id org-id1
                                                                 :archived false})))
        (is (:success (organizations/set-organization-archived! {:organization/id org-id2
                                                                 :archived true})))

        (is (= {:enabled true
                :archived false}
               (status-flags org-id1)))
        (is (= {:enabled false
                :archived true}
               (status-flags org-id2)))))))
