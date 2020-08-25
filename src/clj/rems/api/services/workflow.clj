(ns rems.api.services.workflow
  (:require [com.rpl.specter :refer [ALL transform]]
            [rems.api.services.dependencies :as dependencies]
            [rems.api.services.util :as util]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.organizations :as organizations]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.json :as json]))

(defn invalid-forms-error [forms]
  (let [invalid (seq (remove (comp form/get-form-template :form/id) forms))]
    (when invalid
      {:success false
       :errors [{:type :invalid-form
                 :forms invalid}]})))

(defn invalid-users-error [userids]
  (let [invalid (seq (remove users/user-exists? userids))]
    (when invalid
      {:success false
       :errors [{:type :invalid-user
                 :users invalid}]})))

(defn create-workflow! [{:keys [organization handlers forms] :as cmd}]
  (util/check-allowed-organization! organization)
  (or (invalid-users-error handlers)
      (invalid-forms-error forms)
      (let [id (workflow/create-workflow! cmd)]
        (dependencies/reset-cache!)
        {:success (not (nil? id))
         :id id})))

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
  (or (dependencies/change-archive-status-error archived {:workflow/id id})
      (do
        (db/set-workflow-archived! {:id id
                                    :archived archived})
        {:success true})))

;; TODO more systematic joining for these needed. Now we just add the title for the UI
(defn- enrich-workflow-form [item]
  (select-keys (dependencies/enrich-dependency item) [:form/id :form/title]))

(defn- join-workflow-forms [workflow]
  (update-in workflow [:workflow :forms] (partial mapv enrich-workflow-form)))

(defn- join-dependencies [workflow]
  (when workflow
    (->> workflow
         join-workflow-forms
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
