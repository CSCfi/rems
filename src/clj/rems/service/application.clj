(ns rems.service.application
  (:require [clj-time.coerce :as time-coerce]
            [rems.db.applications]
            [rems.application.commands :as commands]
            [rems.application.search :as search]
            [rems.db.csv]
            [rems.db.attachments]
            [rems.db.events]
            [rems.db.user-settings]))

(defn format-overview [app]
  (dissoc app
          :application/events
          :application/forms
          :application/licenses))

(defn- find-applications [userid query]
  (rems.db.applications/get-all-applications-full userid (search/filter-with-search query)))

(defn- find-my-applications [userid query]
  (rems.db.applications/get-my-applications-full userid (search/filter-with-search query)))

(defn get-all-applications [userid & [query]]
  (->> (find-applications userid query)
       (mapv format-overview)))

(defn get-my-applications [userid & [query]]
  (->> (find-my-applications userid query)
       (mapv format-overview)))

(defn get-application-by-invitation-token [invitation-token]
  (rems.db.applications/get-application-by-invitation-token invitation-token))

(defn export-applications-for-form-as-csv [user-id form-id]
  (let [language (:language (rems.db.user-settings/get-user-settings user-id))
        get-forms #(set (map :form/id (:application/forms %)))
        filtered-applications (->> (rems.db.applications/get-all-applications-full user-id)
                                   (filterv #(contains? (get-forms %) form-id)))]
    (rems.db.csv/applications-to-csv filtered-applications form-id language)))

(def todo-roles
  #{:handler :reviewer :decider :past-reviewer :past-decider})

(defn- is-potential-todo [application]
  (and (not= :application.state/draft (:application/state application))
       (some todo-roles (:application/roles application))))

(defn- is-todo [application]
  (and (= :application.state/submitted (:application/state application))
       (some commands/todo-commands (:application/permissions application))))

(defn get-todos [userid & [query]]
  (->> (find-applications userid query)
       (eduction (filter is-todo)
                 (map format-overview))
       (into [])))

(defn- overview-only-active-handlers [app]
  (update-in app
             [:application/workflow :workflow.dynamic/handlers]
             #(filter :handler/active? %)))

(def ^:private last-activity (comp time-coerce/to-long :application/last-activity))

(defn- find-handled-applications [userid & [query]]
  (->> (find-applications userid query)
       (eduction (filter is-potential-todo)
                 (remove is-todo))))

(defn get-handled-applications [userid & [{:keys [query only-active-handlers limit]}]]
  (time
   (cond->> (find-handled-applications userid query)
     only-active-handlers (eduction (map overview-only-active-handlers))
     true (mapv format-overview)
     true (sort-by last-activity >)
     limit (take limit))))

(defn get-handled-applications-count [userid]
  (time
   (count (seq (find-handled-applications userid)))))

(defn validate [userid application-id field-values]
  (when-let [app (rems.db.applications/get-application-for-user userid application-id)]
    (merge {:success true}
           (commands/validate-application app field-values))))
