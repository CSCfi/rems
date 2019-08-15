(ns ^:integration rems.api.services.test-catalogue
  (:require [clojure.test :refer :all]
            [rems.api.services.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.resource :as resource]
            [rems.api.services.workflow :as workflow]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- status-flags [item-id]
  (-> (catalogue/get-localized-catalogue-item item-id)
      (select-keys [:enabled :archived])))

(deftest test-update-catalogue-item!
  (let [form-id (test-data/create-form! {})
        lic-id (test-data/create-license! {})
        res-id (test-data/create-resource! {:license-ids [lic-id]})
        workflow-id (test-data/create-dynamic-workflow! {})
        item-id (test-data/create-catalogue-item! {:resource-id res-id
                                                   :form-id form-id
                                                   :workflow-id workflow-id})
        item-id2 (test-data/create-catalogue-item! {})

        archive-catalogue-item!
        #(catalogue/update-catalogue-item! {:id item-id
                                            :enabled true
                                            :archived %})
        archive-form! #(form/update-form! {:id form-id
                                           :enabled true
                                           :archived %})
        archive-license! #(licenses/update-license! {:id lic-id
                                                     :enabled true
                                                     :archived %})
        archive-resource! #(resource/update-resource! {:id res-id
                                                       :enabled true
                                                       :archived %})
        archive-workflow! #(workflow/update-workflow! {:id workflow-id
                                                       :enabled true
                                                       :archived %})]
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
             (status-flags item-id2))))

    (testing "cannot unarchive if form is archived"
      (archive-catalogue-item! true)
      (archive-form! true)
      (is (not (:success (archive-catalogue-item! false))))
      (archive-form! false)
      (is (:success (archive-catalogue-item! true))))

    (testing "cannot unarchive if resource is archived"
      (archive-catalogue-item! true)
      (archive-resource! true)
      (is (not (:success (archive-catalogue-item! false))))
      (archive-resource! false)
      (is (:success (archive-catalogue-item! true))))

    (testing "cannot unarchive if workflow is archived"
      (archive-catalogue-item! true)
      (archive-workflow! true)
      (is (not (:success (archive-catalogue-item! false))))
      (archive-workflow! false)
      (is (:success (archive-catalogue-item! true))))

    (testing "cannot unarchive if license is archived"
      (archive-catalogue-item! true)
      (archive-resource! true)
      (archive-license! true)
      (let [errors (:errors (archive-catalogue-item! false))]
        (is (= (set (mapv :type errors))
               #{:t.administration.errors/resource-archived
                 :t.administration.errors/license-archived})))
      (archive-license! false)
      (archive-resource! false)
      (is (:success (archive-catalogue-item! true))))))

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
