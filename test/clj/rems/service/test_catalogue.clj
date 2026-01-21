(ns ^:integration rems.service.test-catalogue
  (:require [clojure.test :refer :all]
            [rems.db.catalogue]
            [rems.db.category]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [rollback-db-fixture test-db-fixture]]
            [rems.service.catalogue]
            [rems.service.form]
            [rems.service.licenses]
            [rems.service.resource]
            [rems.service.workflow]
            [rems.testing-util :refer [with-user]])
  (:import org.joda.time.DateTime))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(def ^:private owner "owner")

(defn- get-catalogue-item [id]
  (with-user owner
    (rems.service.catalogue/get-catalogue-item id)))

(defn- get-catalogue-items [& [opts]]
  (with-user owner
    (rems.service.catalogue/get-catalogue-items opts)))

(defn- get-catalogue-tree [& [opts]]
  (with-user owner
    (rems.service.catalogue/get-catalogue-tree opts)))

(defn- status-flags [item-id]
  (select-keys (get-catalogue-item item-id) [:enabled :archived]))

(deftest catalogue-item-enabled-archived-test
  (let [_org (test-helpers/create-organization! {})
        form-id (test-helpers/create-form! {:form/external-title {:en "form" :fi "form" :sv "form"}
                                            :form/internal-name "catalogue-item-enabled-archived-test-form"})
        lic-id (test-helpers/create-license! {})
        res-id (test-helpers/create-resource! {:resource-ext-id "ext" :license-ids [lic-id]})
        workflow-id (test-helpers/create-workflow! {:title "workflow"})
        item-id (test-helpers/create-catalogue-item! {:resource-id res-id
                                                      :form-id form-id
                                                      :workflow-id workflow-id})
        item-id2 (test-helpers/create-catalogue-item! {})

        enable-catalogue-item! #(with-user owner
                                  (rems.service.catalogue/set-catalogue-item-enabled! {:id item-id
                                                                                       :enabled %}))
        archive-catalogue-item! #(with-user owner
                                   (rems.service.catalogue/set-catalogue-item-archived! {:id item-id
                                                                                         :archived %}))
        archive-form! #(with-user owner
                         (rems.service.form/set-form-archived! {:id form-id
                                                                :archived %}))
        archive-license! #(with-user owner
                            (rems.service.licenses/set-license-archived! {:id lic-id
                                                                          :archived %}))
        archive-resource! #(with-user owner
                             (rems.service.resource/set-resource-archived! {:id res-id
                                                                            :archived %}))
        archive-workflow! #(with-user owner
                             (rems.service.workflow/set-workflow-archived! {:id workflow-id
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
      (rems.db.catalogue/set-attributes! item-id {:endt (DateTime. 1)})
      (is (:expired (get-catalogue-item item-id)))
      (enable-catalogue-item! true)
      (is (= {:enabled true
              :archived false}
             (status-flags item-id)))
      (is (not (:expired (get-catalogue-item item-id))))
      (is (not (:end (get-catalogue-item item-id)))))

    (testing "disable doesn't set end time"
      (enable-catalogue-item! false)
      (is (= {:enabled false
              :archived false}
             (status-flags item-id)))
      (is (not (:expired (get-catalogue-item item-id))))
      (is (not (:end (get-catalogue-item item-id)))))

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
        (rems.service.catalogue/set-catalogue-item-enabled! {:id item-id2 :enabled false})
        (rems.service.catalogue/set-catalogue-item-archived! {:id item-id2 :archived false}))
      (is (= {:enabled true
              :archived true}
             (status-flags item-id)))
      (is (= {:enabled false
              :archived false}
             (status-flags item-id2))))

    (testing "cannot unarchive if form is archived"
      (archive-catalogue-item! true)
      (archive-form! true)
      (let [response (archive-catalogue-item! false)]
        (is (= {:success false
                :errors [{:type :t.administration.errors/dependencies-archived
                          :forms [{:form/id form-id
                                   :form/external-title {:en "form" :fi "form" :sv "form"}
                                   :form/internal-name "catalogue-item-enabled-archived-test-form"}]}]}
               response)))

      (archive-form! false)
      (let [response (archive-catalogue-item! true)]
        (is (:success response))))

    (testing "cannot unarchive if resource is archived"
      (archive-catalogue-item! true)
      (archive-resource! true)
      (is (= {:success false
              :errors [{:type :t.administration.errors/dependencies-archived
                        :resources [{:resource/id res-id :resid "ext"}]}]}
             (archive-catalogue-item! false)))

      (archive-resource! false)
      (is (= {:success true}
             (archive-catalogue-item! true))))

    (testing "cannot unarchive if workflow is archived"
      (archive-catalogue-item! true)
      (archive-workflow! true)
      (is (= {:success false
              :errors [{:type :t.administration.errors/dependencies-archived
                        :workflows [{:workflow/id workflow-id :title "workflow"}]}]}
             (archive-catalogue-item! false)))

      (archive-workflow! false)
      (is (:success (archive-catalogue-item! true))))

    (testing "cannot unarchive if resource and license are archived"
      (archive-catalogue-item! true)
      (archive-resource! true)
      (archive-license! true)
      (is (= {:success false
              :errors [{:type :t.administration.errors/dependencies-archived
                        :resources [{:resource/id res-id :resid "ext"}]
                        :licenses [{:license/id lic-id :localizations {}}]}]}
             (archive-catalogue-item! false)))

      (archive-license! false)
      (archive-resource! false)
      (is (:success (archive-catalogue-item! true))))))

(deftest test-edit-catalogue-item
  (let [_org (test-helpers/create-organization! {})
        item-id (test-helpers/create-catalogue-item!
                 {:title {:en "Old title"
                          :fi "Vanha nimi"}})
        old-item (first (get-catalogue-items))

        _ (with-user "owner"
            (rems.service.catalogue/edit-catalogue-item!
             {:id item-id
              :localizations {:en {:title "New title"}
                              :fi {:title "Uusi nimi"}}}))
        new-item (first (get-catalogue-items))]
    (is (= "Old title" (get-in old-item [:localizations :en :title])))
    (is (= "Vanha nimi" (get-in old-item [:localizations :fi :title])))
    (is (= "New title" (get-in new-item [:localizations :en :title])))
    (is (= "Uusi nimi" (get-in new-item [:localizations :fi :title])))))

(deftest test-get-catalogue-items
  (let [owner "owner"
        _org (test-helpers/create-organization! {})
        item-id (test-helpers/create-catalogue-item! {})]

    (testing "find all"
      (is (= [item-id] (map :id (get-catalogue-items)))))

    (testing "archived catalogue items"
      (with-user owner
        (rems.service.catalogue/set-catalogue-item-archived! {:id item-id
                                                              :archived true}))
      (is (= []
             (mapv :id (get-catalogue-items))
             (mapv :id (get-catalogue-items {:archived false}))))
      (is (= [item-id] (mapv :id (get-catalogue-items {:archived true})))))))

(deftest test-get-catalogue-tree
  (is (= {:roots []}
         (get-catalogue-tree)))

  (let [get-category (fn [category]
                       (rems.db.category/get-category (:category/id category)))
        child {:category/id (test-helpers/create-category! {})}
        parent {:category/id (test-helpers/create-category! {:category/children [child]})}
        _empty {:category/id (test-helpers/create-category! {})} ; should not be seen
        item1 (get-catalogue-item (test-helpers/create-catalogue-item! {}))
        item2 (get-catalogue-item (test-helpers/create-catalogue-item! {:enabled false}))
        item3 (get-catalogue-item (test-helpers/create-catalogue-item! {:categories []}))
        item4 (assoc (get-catalogue-item (test-helpers/create-catalogue-item! {:categories [child]}))
                     :categories [(get-category child)])
        item5 (get-catalogue-item (test-helpers/create-catalogue-item! {:categories [] :enabled false}))
        item6 (assoc (get-catalogue-item (test-helpers/create-catalogue-item! {:categories [child] :enabled false}))
                     :categories [(get-category child)])
        item7 (assoc (get-catalogue-item (test-helpers/create-catalogue-item! {:categories [parent]}))
                     :categories [(get-category parent)])
        item8 (assoc (get-catalogue-item (test-helpers/create-catalogue-item! {:categories [parent] :enabled false}))
                     :categories [(get-category parent)])]

    (is (= {:roots [(assoc (get-category parent)
                           :category/children [(assoc (get-category child) :category/items [item4 item6])]
                           :category/items [item7 item8])
                    item1
                    item2
                    item3
                    item5]}
           (get-catalogue-tree {:archived false
                                :empty false})))

    (testing "showing only enabled"
      (is (= {:roots [(assoc (get-category parent)
                             :category/children [(assoc (get-category child) :category/items [item4
                                                                                              ;; item 6 is not seen as it's not enabled
                                                                                              ])]
                             :category/items [item7])
                      item1
                      ;; item 2 is not seen as it's not enabled
                      item3]}
             (get-catalogue-tree {:archived false
                                  :empty false
                                  :enabled true}))))

    (testing "disabling more items"
      (with-user "owner"
        (rems.service.catalogue/set-catalogue-item-enabled! {:id (:id item1) :enabled false}) ; top level
        (rems.service.catalogue/set-catalogue-item-enabled! {:id (:id item4) :enabled false})) ; inside category

      (is (= {:roots [(-> (get-category parent)
                          (assoc :category/items [item7])
                          (dissoc :category/children)) ; child does not have visible items anymore
                      item3]}
             (get-catalogue-tree {:archived false
                                  :empty false
                                  :enabled true}))))))
