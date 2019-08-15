(ns rems.api.services.catalogue
  (:require [clojure.core.memoize :as memo]
            [rems.common-util :refer [index-by]]
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

(defn create-catalogue-item! [command]
  (let [id (:id (db/create-catalogue-item! (select-keys command [:title :form :resid :wfid :enabled :archived])))]
    {:success (not (nil? id))
     :id id}))

(defn create-catalogue-item-localization! [command]
  (let [return {:success (not (nil? (:id (db/create-catalogue-item-localization! (select-keys command [:id :langcode :title])))))}]
    ;; Reset cache so that next call to get localizations will get this one.
    (catalogue/reset-cache!)
    return))

(defn update-catalogue-item! [command]
  (let [{:keys [form resource workflow licenses]} (dependencies-for-catalogue-item (:id command))
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
    (if (and (not (:archived command))
             (not (empty? errors)))
      {:success false
       :errors errors}
      (do (db/set-catalogue-item-state! (select-keys command [:id :enabled :archived]))
          {:success true}))))

(def get-localized-catalogue-items catalogue/get-localized-catalogue-items)
(def get-localized-catalogue-item catalogue/get-localized-catalogue-item)
