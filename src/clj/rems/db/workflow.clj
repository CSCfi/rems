(ns rems.db.workflow
  (:require [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.json :as json])
  (:import [org.apache.commons.lang3 NotImplementedException]))

(defn- parse-workflow-body [json]
  (json/parse-string json))

(defn- parse-licenses [json]
  (json/parse-string json))

(defn get-workflow [id]
  (-> {:wfid id}
      db/get-workflow
      (update :workflow parse-workflow-body)
      (update :licenses parse-licenses)
      db/assoc-expired))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map #(update % :workflow parse-workflow-body))
       (map db/assoc-expired)
       (db/apply-filters filters)))

(defn- create-auto-approve-workflow! [{:keys [user-id organization title]}]
  (assert user-id)
  ;; TODO: create a new auto-approve workflow in the style of dynamic workflows
  (throw (NotImplementedException. "auto-approve workflows are not yet implemented")))

(defn- create-dynamic-workflow! [{:keys [user-id organization title handlers]}]
  (assert user-id)
  (assert organization)
  (assert title)
  (assert (every? string? handlers) {:handlers handlers})
  (let [wfid (:id (db/create-workflow! {:organization organization,
                                        :owneruserid user-id,
                                        :modifieruserid user-id,
                                        :title title,
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

(defn update-workflow! [command]
  (let [catalogue-items
        (->> (catalogue/get-localized-catalogue-items {:workflow (:id command) :archived false})
             (map #(select-keys % [:id :title :localizations])))]
    (if (and (:archived command) (seq catalogue-items))
      {:success false
       :errors [{:type :t.administration.errors/workflow-in-use :catalogue-items catalogue-items}]}
      (do
        (db/update-workflow! (merge (select-keys command [:id :enabled :archived :title])
                                    (when-let [handlers (:handlers command)]
                                      {:workflow (json/generate-string {:type :workflow/dynamic
                                                                        :handlers handlers})})))
        {:success true}))))
