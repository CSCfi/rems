(ns ^:integration rems.api.services.test-catalogue
  (:require [clojure.test :refer :all]
            [rems.api.services.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.resource :as resource]
            [rems.api.services.workflow :as workflow]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [caches-fixture rollback-db-fixture test-db-fixture]]))

(use-fixtures :once test-db-fixture caches-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- status-flags [item-id]
  (-> (catalogue/get-localized-catalogue-item item-id)
      (select-keys [:enabled :archived])))

(deftest catalogue-item-enabled-archived-test
  (let [form-id (test-data/create-form! {})
        lic-id (test-data/create-license! {})
        res-id (test-data/create-resource! {:license-ids [lic-id]})
        workflow-id (test-data/create-workflow! {})
        item-id (test-data/create-catalogue-item! {:resource-id res-id
                                                   :form-id form-id
                                                   :workflow-id workflow-id})
        item-id2 (test-data/create-catalogue-item! {})

        enable-catalogue-item!
        #(catalogue/set-catalogue-item-enabled! {:id item-id
                                                 :enabled %})
        archive-catalogue-item!
        #(catalogue/set-catalogue-item-archived! {:id item-id
                                                  :archived %})
        archive-form! #(form/set-form-archived! {:id form-id
                                                 :archived %})
        archive-license! #(licenses/set-license-archived! {:id lic-id
                                                           :archived %})
        archive-resource! #(resource/set-resource-archived! {:id res-id
                                                             :archived %})
        archive-workflow! #(workflow/set-workflow-archived! {:id workflow-id
                                                             :archived %})]
    (testing "new catalogue items are enabled and not archived"
      (is (= {:enabled true
              :archived false}
             (status-flags item-id))))

    ;; reset all to false for the following tests
    (enable-catalogue-item! false)
    (archive-catalogue-item! false)

    (testing "enable"
      (enable-catalogue-item! true)
      (is (= {:enabled true
              :archived false}
             (status-flags item-id))))

    (testing "disable"
      (enable-catalogue-item! false)
      (is (= {:enabled false
              :archived false}
             (status-flags item-id))))

    (testing "archive"
      (archive-catalogue-item! true)
      (is (= {:enabled false
              :archived true}
             (status-flags item-id))))

    (testing "unarchive"
      (archive-catalogue-item! false)
      (is (= {:enabled false
              :archived false}
             (status-flags item-id))))

    (testing "does not affect unrelated catalogue items"
      (enable-catalogue-item! true)
      (archive-catalogue-item! true)
      (catalogue/set-catalogue-item-enabled! {:id item-id2 :enabled false})
      (catalogue/set-catalogue-item-archived! {:id item-id2 :archived false})
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

    (testing "cannot unarchive if resource and license are archived"
      (archive-catalogue-item! true)
      (archive-resource! true)
      (archive-license! true)
      (let [errors (:errors (archive-catalogue-item! false))]
        (is (= #{:t.administration.errors/resource-archived
                 :t.administration.errors/license-archived}
               (set (mapv :type errors)))))
      (archive-license! false)
      (archive-resource! false)
      (is (:success (archive-catalogue-item! true))))))

(deftest test-edit-catalogue-item
  (let [item-id (test-data/create-catalogue-item!
                 {:title {:en "Old title"
                          :fi "Vanha nimi"}})
        old-item (first (catalogue/get-localized-catalogue-items))

        _ (catalogue/edit-catalogue-item!
           {:id item-id
            :localizations {:en {:title "New title"}
                            :fi {:title "Uusi nimi"}}})
        new-item (first (catalogue/get-localized-catalogue-items))]
    (is (= "Old title" (get-in old-item [:localizations :en :title])))
    (is (= "Vanha nimi" (get-in old-item [:localizations :fi :title])))
    (is (= "New title" (get-in new-item [:localizations :en :title])))
    (is (= "Uusi nimi" (get-in new-item [:localizations :fi :title])))))

(deftest test-get-localized-catalogue-items
  (let [item-id (test-data/create-catalogue-item! {})]

    (testing "find all"
      (is (= [item-id] (map :id (catalogue/get-localized-catalogue-items)))))

    (testing "archived catalogue items"
      (catalogue/set-catalogue-item-archived! {:id item-id
                                               :archived true})
      (is (= [] (map :id (catalogue/get-localized-catalogue-items))))
      (is (= [item-id] (map :id (catalogue/get-localized-catalogue-items {:archived true}))))
      (is (= [] (map :id (catalogue/get-localized-catalogue-items {:archived false})))))))
