(ns ^:integration rems.api.services.test-catalogue
  (:require [clojure.test :refer :all]
            [rems.api.services.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- status-flags [item-id]
  (-> (catalogue/get-localized-catalogue-item item-id)
      (select-keys [:enabled :archived])))

(deftest test-update-catalogue-item!
  (let [item-id (test-data/create-catalogue-item! {})
        item-id2 (test-data/create-catalogue-item! {})]

    (testing "new catalogue items are enabled and not archived"
      (is (= {:enabled true
              :archived false}
             (status-flags item-id))))

    ;; reset all to false for the following tests
    (catalogue/update-catalogue-item! {:id item-id
                                       :enabled false
                                       :archived false})

    (testing "enable"
      (catalogue/update-catalogue-item! {:id item-id
                                         :enabled true})
      (is (= {:enabled true
              :archived false}
             (status-flags item-id))))

    (testing "disable"
      (catalogue/update-catalogue-item! {:id item-id
                                         :enabled false})
      (is (= {:enabled false
              :archived false}
             (status-flags item-id))))

    (testing "archive"
      (catalogue/update-catalogue-item! {:id item-id
                                         :archived true})
      (is (= {:enabled false
              :archived true}
             (status-flags item-id))))

    (testing "unarchive"
      (catalogue/update-catalogue-item! {:id item-id
                                         :archived false})
      (is (= {:enabled false
              :archived false}
             (status-flags item-id))))

    (testing "does not affect unrelated catalogue items"
      (catalogue/update-catalogue-item! {:id item-id
                                         :enabled true
                                         :archived true})
      (catalogue/update-catalogue-item! {:id item-id2
                                         :enabled false
                                         :archived false})
      (is (= {:enabled true
              :archived true}
             (status-flags item-id)))
      (is (= {:enabled false
              :archived false}
             (status-flags item-id2))))))

(deftest test-get-localized-catalogue-items
  (let [item-id (test-data/create-catalogue-item! {})]

    (testing "find all"
      (is (= [item-id] (map :id (catalogue/get-localized-catalogue-items)))))

    (testing "archived catalogue items"
      (catalogue/update-catalogue-item! {:id item-id
                                         :archived true})
      (is (= [] (map :id (catalogue/get-localized-catalogue-items))))
      (is (= [item-id] (map :id (catalogue/get-localized-catalogue-items {:archived true}))))
      (is (= [] (map :id (catalogue/get-localized-catalogue-items {:archived false})))))))
