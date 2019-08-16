(ns rems.api.services.workflow
  (:require [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.workflow :as workflow]
            [rems.db.users :as users]
            [rems.json :as json])
  (:import [org.apache.commons.lang3 NotImplementedException]))

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

(defn update-workflow! [{:keys [id] :as command}]
  (let [workflow (workflow/get-workflow id)
        archived-licenses (filter :archived (:licenses workflow))
        catalogue-items
        (->> (catalogue/get-localized-catalogue-items {:workflow id
                                                       :archived false})
             (map #(select-keys % [:id :title :localizations])))]
    (cond
      (and (:archived command) (seq catalogue-items))
      {:success false
       :errors [{:type :t.administration.errors/workflow-in-use
                 :catalogue-items catalogue-items}]}

      (and (not (:archived command)) (seq archived-licenses))
      {:success false
       :errors [{:type :t.administration.errors/license-archived
                 :licenses archived-licenses}]}

      :else
      (do
        (db/update-workflow!
         (merge (select-keys command [:id :enabled :archived :title])
                (when-let [handlers (:handlers command)]
                  {:workflow (json/generate-string {:type :workflow/dynamic
                                                    :handlers handlers})})))
        {:success true}))))

(defn get-workflow [id] (workflow/get-workflow id))
(defn get-workflows [filters] (workflow/get-workflows filters))
(defn get-available-actors [] (users/get-users))
