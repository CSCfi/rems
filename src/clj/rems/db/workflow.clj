(ns rems.db.workflow
  (:require [rems.db.core :as db]
            [rems.db.workflow-actors :as actors]
            [rems.util :refer [get-user-id]]))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map db/assoc-active)
       (db/apply-filters filters)))

(defn- create-auto-approve-workflow! [{:keys [organization title]}]
  (let [wfid (:id (db/create-workflow! {:organization organization,
                                        :owneruserid (get-user-id),
                                        :modifieruserid (get-user-id),
                                        :title title,
                                        :fnlround 0}))]
    {:id wfid}))

(defn- create-rounds-workflow! [{:keys [organization title rounds]}]
  (assert (not (empty? rounds)) "no rounds")
  (let [wfid (:id (db/create-workflow! {:organization organization,
                                        :owneruserid (get-user-id),
                                        :modifieruserid (get-user-id),
                                        :title title,
                                        :fnlround (dec (count rounds))}))]
    (doseq [[round-index round] (map-indexed vector rounds)]
      (doseq [actor (:actors round)]
        (case (:type round)
          :approval (actors/add-approver! wfid (:userid actor) round-index)
          :review (actors/add-reviewer! wfid (:userid actor) round-index))))
    {:id wfid}))

(defn create-workflow! [command]
  (case (:type command)
    :auto-approve (create-auto-approve-workflow! command)
    :dynamic (assert false "TODO") ;; TODO
    :rounds (create-rounds-workflow! command)))
