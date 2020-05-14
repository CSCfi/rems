(ns rems.api.services.catalogue
  (:require [rems.api.services.dependencies :as dependencies]
            [rems.api.services.util :as util]
            [rems.common.util :refer [index-by]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.catalogue :as catalogue]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
            [rems.db.workflow :as workflow]))

(defn create-catalogue-item! [{:keys [localizations organization] :as command}]
  (util/check-allowed-organization! organization)
  ;; TODO make :organization unoptional?
  (let [id (:id (db/create-catalogue-item! (merge {:organization ""}
                                                  (select-keys command [:form :resid :wfid :enabled :archived :organization]))))
        loc-ids
        (doall
         (for [[langcode localization] localizations]
           (:id (db/upsert-catalogue-item-localization! {:id id
                                                         :langcode (name langcode)
                                                         :title (:title localization)
                                                         :infourl (:infourl localization)}))))]
    ;; Reset cache so that next call to get localizations will get these ones.
    (catalogue/reset-cache!)
    (dependencies/reset-cache!)
    {:success (not (some nil? (cons id loc-ids)))
     :id id}))

(def get-localized-catalogue-items catalogue/get-localized-catalogue-items)
(def get-localized-catalogue-item catalogue/get-localized-catalogue-item)

(defn- check-allowed-to-edit! [id]
  (-> id
      get-localized-catalogue-item
      :organization
      util/check-allowed-organization!))

(defn edit-catalogue-item! [{:keys [id localizations] :as command}]
  (check-allowed-to-edit! id)
  (doseq [[langcode localization] localizations]
    (db/upsert-catalogue-item-localization!
     (merge {:id id
             :langcode (name langcode)}
            (select-keys localization [:title :infourl]))))
  ;; Reset cache so that next call to get localizations will get these ones.
  (catalogue/reset-cache!)
  (dependencies/reset-cache!)
  (applications/reload-cache!)
  {:success true})

(defn set-catalogue-item-enabled! [{:keys [id enabled]}]
  (check-allowed-to-edit! id)
  (db/set-catalogue-item-enabled! {:id id :enabled enabled})
  (dependencies/reset-cache!)
  {:success true})

(defn set-catalogue-item-archived! [{:keys [id archived]}]
  (check-allowed-to-edit! id)
  (if-let [errors (when-not archived
                    (dependencies/unarchive-errors {:catalogue-item/id id}))]
    {:success false
     :errors errors}
    (do (db/set-catalogue-item-archived! {:id id
                                          :archived archived})
        (dependencies/reset-cache!)
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
                                               :organization (:organization item)
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

      ;; end the old catalogue item
      (db/set-catalogue-item-enabled! {:id (:id item) :enabled false})
      (db/set-catalogue-item-archived! {:id (:id item) :archived true})
      (db/set-catalogue-item-endt! {:id (:id item) :end (:start new-item)})

      ;; clear dependencies cache after editing status bits
      (dependencies/reset-cache!)

      {:success true :catalogue-item-id (:id new-item)})))
