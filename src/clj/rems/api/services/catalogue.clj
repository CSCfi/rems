(ns rems.api.services.catalogue
  (:require [rems.common-util :refer [index-by]]
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

(defn create-catalogue-item! [{:keys [localizations] :as command}]
  (let [id (:id (db/create-catalogue-item! (select-keys command [:form :resid :wfid :enabled :archived])))
        loc-ids
        (doall
         (for [[langcode localization] localizations]
           (:id (db/create-catalogue-item-localization! {:id id
                                                         :langcode (name langcode)
                                                         :title (:title localization)}))))]
    ;; Reset cache so that next call to get localizations will get these ones.
    (catalogue/reset-cache!)
    {:success (not (some nil? (cons id loc-ids)))
     :id id}))

(defn edit-catalogue-item! [{:keys [id localizations] :as command}]
  (doseq [[langcode localization] localizations]
    (db/edit-catalogue-item-localization! {:id id
                                           :langcode (name langcode)
                                           :title (:title localization)}))
  ;; Reset cache so that next call to get localizations will get these ones.
  (catalogue/reset-cache!)
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
