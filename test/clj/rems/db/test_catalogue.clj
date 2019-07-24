(ns ^:integration rems.db.test-catalogue
  (:require [clojure.test :refer :all]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- status-flags [item-id]
  (-> (catalogue/get-localized-catalogue-item item-id)
      (select-keys [:enabled :archived])))

(deftest test-update-catalogue-item!
  (let [uid "test-user"
        form-id (:id (form/create-form! uid {:form/organization "org" :form/title "" :form/fields []}))
        wf-id (:id (db/create-workflow! {:organization "org" :modifieruserid uid :owneruserid uid :title "Test workflow"}))
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
        form-id (:id (form/create-form! uid {:form/organization "org" :form/title "" :form/fields []}))
        wf-id (:id (db/create-workflow! {:organization "org" :modifieruserid uid :owneruserid uid :title "Test workflow"}))
        item-id (:id (db/create-catalogue-item! {:title "item" :form form-id :resid nil :wfid wf-id}))]

    (testing "find all"
      (is (= [item-id] (map :id (catalogue/get-localized-catalogue-items)))))

    (testing "archived catalogue items"
      (catalogue/update-catalogue-item! {:id item-id
                                         :archived true})
      (is (= [] (map :id (catalogue/get-localized-catalogue-items))))
      (is (= [item-id] (map :id (catalogue/get-localized-catalogue-items {:archived true}))))
      (is (= [] (map :id (catalogue/get-localized-catalogue-items {:archived false})))))))
