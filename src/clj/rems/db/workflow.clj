(ns rems.db.workflow
  (:require [rems.db.core :as db]
            [rems.json :as json]))

(defn- parse-workflow-body [json]
  (json/parse-string json))

(defn- parse-licenses [json]
  (json/parse-string json))

(defn get-workflow [id]
  (-> {:wfid id}
      db/get-workflow
      (update :workflow parse-workflow-body)
      (update :licenses parse-licenses)
      db/assoc-active))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map #(update % :workflow parse-workflow-body))
       (map db/assoc-active)
       (db/apply-filters filters)))

(defn- create-auto-approve-workflow! [{:keys [user-id organization title]}]
  (assert user-id)
  ;; TODO: create a new auto-approve workflow in the style of dynamic workflows
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
                                        :workflow (json/generate-string {:type :workflow/dynamic
                                                                         :handlers handlers})}))]
    {:id wfid}))

(defn create-workflow! [command]
  (let [result (case (:type command)
                 :auto-approve (create-auto-approve-workflow! command)
                 :dynamic (create-dynamic-workflow! command))]
    (merge
     result
     {:success (not (nil? (:id result)))})))
