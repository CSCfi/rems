(ns rems.service.resource
  (:require [com.rpl.specter :refer [ALL transform]]
            [rems.service.dependencies :as dependencies]
            [rems.service.util :as util]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.organizations :as organizations]
            [rems.db.resource :as resource]
            [rems.ext.duo :as duo]))

(defn- join-dependencies [resource]
  (when resource
    (->> resource
         organizations/join-organization
         licenses/join-resource-licenses
         (duo/join-duo-codes [:resource/duo :duo/codes])
         (transform [:licenses ALL] organizations/join-organization))))

(defn get-resource [id]
  (when-let [resource (resource/get-resource id)]
    (join-dependencies resource)))

(defn get-resources [filters]
  (->> (resource/get-resources filters)
       (mapv join-dependencies)))

(defn create-resource! [resource]
  (util/check-allowed-organization! (:organization resource))
  (let [id (resource/create-resource! resource)]
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
