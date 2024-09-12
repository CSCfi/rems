(ns rems.service.workflow
  (:require [rems.application.commands :as commands]
            [rems.common.util :refer [apply-filters]]
            [rems.db.applications]
            [rems.db.form]
            [rems.db.licenses]
            [rems.db.organizations]
            [rems.db.users]
            [rems.db.workflow]
            [rems.service.dependencies :as dependencies]
            [rems.service.util :as util]))

(defn invalid-forms-error [forms]
  (when-some [invalid (seq (remove (comp rems.db.form/get-form-template :form/id) forms))]
    {:success false
     :errors [{:type :invalid-form
               :forms invalid}]}))

(defn invalid-users-error [userids]
  (when-some [invalid (seq (remove rems.db.users/user-exists? userids))]
    {:success false
     :errors [{:type :invalid-user
               :users invalid}]}))

(defn invalid-licenses-error [licenses]
  (when-some [invalid (seq (remove (comp rems.db.licenses/license-exists? :license/id) licenses))]
    {:success false
     :errors [{:type :invalid-license
               :licenses invalid}]}))

(defn invalid-disable-commands-error [disable-commands]
  (when-some [invalid (seq (remove (comp (set commands/command-names) :command) disable-commands))]
    {:success false
     :errors [{:type :invalid-disable-commands
               :commands invalid}]}))

(defn create-workflow! [{:keys [anonymize-handling
                                disable-commands
                                forms
                                handlers
                                licenses
                                organization
                                processing-states
                                type
                                title
                                voting]
                         :as cmd}]
  (util/check-allowed-organization! organization)
  (or (invalid-users-error handlers)
      (invalid-forms-error forms)
      (invalid-licenses-error licenses)
      (invalid-disable-commands-error disable-commands)
      (let [id (rems.db.workflow/create-workflow! (update cmd :licenses #(map :license/id %)))]
        {:success (not (nil? id))
         :id id})))

(defn edit-workflow! [{:keys [anonymize-handling
                              disable-commands
                              handlers
                              id
                              organization
                              processing-states
                              title
                              voting]
                       :as cmd}]
  (let [workflow (rems.db.workflow/get-workflow id)]
    (util/check-allowed-organization! (:organization workflow))
    (when organization
      (util/check-allowed-organization! organization))
    (or (invalid-users-error handlers)
        (invalid-disable-commands-error disable-commands)
        (do
          (rems.db.workflow/edit-workflow! (update cmd :licenses #(map :license/id %)))
          (rems.db.applications/reload-applications! {:by-workflow-ids [id]})
          {:success true}))))

(defn set-workflow-enabled! [{:keys [id enabled]}]
  (util/check-allowed-organization! (:organization (rems.db.workflow/get-workflow id)))
  (rems.db.workflow/set-enabled! id enabled)
  {:success true})

(defn set-workflow-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:organization (rems.db.workflow/get-workflow id)))
  (or (dependencies/change-archive-status-error archived {:workflow/id id})
      (do
        (rems.db.workflow/set-archived! id archived)
        {:success true})))

;; TODO more systematic joining for these needed. Now we just add the title for the UI
(defn- enrich-workflow-form [item]
  (-> item
      rems.db.form/join-form-template
      (select-keys [:form/id :form/internal-name :form/external-title])))

(defn- enrich-workflow-license [item]
  (-> item
      rems.db.licenses/join-license
      rems.db.organizations/join-organization))

(defn- join-dependencies [workflow]
  (when workflow
    (-> workflow
        rems.db.organizations/join-organization
        (update-in [:workflow :forms] (partial map enrich-workflow-form))
        (update-in [:workflow :licenses] (partial map enrich-workflow-license)))))

(defn get-workflow [id]
  (->> (rems.db.workflow/get-workflow id)
       join-dependencies))

(defn get-workflows [filters]
  (->> (rems.db.workflow/get-workflows)
       (apply-filters filters)
       (mapv join-dependencies)))

(defn get-handlers []
  (let [workflows (->> (rems.db.workflow/get-workflows)
                       (apply-filters {:enabled true
                                       :archived false}))
        handlers (mapcat (fn [wf]
                           (get-in wf [:workflow :handlers]))
                         workflows)]
    (->> handlers distinct (sort-by :userid))))
