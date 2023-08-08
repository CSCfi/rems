(ns rems.service.workflow
  (:require [rems.application.commands :as commands]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.organizations :as organizations]
            [rems.db.workflow :as workflow]
            [rems.service.cache :as cache]
            [rems.service.dependencies :as dependencies]
            [rems.service.util :as util]))

(defn invalid-forms-error [forms]
  (when-some [invalid (seq (remove (comp form/get-form-template :form/id) forms))]
    {:success false
     :errors [{:type :invalid-form
               :forms invalid}]}))

(defn invalid-users-error [userids]
  (when-some [invalid (seq (remove cache/user-exists? userids))]
    {:success false
     :errors [{:type :invalid-user
               :users invalid}]}))

(defn invalid-licenses-error [licenses]
  (when-some [invalid (seq (remove (comp licenses/license-exists? :license/id) licenses))]
    {:success false
     :errors [{:type :invalid-license
               :licenses invalid}]}))

(defn invalid-disable-commands-error [disable-commands]
  (when-some [invalid (seq (remove (comp (set commands/command-names) :command) disable-commands))]
    {:success false
     :errors [{:type :invalid-disable-commands
               :commands invalid}]}))

(defn create-workflow! [{:keys [organization handlers licenses forms disable-commands] :as cmd}]
  (util/check-allowed-organization! organization)
  (or (invalid-users-error handlers)
      (invalid-forms-error forms)
      (invalid-licenses-error licenses)
      (invalid-disable-commands-error disable-commands)
      (let [id (workflow/create-workflow! (update cmd :licenses #(map :license/id %)))]
        (dependencies/reset-cache!)
        {:success (not (nil? id))
         :id id})))

(defn edit-workflow! [{:keys [id organization handlers disable-commands] :as cmd}]
  (let [workflow (workflow/get-workflow id)]
    (util/check-allowed-organization! (:organization workflow))
    (when organization
      (util/check-allowed-organization! organization))
    (or (invalid-users-error handlers)
        (invalid-disable-commands-error disable-commands)
        (do
          (workflow/edit-workflow! (update cmd :licenses #(map :license/id %)))
          (dependencies/evict! :workflow id)
          {:success true}))))

(defn set-workflow-enabled! [{:keys [id enabled]}]
  (util/check-allowed-organization! (:organization (workflow/get-workflow id)))
  (workflow/set-workflow-enabled! {:id id :enabled enabled})
  {:success true})

(defn set-workflow-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:organization (workflow/get-workflow id)))
  (or (dependencies/change-archive-status-error archived {:workflow/id id})
      (do
        (workflow/set-workflow-archived! {:id id
                                          :archived archived})
        {:success true})))

;; TODO more systematic joining for these needed. Now we just add the title for the UI
(defn- enrich-workflow-form [item]
  (-> item
      dependencies/enrich-dependency
      (select-keys [:form/id :form/internal-name :form/external-title])))

(defn- enrich-workflow-license [item]
  (-> item
      licenses/join-license
      organizations/join-organization))

(defn- join-dependencies [workflow]
  (when workflow
    (-> workflow
        organizations/join-organization
        (update-in [:workflow :handlers] (partial mapv (comp cache/get-user :userid)))
        (update-in [:workflow :forms] (partial mapv enrich-workflow-form))
        (update-in [:workflow :licenses] (partial mapv enrich-workflow-license)))))

(defn get-workflow [id]
  (->> (workflow/get-workflow id)
       join-dependencies))

(defn get-workflows [filters]
  (->> (workflow/get-workflows filters)
       (mapv join-dependencies)))

(defn get-available-actors [] (cache/get-users))

(defn get-handlers []
  (let [workflows (workflow/get-workflows {:enabled true
                                           :archived false})
        handlers (mapcat (fn [wf]
                           (get-in wf [:workflow :handlers]))
                         workflows)]
    (->> handlers distinct (sort-by :userid))))

