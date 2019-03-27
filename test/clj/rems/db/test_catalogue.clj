(ns ^:integration rems.db.test-catalogue
  (:require [clojure.test :refer :all]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.test-db :refer [db-each-fixture db-once-fixture]]))

(use-fixtures :once db-once-fixture)
(use-fixtures :each db-each-fixture)

(defn- status-flags [item-id]
  (-> (catalogue/get-localized-catalogue-item item-id)
      (select-keys [:enabled :archived])))

(deftest test-update-catalogue-item!
  (let [uid "test-user"
        form-id (:id (db/create-form! {:organization "org" :title "form with max lengths" :user uid}))
        wf-id (:id (db/create-workflow! {:organization "org" :modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 0}))
        item-id (:id (db/create-catalogue-item! {:title "item" :form form-id :resid nil :wfid wf-id}))
        item-id2 (:id (db/create-catalogue-item! {:title "item" :form form-id :resid nil :wfid wf-id}))]

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
  (let [uid "test-user"
        form-id (:id (db/create-form! {:organization "org" :title "form with max lengths" :user uid}))
        wf-id (:id (db/create-workflow! {:organization "org" :modifieruserid uid :owneruserid uid :title "Test workflow" :fnlround 0}))
        item-id (:id (db/create-catalogue-item! {:title "item" :form form-id :resid nil :wfid wf-id}))]

    (testing "find all"
      (is (contains? (set (map :id (catalogue/get-localized-catalogue-items)))
                     item-id)))

    (testing "archived catalogue items"
      (catalogue/update-catalogue-item! {:id item-id
                                         :archived true})
      (is (not (contains? (set (map :id (catalogue/get-localized-catalogue-items)))
                          item-id)))
      (is (contains? (set (map :id (catalogue/get-localized-catalogue-items {:archived true})))
                     item-id))
      (is (not (contains? (set (map :id (catalogue/get-localized-catalogue-items {:archived false})))
                          item-id))))))
