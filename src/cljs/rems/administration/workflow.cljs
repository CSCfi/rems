(ns rems.administration.workflow
  (:require [clojure.string :as str]
            [medley.core :refer [indexed]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [document-title enrich-user readonly-checkbox]]
            [rems.collapsible :as collapsible]
            [rems.common.util :refer [andstr]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localized localize-command localize-role localize-state text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ workflow-id]]
   {:db (assoc db ::loading? true)
    ::fetch-workflow [workflow-id]}))

(defn- fetch-workflow [workflow-id]
  (fetch (str "/api/workflows/" workflow-id)
         {:handler #(rf/dispatch [::fetch-workflow-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch workflow")}))

(rf/reg-fx ::fetch-workflow (fn [[workflow-id]] (fetch-workflow workflow-id)))

(rf/reg-event-db
 ::fetch-workflow-result
 (fn [db [_ workflow]]
   (-> db
       (assoc ::workflow workflow)
       (dissoc ::loading?))))

(rf/reg-sub ::workflow (fn [db _] (::workflow db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn edit-action [workflow-id]
  (atoms/edit-action
   {:class "edit-workflow"
    :url (str "/administration/workflows/edit/" workflow-id)}))

(defn edit-button [workflow-id]
  [atoms/action-button (edit-action workflow-id)])

(def workflow-types
  {:workflow/default :t.create-workflow/default-workflow
   :workflow/decider :t.create-workflow/decider-workflow
   :workflow/master :t.create-workflow/master-workflow})

(defn- render-command [command]
  [:div
   (localize-command command)
   [:code.color-pre " (" (name command) ")"]])

(defn- render-user-roles [roles]
  (for [role roles]
    [:div
     (if (some #{role} [:expirer :reporter])
       (text :t.roles/technical-role)
       (localize-role role))
     [:code.color-pre " (" (name role) ")"]]))

(defn- render-application-states [states]
  (for [state states]
    [:div
     (localize-state state)
     [:code.color-pre " (" (name state) ")"]]))

(rf/reg-sub ::disable-commands
            :<- [::workflow]
            #(get-in % [:workflow :disable-commands]))
(rf/reg-sub ::disable-commands-table-rows
            :<- [::disable-commands]
            :<- [:language]
            (fn [[disable-commands _language]]
              (for [[rule-index rule] (indexed disable-commands)
                    :let [command (:command rule)
                          states (sort (:when/state rule))
                          roles (sort (:when/role rule))]]
                {:key (str "disable-command-" rule-index)
                 :command {:display-value (render-command command)}
                 :application-state {:display-value (if (seq states)
                                                      (into [:<>] (render-application-states states))
                                                      (text :t.dropdown/placeholder-any-selection))}
                 :user-role {:display-value (if (seq roles)
                                              (into [:<>] (render-user-roles roles))
                                              (text :t.dropdown/placeholder-any-selection))}})))

(defn- disable-commands-table []
  [table/table {:id ::disable-commands
                :columns [{:key :command
                           :title (text :t.administration/disabled-command)
                           :sortable? false}
                          {:key :application-state
                           :title (text :t.administration/application-state)
                           :sortable? false}
                          {:key :user-role
                           :title (text :t.administration/user-role)
                           :sortable? false}]
                :rows [::disable-commands-table-rows]
                :default-sort-column :command}])

(defn workflow-view [workflow language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "workflow"
     :title [:span (andstr (get-in workflow [:organization :organization/short-name language]) "/") (:title workflow)]
     :always [:div
              (when (get-in workflow [:workflow :anonymize-handling])
                [:div.alert.alert-info (text :t.administration/workflow-anonymize-handling-explanation)])
              [inline-info-field (text :t.administration/organization) (get-in workflow [:organization :organization/name language])]
              [inline-info-field (text :t.administration/title) (:title workflow)]
              [inline-info-field (text :t.administration/type) (text (get workflow-types
                                                                          (get-in workflow [:workflow :type])
                                                                          :t/missing))]
              [inline-info-field (text :t.create-workflow/handlers) (->> (get-in workflow [:workflow :handlers])
                                                                         (map enrich-user)
                                                                         (map :display)
                                                                         (str/join ", "))]
              [inline-info-field (text :t.administration/anonymize-handling) [readonly-checkbox {:value (true? (get-in workflow [:workflow :anonymize-handling]))}]]
              [inline-info-field (text :t.administration/active) [readonly-checkbox {:value (status-flags/active? workflow)}]]
              [inline-info-field (text :t.administration/forms)
               (if-some [forms (seq (for [form (get-in workflow [:workflow :forms])
                                          :let [uri (str "/administration/forms/" (:form/id form))
                                                title (:form/internal-name form)]]
                                      [atoms/link nil uri title]))]
                 (into [:<>] (interpose ", ") forms)
                 (text :t.administration/no-forms))]
              [inline-info-field (text :t.administration/licenses)
               (if-some [licenses (seq (for [license (get-in workflow [:workflow :licenses])
                                             :let [uri (str "/administration/licenses/" (:license/id license))
                                                   title (:title (localized (:localizations license)))]]
                                         [atoms/link nil uri title]))]
                 (into [:<>] (interpose ", ") licenses)
                 (text :t.administration/no-licenses))]
              (when-let [voting (get-in workflow [:workflow :voting])]
                [inline-info-field
                 (text :t.administration/voting)
                 (text (keyword (str "t" ".administration") (:type voting)))])]}]
   (when (seq (get-in workflow [:workflow :disable-commands]))
     [collapsible/component
      {:id "workflow-disabled-commands"
       :title (text :t.administration/disabled-commands)
       :always [:div
                [:div.alert.alert-info (text :t.administration/workflow-disabled-commands-explanation)]
                [:div.mt-4 [disable-commands-table]]]}])

   (let [id (:id workflow)]
     [:div.col.commands
      [administration/back-button "/administration/workflows"]
      [roles/show-when roles/+admin-write-roles+
       [edit-button id]
       [status-flags/enabled-toggle workflow #(rf/dispatch [:rems.administration.workflows/set-workflow-enabled %1 %2 [::enter-page id]])]
       [status-flags/archived-toggle workflow #(rf/dispatch [:rems.administration.workflows/set-workflow-archived %1 %2 [::enter-page id]])]]])])

(defn workflow-page []
  (let [workflow (rf/subscribe [::workflow])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration/navigator]
       [document-title (text :t.administration/workflow)]
       [flash-message/component :top]
       (if @loading?
         [spinner/big]
         [workflow-view @workflow @language])])))
