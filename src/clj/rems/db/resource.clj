(ns rems.db.resource
  (:require [rems.db.core :as db]
            [rems.util :refer [get-user-id]]))

(defn get-resources [filters]
  (->> (db/get-resources)
       (map db/assoc-active)
       (db/apply-filters filters)))

(defn create-resource! [{:keys [resid organization licenses]}]
  (let [id (:id (db/create-resource! {:resid resid
                                      :organization organization
                                      :owneruserid (get-user-id)
                                      :modifieruserid (get-user-id)}))]
    (doseq [licid licenses]
      (db/create-resource-license! {:resid id
                                    :licid licid}))
    {:id id}))
