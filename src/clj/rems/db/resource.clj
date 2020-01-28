(ns rems.db.resource
  (:require [rems.api.services.util :as util]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]))

(defn- format-resource
  [{:keys [id owneruserid modifieruserid organization resid enabled archived]}]
  {:id id
   :owneruserid owneruserid
   :modifieruserid modifieruserid
   :organization organization
   :resid resid
   :enabled enabled
   :archived archived})

(defn get-resource [id user-id]
  (let [resource (-> {:id id}
                     db/get-resource
                     format-resource
                     (assoc :licenses (licenses/get-resource-licenses id)))]
    (when-not (util/forbidden-organization? user-id (:organization resource))
      resource)))

(defn get-resources [filters user-id]
  (->> (db/get-resources)
       (remove #(util/forbidden-organization? user-id (:organization %)))
       (db/apply-filters filters)
       (map format-resource)
       (map #(assoc % :licenses (licenses/get-resource-licenses (:id %))))))

(defn ext-id-exists? [ext-id]
  (some? (db/get-resource {:resid ext-id})))
