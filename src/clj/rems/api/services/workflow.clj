(ns rems.api.services.workflow
  (:require [com.rpl.specter :refer [ALL transform]]
            [rems.api.services.dependencies :as dependencies]
            [rems.api.services.util :as util]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.organizations :as organizations]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.json :as json]
            [schema.core :as s]))

(def ^:private validate-workflow-body
  (s/validator workflow/WorkflowBody))

(defn create-workflow! [{:keys [user-id organization type title handlers forms]}]
  (util/check-allowed-organization! organization)
  (let [body {:type type
              :handlers handlers
              :forms forms} ;; TODO missing validation for handlers and forms, see #2182
        id (:id (db/create-workflow! {:organization (:organization/id organization)
                                      :owneruserid user-id
                                      :modifieruserid user-id
                                      :title title
                                      :workflow (json/generate-string
                                                 (validate-workflow-body body))}))]
    (dependencies/reset-cache!)
    {:success (not (nil? id))
     :id id}))

(defn- unrich-workflow [workflow]
  ;; TODO: keep handlers always in the same format, to avoid this conversion (we can ignore extra keys)
  (if (get-in workflow [:workflow :handlers])
    (update-in workflow [:workflow :handlers] #(map :userid %))
    workflow))

(defn edit-workflow! [{:keys [id title handlers]}]
  (let [workflow (unrich-workflow (workflow/get-workflow id))
        workflow-body (cond-> (:workflow workflow)
                        handlers (assoc :handlers handlers))]
    (util/check-allowed-organization! (:organization workflow))
    (db/edit-workflow! {:id id
                        :title title
                        :workflow (json/generate-string workflow-body)}))
  (applications/reload-cache!)
  {:success true})

(defn set-workflow-enabled! [{:keys [id enabled]}]
  (util/check-allowed-organization! (:organization (workflow/get-workflow id)))
  (db/set-workflow-enabled! {:id id :enabled enabled})
  {:success true})

(defn set-workflow-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:organization (workflow/get-workflow id)))
  (if-let [errors (if archived
                    (dependencies/archive-errors :t.administration.errors/workflow-in-use {:workflow/id id})
                    (dependencies/unarchive-errors {:workflow/id id}))]
    {:success false
     :errors errors}
    (do
      (db/set-workflow-archived! {:id id
                                  :archived archived})
      {:success true})))

(defn- join-dependencies [workflow]
  (when workflow
    (->> workflow
         organizations/join-organization
         workflow/join-workflow-licenses
         (transform [:licenses ALL] organizations/join-organization))))

(defn get-workflow [id]
  (->> (workflow/get-workflow id)
       join-dependencies))

(defn get-workflows [filters]
  (->> (workflow/get-workflows filters)
       (mapv join-dependencies)))

(defn get-available-actors [] (users/get-users))

(defn get-handlers []
  (let [workflows (workflow/get-workflows {:enabled true
                                           :archived false})
        handlers (mapcat (fn [wf]
                           (get-in wf [:workflow :handlers]))
                         workflows)]
    (->> handlers distinct (sort-by :userid))))
