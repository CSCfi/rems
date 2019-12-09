(ns rems.api.services.workflow
  (:require [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.workflow :as workflow]
            [rems.db.users :as users]
            [rems.json :as json])
  (:import [org.apache.commons.lang3 NotImplementedException]))

(defn- create-auto-approve-workflow! [{:keys [user-id organization title]}] ; TODO: remove
  (assert user-id)
  ;; TODO: create a new auto-approve workflow in the style of dynamic workflows
  (throw (NotImplementedException. "auto-approve workflows are not yet implemented")))

(defn- create-dynamic-workflow! [{:keys [user-id organization type title handlers]}] ; TODO: inline
  (assert user-id)
  (assert organization)
  (assert title)
  (assert (every? string? handlers) {:handlers handlers})
  (let [wfid (:id (db/create-workflow! {:organization organization,
                                        :owneruserid user-id,
                                        :modifieruserid user-id,
                                        :title title,
                                        :workflow (json/generate-string {:type type
                                                                         :handlers handlers})}))]
    {:id wfid}))

(defn create-workflow! [command]
  (let [result (case (:type command)
                 :auto-approve (create-auto-approve-workflow! command)
                 :workflow/dynamic (create-dynamic-workflow! command)
                 :workflow/bureaucratic (create-dynamic-workflow! command))]
    (merge
     result
     {:success (not (nil? (:id result)))})))

(defn- unrich-workflow [workflow]
  ;; TODO: keep handlers always in the same format, to avoid this conversion (we can ignore extra keys)
  (if (get-in workflow [:workflow :handlers])
    (update-in workflow [:workflow :handlers] #(map :userid %))
    workflow))

(defn edit-workflow! [{:keys [id title handlers]}]
  (let [workflow (unrich-workflow (workflow/get-workflow id))
        workflow-body (cond-> (:workflow workflow)
                        handlers (assoc :handlers handlers))]
    (db/edit-workflow! {:id id
                        :title title
                        :workflow (json/generate-string workflow-body)}))
  {:success true})

(defn set-workflow-enabled! [command]
  (db/set-workflow-enabled! (select-keys command [:id :enabled]))
  {:success true})

(defn set-workflow-archived! [{:keys [id archived]}]
  (let [workflow (workflow/get-workflow id)
        archived-licenses (filter :archived (:licenses workflow))
        catalogue-items
        (->> (catalogue/get-localized-catalogue-items {:workflow id
                                                       :archived false})
             (map #(select-keys % [:id :title :localizations])))]
    (cond
      (and archived (seq catalogue-items))
      {:success false
       :errors [{:type :t.administration.errors/workflow-in-use
                 :catalogue-items catalogue-items}]}

      (and (not archived) (seq archived-licenses))
      {:success false
       :errors [{:type :t.administration.errors/license-archived
                 :licenses archived-licenses}]}

      :else
      (do
        (db/set-workflow-archived! {:id id
                                    :archived archived})
        {:success true}))))

(defn get-workflow [id] (workflow/get-workflow id))
(defn get-workflows [filters] (workflow/get-workflows filters))
(defn get-available-actors [] (users/get-users))
