(ns ^:integration rems.api.services.test-resources
  (:require [clojure.test :refer :all]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.resource :as resources]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- status-flags [res-id]
  (-> (resources/get-resource res-id)
      (select-keys [:enabled :archived])))

(deftest test-update-resource!
  (let [lic-id (test-data/create-license! {})
        res-id (test-data/create-resource! {:license-ids [lic-id]})
        res-id2 (test-data/create-resource! {})

        archive-license! #(licenses/set-license-archived! {:id lic-id
                                                           :archived %})]

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

    (testing "cannot unarchive if license is archived"
      (resources/update-resource! {:id res-id
                                   :archived true})
      (archive-license! true)
      (is (not (:success (resources/update-resource! {:id res-id
                                                      :archived false}))))
      (archive-license! false)
      (is (:success (resources/update-resource! {:id res-id
                                                 :archived false}))))

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
