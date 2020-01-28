(ns rems.api.services.resource
  (:require [rems.api.services.util :as util]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource])
  (:import (org.postgresql.util PSQLException)))

(defn- duplicate-resid? [^Exception e]
  (let [cause (.getCause e)]
    (and (instance? PSQLException cause)
         (.getServerErrorMessage ^PSQLException cause)
         (= "duplicate key value violates unique constraint \"resource_resid_u\""
            (.getMessage (.getServerErrorMessage ^PSQLException cause))))))

(defn create-resource! [{:keys [resid organization licenses]} user-id]
  (try
    (or (util/forbidden-organization-error? user-id organization)
        (let [id (:id (db/create-resource! {:resid resid
                                            :organization organization
                                            :owneruserid user-id
                                            :modifieruserid user-id}))]
          (doseq [licid licenses]
            (db/create-resource-license! {:resid id
                                          :licid licid}))
          {:success true
           :id id}))
    (catch Exception e
      (if (duplicate-resid? e)
        {:success false
         :errors [{:type :t.administration.errors/duplicate-resid :resid resid}]}
        (throw e)))))

(defn set-resource-enabled! [command]
  (db/set-resource-enabled! (select-keys command [:id :enabled]))
  {:success true})

(defn set-resource-archived! [{:keys [id archived]}]
  (let [catalogue-items
        (->> (catalogue/get-localized-catalogue-items {:resource-id id
                                                       :archived false})
             (map #(select-keys % [:id :title :localizations])))
        licenses (licenses/get-resource-licenses id)
        archived-licenses (filter :archived licenses)]
    (cond
      (and archived (seq catalogue-items))
      {:success false
       :errors [{:type :t.administration.errors/resource-in-use
                 :catalogue-items catalogue-items}]}

      (and (not archived) (seq archived-licenses))
      {:success false
       :errors [{:type :t.administration.errors/license-archived
                 :licenses archived-licenses}]}

      :else
      (do
        (db/set-resource-archived! {:id id
                                    :archived archived})
        {:success true}))))

(defn get-resource [id user-id] (resource/get-resource id user-id))
(defn get-resources [filters user-id] (resource/get-resources filters user-id))
