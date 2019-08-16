(ns rems.db.resource
  (:require [rems.db.core :as db]
            [rems.db.licenses :as licenses]))

(defn- format-resource
  [{:keys [id owneruserid modifieruserid organization resid start end expired enabled archived]}]
  {:id id
   :owneruserid owneruserid
   :modifieruserid modifieruserid
   :organization organization
   :resid resid
   :start start
   :end end
   :expired expired
   :enabled enabled
   :archived archived})

(defn get-resource [id]
  (-> {:id id}
      db/get-resource
      db/assoc-expired
      format-resource
      (assoc :licenses (licenses/get-resource-licenses id))))

(defn get-resources [filters]
  (->> (db/get-resources)
       (map db/assoc-expired)
       (db/apply-filters filters)
       (map format-resource)
       (map #(assoc % :licenses (licenses/get-resource-licenses (:id %))))))
