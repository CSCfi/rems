(ns ^:integration rems.api.services.test-catalogue
  (:require [clojure.test :refer :all]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.services.form :as form]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.resource :as resource]
            [rems.api.services.workflow :as workflow]
            [rems.db.core :as db]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [caches-fixture rollback-db-fixture test-db-fixture]]
            [rems.testing-util :refer [with-user]])
  (:import org.joda.time.DateTime))

(use-fixtures :once test-db-fixture caches-fixture)
(use-fixtures :each rollback-db-fixture)

(defn- status-flags [item-id]
  (-> (catalogue/get-localized-catalogue-item item-id)
      (select-keys [:enabled :archived])))

(deftest catalogue-item-enabled-archived-test
  (let [owner "owner"
        _org (test-helpers/create-organization! {})
        form-id (test-helpers/create-form! {})
        lic-id (test-helpers/create-license! {})
        res-id (test-helpers/create-resource! {:resource-ext-id "ext" :license-ids [lic-id]})
        workflow-id (test-helpers/create-workflow! {})
        item-id (test-helpers/create-catalogue-item! {:resource-id res-id
                                                      :form-id form-id
                                                      :workflow-id workflow-id})
        item-id2 (test-helpers/create-catalogue-item! {})

        enable-catalogue-item! #(with-user owner
                                  (catalogue/set-catalogue-item-enabled! {:id item-id
                                                                          :enabled %}))
        archive-catalogue-item! #(with-user owner
                                   (catalogue/set-catalogue-item-archived! {:id item-id
                                                                            :archived %}))
        archive-form! #(with-user owner
                         (form/set-form-archived! {:id form-id
                                                   :archived %}))
        archive-license! #(with-user owner
                            (licenses/set-license-archived! {:id lic-id
                                                             :archived %}))
        archive-resource! #(with-user owner
                             (resource/set-resource-archived! {:id res-id
                                                               :archived %}))
        archive-workflow! #(with-user owner
                             (workflow/set-workflow-archived! {:id workflow-id
                                                               :archived %}))]
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

    (testing "enable unsets end time"
      (db/set-catalogue-item-endt! {:id item-id :end (DateTime. 1)})
      (is (:expired (catalogue/get-localized-catalogue-item item-id)))
      (enable-catalogue-item! true)
      (is (= {:enabled true
              :archived false}
             (status-flags item-id)))
      (is (not (:expired (catalogue/get-localized-catalogue-item item-id))))
      (is (not (:end (catalogue/get-localized-catalogue-item item-id)))))

    (testing "disable doesn't set end time"
      (enable-catalogue-item! false)
      (is (= {:enabled false
              :archived false}
             (status-flags item-id)))
      (is (not (:expired (catalogue/get-localized-catalogue-item item-id))))
      (is (not (:end (catalogue/get-localized-catalogue-item item-id)))))

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
      (with-user owner
        (catalogue/set-catalogue-item-enabled! {:id item-id2 :enabled false})
        (catalogue/set-catalogue-item-archived! {:id item-id2 :archived false}))
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
      ;; TODO indirect catalogue item -> resource -> license dep not tracked right now
      (is (= {:success false
              :errors [{:type :t.administration.errors/dependencies-archived
                        :resources [{:id res-id :resid "ext"}]}]}
             (archive-catalogue-item! false)))
      (archive-license! false)
      (archive-resource! false)
      (is (:success (archive-catalogue-item! true))))))

(deftest test-edit-catalogue-item
  (let [_org (test-helpers/create-organization! {})
        item-id (test-helpers/create-catalogue-item!
                 {:title {:en "Old title"
                          :fi "Vanha nimi"}})
        old-item (first (catalogue/get-localized-catalogue-items))

        _ (with-user "owner"
            (catalogue/edit-catalogue-item!
             {:id item-id
              :localizations {:en {:title "New title"}
                              :fi {:title "Uusi nimi"}}}))
        new-item (first (catalogue/get-localized-catalogue-items))]
    (is (= "Old title" (get-in old-item [:localizations :en :title])))
    (is (= "Vanha nimi" (get-in old-item [:localizations :fi :title])))
    (is (= "New title" (get-in new-item [:localizations :en :title])))
    (is (= "Uusi nimi" (get-in new-item [:localizations :fi :title])))))

(deftest test-get-localized-catalogue-items
  (let [owner "owner"
        _org (test-helpers/create-organization! {})
        item-id (test-helpers/create-catalogue-item! {})]

    (testing "find all"
      (is (= [item-id] (map :id (catalogue/get-localized-catalogue-items)))))

    (testing "archived catalogue items"
      (with-user owner
        (catalogue/set-catalogue-item-archived! {:id item-id
                                                 :archived true}))
      (is (= [] (map :id (catalogue/get-localized-catalogue-items))))
      (is (= [item-id] (map :id (catalogue/get-localized-catalogue-items {:archived true}))))
      (is (= [] (map :id (catalogue/get-localized-catalogue-items {:archived false})))))))
