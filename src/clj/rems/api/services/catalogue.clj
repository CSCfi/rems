(ns rems.api.services.catalogue
  (:require [rems.api.services.dependencies :as dependencies]
            [rems.api.services.util :as util]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.catalogue :as catalogue]
            [rems.db.organizations :as organizations]))

(defn create-catalogue-item! [{:keys [localizations organization] :as command}]
  (util/check-allowed-organization! organization)
  (let [id (:id (db/create-catalogue-item! (merge {:organization (:organization/id organization "default")}
                                                  (select-keys command [:form :resid :wfid :enabled :archived]))))
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
    (->> item
         organizations/join-organization
         ;; not used at the moment
         #_licenses/join-catalogue-item-licenses
         #_(transform [:licenses ALL] organizations/join-organization))))

(defn get-localized-catalogue-items [& [query-params]]
  (->> (catalogue/get-localized-catalogue-items (or query-params {}))
       (mapv join-dependencies)))

(defn get-localized-catalogue-item [id]
  (->> (catalogue/get-localized-catalogue-item id)
       join-dependencies))

(defn- check-allowed-to-edit! [id]
  (-> id
      get-localized-catalogue-item
      :organization
      util/check-allowed-organization!))

(defn edit-catalogue-item! [{:keys [id localizations]}]
  (check-allowed-to-edit! id)
  (doseq [[langcode localization] localizations]
    (db/upsert-catalogue-item-localization!
     (merge {:id id
             :langcode (name langcode)}
            (select-keys localization [:title :infourl]))))
  ;; Reset cache so that next call to get localizations will get these ones.
  (catalogue/reset-cache!)
  (applications/reload-cache!)
  {:success true})

(defn set-catalogue-item-enabled! [{:keys [id enabled]}]
  (check-allowed-to-edit! id)
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
                                               :wfid (:wfid item)})]

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
