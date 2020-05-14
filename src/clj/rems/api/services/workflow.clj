(ns rems.api.services.workflow
  (:require [rems.api.services.util :as util]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.form :as form]
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
        id (:id (db/create-workflow! {:organization organization
                                      :owneruserid user-id
                                      :modifieruserid user-id
                                      :title title
                                      :workflow (json/generate-string
                                                 (validate-workflow-body body))}))]
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
  (let [workflow (workflow/get-workflow id)
        archived-licenses (seq (filter :archived (:licenses workflow)))
        archived-forms (->> (get-in workflow [:workflow :forms])
                            (map (comp form/get-form-template :form/id))
                            (filter :archived)
                            (map #(select-keys % [:form/id :form/title]))
                            seq)
        catalogue-items (->> (catalogue/get-localized-catalogue-items {:workflow id
                                                       :archived false})
                             (map #(select-keys % [:id :title :localizations])))]
    (util/check-allowed-organization! (:organization workflow))
    (let [errors (seq
                  (concat
                   (when (and archived (seq catalogue-items))
                     [{:type :t.administration.errors/workflow-in-use
                       :catalogue-items catalogue-items}])
                   (when (and (not archived) (seq archived-licenses))
                     [{:type :t.administration.errors/license-archived
                       :licenses archived-licenses}])
                   (when (and (not archived) (seq archived-forms))
                     [{:type :t.administration.errors/form-archived
                       :forms archived-forms}])))]
      (if errors
        {:success false
         :errors errors}
        (do
          (db/set-workflow-archived! {:id id
                                      :archived archived})
          {:success true})))))

(defn get-workflow [id]
  (workflow/get-workflow id))

(defn get-workflows [filters]
  (workflow/get-workflows filters))

(defn get-available-actors [] (users/get-users))

(defn get-handlers []
  (let [workflows (workflow/get-workflows {:enabled true
                                           :archived false})
        handlers (mapcat (fn [wf]
                           (get-in wf [:workflow :handlers]))
                         workflows)]
    (->> handlers distinct (sort-by :userid))))
