(ns rems.api.services.catalogue
  (:require [rems.api.services.util :as util]
            [rems.common-util :refer [index-by]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.catalogue :as catalogue]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
            [rems.db.workflow :as workflow]))

(defn- dependencies-for-catalogue-item [id]
  (let [{:keys [formid wfid resource-id]} (catalogue/get-localized-catalogue-item id)]
    {:form (form/get-form-template formid)
     :resource (resource/get-resource resource-id)
     :workflow (workflow/get-workflow wfid)
     :licenses (licenses/get-licenses {:wfid wfid
                                       :items [id]})}))

(defn create-catalogue-item! [{:keys [localizations organization] :as command}]
  (or (util/forbidden-organization-error organization)
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
        {:success (not (some nil? (cons id loc-ids)))
         :id id})))

(defn edit-catalogue-item! [{:keys [id localizations] :as command}]
  (doseq [[langcode localization] localizations]
    (db/upsert-catalogue-item-localization!
     (merge {:id id
             :langcode (name langcode)}
            (select-keys localization [:title :infourl]))))
  ;; Reset cache so that next call to get localizations will get these ones.
  (catalogue/reset-cache!)
  (applications/reload-cache!)
  {:success true})

(defn set-catalogue-item-enabled! [command]
  (db/set-catalogue-item-enabled! (select-keys command [:id :enabled]))
  {:success true})

(defn set-catalogue-item-archived! [{:keys [id archived]}]
  (let [{:keys [form resource workflow licenses]} (dependencies-for-catalogue-item id)
        archived-licenses (filter :archived licenses)
        errors
        (remove
         nil?
         [(when (:archived form)
            {:type :t.administration.errors/form-archived :forms [form]})
          (when (:archived resource)
            {:type :t.administration.errors/resource-archived :resources [resource]})
          (when (:archived workflow)
            {:type :t.administration.errors/workflow-archived :workflows [workflow]})
          (when (not (empty? archived-licenses))
            {:type :t.administration.errors/license-archived :licenses archived-licenses})])]
    (if (and (not archived)
             (not (empty? errors)))
      {:success false
       :errors errors}
      (do (db/set-catalogue-item-archived! {:id id
                                            :archived archived})
          {:success true}))))

(def get-localized-catalogue-items catalogue/get-localized-catalogue-items)
(def get-localized-catalogue-item catalogue/get-localized-catalogue-item)

(defn change-form!
  "Changes the form of a catalogue item.

  Since we don't want to modify the old item we must create
  a new item that is the copy of the old item except for the changed form."
  [item form-id]
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

      {:success true :catalogue-item-id (:id new-item)})))
