(ns rems.db.resource
  (:require [rems.db.core :as db]
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

(defn get-resource [id]
  (when-let [resource (db/get-resource {:id id})]
    (format-resource resource)))

(defn get-resources [filters]
  (->> (db/get-resources)
       (db/apply-filters filters)
       (map format-resource)))

(defn ext-id-exists? [ext-id]
  (some? (db/get-resource {:resid ext-id})))
