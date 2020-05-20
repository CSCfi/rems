(ns rems.api.services.resource
  (:require [com.rpl.specter :refer [ALL transform]]
            [rems.api.services.dependencies :as dependencies]
            [rems.api.services.util :as util]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.organizations :as organizations]
            [rems.db.resource :as resource])
  (:import (org.postgresql.util PSQLException)))

(defn- join-dependencies [resource]
  (when resource
    (->> resource
         organizations/join-organization
         licenses/join-resource-licenses
         (transform [:licenses ALL] organizations/join-organization))))

(defn get-resource [id]
  (->> (resource/get-resource id)
       join-dependencies))

(defn get-resources [filters]
  (->> (resource/get-resources filters)
       (mapv join-dependencies)))

(defn create-resource! [{:keys [resid organization licenses] :as command} user-id]
  (util/check-allowed-organization! organization)
  (let [id (:id (db/create-resource! {:resid resid
                                      :organization (:organization/id organization)
                                      :owneruserid user-id
                                      :modifieruserid user-id}))]
    (doseq [licid licenses]
      (db/create-resource-license! {:resid id
                                    :licid licid}))
    ;; reset-cache! not strictly necessary since resources don't depend on anything, but here for consistency
    (dependencies/reset-cache!)
    {:success true
     :id id}))

(defn set-resource-enabled! [{:keys [id enabled]}]
  (util/check-allowed-organization! (:organization (get-resource id)))
  (db/set-resource-enabled! {:id id :enabled enabled})
  {:success true})

(defn set-resource-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:organization (get-resource id)))
  (or (dependencies/change-archive-status-error archived {:resource/id id})
      (do
        (db/set-resource-archived! {:id id
                                    :archived archived})
        {:success true})))
