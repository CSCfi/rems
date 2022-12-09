(ns rems.service.catalogue
  (:require [medley.core :refer [assoc-some]]
            [rems.service.dependencies :as dependencies]
            [rems.service.util :as util]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.catalogue :as catalogue]
            [rems.db.category :as category]
            [rems.db.organizations :as organizations]
            [rems.common.util :refer [build-dags]]))

;; TODO this bypasses the db layer
;; TODO move catalogue item localizations into the catalogueitemdata
(defn create-catalogue-item! [{:keys [localizations organization] :as command}]
  (util/check-allowed-organization! organization)
  (let [id (:id (db/create-catalogue-item! (merge {:organization (:organization/id organization "default")}
                                                  (select-keys command [:form :resid :wfid :enabled :archived :start])
                                                  {:catalogueitemdata (catalogue/catalogueitemdata->json command)})))
        loc-ids
        (doall
         (for [[langcode localization] localizations]
           (:id (db/upsert-catalogue-item-localization! {:id id
                                                         :langcode (name langcode)
                                                         :title (:title localization)
                                                         :infourl (:infourl localization)}))))]
    ;; New dependencies introduced
    (dependencies/reset-cache!)
    ;; Reset cache so that next call to get localizations will get these ones.
    (catalogue/reset-cache!)
    {:success (not (some nil? (cons id loc-ids)))
     :id id}))

(defn- join-dependencies [item]
  (when item
    (-> item
        organizations/join-organization
        (update :categories category/enrich-categories)
        ;; XXX: not used at the moment
        #_licenses/join-catalogue-item-licenses
        #_(transform [:licenses ALL] organizations/join-organization))))

(defn get-localized-catalogue-items [& [query-params]]
  (->> (catalogue/get-localized-catalogue-items (or query-params {}))
       (mapv join-dependencies)))

(defn get-localized-catalogue-item [id]
  (->> (catalogue/get-localized-catalogue-item id)
       join-dependencies))

(defn get-catalogue-tree [& [query-params]]
  (let [catalogue-items (get-localized-catalogue-items query-params)
        has-category? (fn [item category]
                        (contains? (set (map :category/id (:categories item)))
                                   (:category/id category)))
        categories (for [category (category/get-categories)
                         :let [matching-items (filterv #(has-category? % category) catalogue-items)]]
                     (assoc-some category :category/items matching-items))
        include-category? (fn [category]
                            (case (:empty query-params)
                              nil true ; not set, include all
                              false (or (seq (:category/children category))
                                        (seq (:category/items category)))
                              true (and (empty? (:category/children category))
                                        (empty? (:category/items category)))))
        items-without-category (for [item catalogue-items
                                     :when (empty? (:categories item))]
                                 item)
        top-level-categories (build-dags {:id-fn :category/id
                                          :child-id-fn :category/id
                                          :children-fn :category/children
                                          :filter-fn include-category?}
                                         categories)]
    {:roots (into (vec top-level-categories)
                  items-without-category)}))

(defn- check-allowed-to-edit! [id]
  (-> id
      get-localized-catalogue-item
      :organization
      util/check-allowed-organization!))

(defn edit-catalogue-item! [{:keys [id localizations organization] :as item}]
  (check-allowed-to-edit! id)
  (when (:organization/id organization)
    (util/check-allowed-organization! organization)
    (db/set-catalogue-item-organization! {:id id
                                          :organization (:organization/id organization)}))
  (doseq [[langcode localization] localizations]
    (db/upsert-catalogue-item-localization!
     (merge {:id id
             :langcode (name langcode)}
            (select-keys localization [:title :infourl]))))
  (when-let [catalogueitemdata (catalogue/catalogueitemdata->json item)]
    (db/set-catalogue-item-data! {:id id
                                  :catalogueitemdata catalogueitemdata}))
  ;; Reset cache so that next call to get localizations will get these ones.
  (catalogue/reset-cache!)
  (applications/reload-cache!)
  (dependencies/reset-cache!)
  {:success true})

(defn set-catalogue-item-enabled! [{:keys [id enabled]}]
  (check-allowed-to-edit! id)
  ;; Clear endt in case it has been set in the db. Otherwise we might
  ;; end up with an enabled item that's not active and can't be made
  ;; active via the UI.
  (db/set-catalogue-item-endt! {:id id :end nil})
  (db/set-catalogue-item-enabled! {:id id :enabled enabled})
  {:success true})

(defn set-catalogue-item-archived! [{:keys [id archived]}]
  (check-allowed-to-edit! id)
  (or (dependencies/change-archive-status-error archived {:catalogue-item/id id})
      (do (db/set-catalogue-item-archived! {:id id
                                            :archived archived})
          {:success true})))

(defn change-form!
  "Changes the form of a catalogue item.

  Since we don't want to modify the old item we must create
  a new item that is the copy of the old item except for the changed form."
  [item form-id]
  (util/check-allowed-organization! (:organization item))
  (if (= (:formid item) form-id)
    {:success true :catalogue-item-id (:id item)}
    ;; create a new item with the new form
    (let [new-item (db/create-catalogue-item! {:enabled true
                                               :archived false
                                               :form form-id
                                               :organization (get-in item [:organization :organization/id])
                                               :resid (:resource-id item)
                                               :wfid (:wfid item)
                                               :catalogueitemdata (catalogue/catalogueitemdata->json item)})]

      ;; copy localizations
      (doseq [[langcode localization] (:localizations item)]
        (db/upsert-catalogue-item-localization! {:id (:id new-item)
                                                 :langcode (name langcode)
                                                 :title (:title localization)
                                                 :infourl (:infourl localization)}))
      ;; Reset cache so that next call to get localizations will get these ones.
      (catalogue/reset-cache!)

      ;; hide the old catalogue item
      (db/set-catalogue-item-enabled! {:id (:id item) :enabled false})
      (db/set-catalogue-item-archived! {:id (:id item) :archived true})

      ;; New dependencies introduced
      (dependencies/reset-cache!)

      {:success true :catalogue-item-id (:id new-item)})))
