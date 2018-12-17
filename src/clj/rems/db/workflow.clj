(ns rems.db.workflow
  (:require [rems.db.core :as db]
            [rems.db.workflow-actors :as actors]
            [cheshire.core :as cheshire]))

(defn- parse-workflow-body [json]
  (cheshire/parse-string json true))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map #(update % :workflow parse-workflow-body))
       (map db/assoc-active)
       (db/apply-filters filters)))

(defn- create-auto-approve-workflow! [{:keys [user-id organization title]}]
  (assert user-id)
  (let [wfid (:id (db/create-workflow! {:organization organization,
                                        :owneruserid user-id,
                                        :modifieruserid user-id,
                                        :title title,
                                        :fnlround 0}))]
    {:id wfid}))

(defn- create-dynamic-workflow! [{:keys [user-id organization title handlers]}]
  (assert user-id)
  (let [wfid (:id (db/create-workflow! {:organization organization,
                                        :owneruserid user-id,
                                        :modifieruserid user-id,
                                        :title title,
                                        :fnlround 0
                                        :workflow (cheshire/generate-string {:type :workflow/dynamic
                                                                             :handlers handlers})}))]
    {:id wfid}))

(defn- create-rounds-workflow! [{:keys [user-id organization title rounds]}]
  (assert user-id)
  (assert (not (empty? rounds)) "no rounds")
  (let [wfid (:id (db/create-workflow! {:organization organization,
                                        :owneruserid user-id,
                                        :modifieruserid user-id,
                                        :title title,
                                        :fnlround (dec (count rounds))}))]
    (doseq [[round-index round] (map-indexed vector rounds)]
      (doseq [actor (:actors round)]
        (case (:type round)
          :approval (actors/add-approver! wfid actor round-index)
          :review (actors/add-reviewer! wfid actor round-index))))
    {:id wfid}))

(defn create-workflow! [command]
  (case (:type command)
    :auto-approve (create-auto-approve-workflow! command)
    :dynamic (create-dynamic-workflow! command)
    :rounds (create-rounds-workflow! command)))
