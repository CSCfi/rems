(ns ^:integration rems.db.test-resources
  (:require [clojure.test :refer :all]
            [rems.db.resource :as resources]
            [rems.db.core :as db]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- status-flags [res-id]
  (-> (resources/get-resource res-id)
      (select-keys [:enabled :archived])))

(deftest test-update-resource!
  (let [user-id "test-user"
        res-id (:id (resources/create-resource! {:resid "res1" :organization "abc" :licenses []} user-id))
        res-id2 (:id (resources/create-resource! {:resid "res2" :organization "abc" :licenses []} user-id))]

    (testing "new resources are enabled and not archived"
      (is (= {:enabled true
              :archived false}
             (status-flags res-id))))

    ;; reset all to false for the following tests
    (resources/update-resource! {:id res-id
                                 :enabled false
                                 :archived false})

    (testing "enable"
      (resources/update-resource! {:id res-id
                                   :enabled true})
      (is (= {:enabled true
              :archived false}
             (status-flags res-id))))

    (testing "disable"
      (resources/update-resource! {:id res-id
                                   :enabled false})
      (is (= {:enabled false
              :archived false}
             (status-flags res-id))))

    (testing "archive"
      (resources/update-resource! {:id res-id
                                   :archived true})
      (is (= {:enabled false
              :archived true}
             (status-flags res-id))))

    (testing "unarchive"
      (resources/update-resource! {:id res-id
                                   :archived false})
      (is (= {:enabled false
              :archived false}
             (status-flags res-id))))

    (testing "does not affect unrelated resources"
      (resources/update-resource! {:id res-id
                                   :enabled true
                                   :archived true})
      (resources/update-resource! {:id res-id2
                                   :enabled false
                                   :archived false})
      (is (= {:enabled true
              :archived true}
             (status-flags res-id)))
      (is (= {:enabled false
              :archived false}
             (status-flags res-id2))))))
