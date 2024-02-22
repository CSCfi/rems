(ns rems.administration.workflow
  (:require [better-cond.core :as b]
            [clojure.string :as str]
            [medley.core :refer [indexed]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [document-title enrich-user readonly-checkbox]]
            [rems.collapsible :as collapsible]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localized localize-command localize-role localize-state text text-format]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ workflow-id]]
   {:db (assoc db ::loading? true)
    ::fetch-workflow [workflow-id]}))

(rf/reg-fx
 ::fetch-workflow
 (fn [[workflow-id]]
   (fetch (str "/api/workflows/" workflow-id)
          {:handler #(rf/dispatch [::fetch-workflow-result %])
           :error-handler (flash-message/default-error-handler :top "Fetch workflow")})))

(rf/reg-event-db
 ::fetch-workflow-result
 (fn [db [_ workflow]]
   (-> db
       (assoc ::workflow workflow)
       (dissoc ::loading?))))

(rf/reg-sub ::workflow (fn [db _] (::workflow db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-sub ::disable-commands
            :<- [::workflow]
            (fn [workflow _]
              (get-in workflow [:workflow :disable-commands])))

(rf/reg-sub ::disable-commands-table-rows
            :<- [::disable-commands]
            :<- [:language] ; re-renders when language changes
            (fn [[disable-commands _language] _]
              (for [[index value] (indexed disable-commands)
                    :let [command (:command value)
                          states (sort (:when/state value))
                          roles (sort (:when/role value))]]
                {:key (str "disable-command-" index)
                 :command {:display-value [:div
                                           (localize-command command)
                                           [:code.color-pre " (" (name command) ")"]]}
                 :application-state {:display-value (if (seq states)
                                                      (into [:<>] (for [state states]
                                                                    [:div
                                                                     (localize-state state)
                                                                     [:code.color-pre " (" (name state) ")"]]))
                                                      (text :t.dropdown/placeholder-any-selection))}
                 :user-role {:display-value (if (seq roles)
                                              (into [:<>] (for [role roles]
                                                            [:div
                                                             (if (some #{role} [:expirer :reporter])
                                                               (text :t.roles/technical-role)
                                                               (localize-role role))
                                                             [:code.color-pre " (" (name role) ")"]]))
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

(rf/reg-sub ::processing-states
            :<- [::workflow]
            (fn [workflow _]
              (get-in workflow [:workflow :processing-states])))

(rf/reg-sub ::processing-states-table-rows
            :<- [::processing-states]
            :<- [:language]
            (fn [[processing-states language] _]
              (for [[index value] (indexed processing-states)]
                {:key (str "processing-state-" index)
                 :value {:display-value [:code.color-pre (:value value)]}
                 :title {:display-value (get-in value [:title language])}})))

(defn- processing-states-table []
  [table/table {:id ::processing-states
                :columns [{:key :title
                           :title (text :t.administration/processing-state)
                           :sortable? false}
                          {:key :value
                           :title (text :t.administration/technical-value)
                           :sortable? false}]
                :rows [::processing-states-table-rows]
                :default-sort-column :value}])

(defn- render-forms [forms]
  (into [:<>]
        (interpose ", ")
        (for [form forms
              :let [uri (str "/administration/forms/" (:form/id form))
                    title (:form/internal-name form)]]
          [atoms/link {} uri title])))

(defn- render-licenses [licenses]
  (into [:<>]
        (interpose ", ")
        (for [license licenses
              :let [uri (str "/administration/licenses/" (:license/id license))
                    title (:title (localized (:localizations license)))]]
          [atoms/link {} uri title])))

(defn- localize-workflow [workflow-type]
  (let [wf-localization-key (case workflow-type
                              :workflow/default :t.create-workflow/default-workflow
                              :workflow/decider :t.create-workflow/decider-workflow
                              :workflow/master :t.create-workflow/master-workflow
                              nil)]
    (text wf-localization-key)))

(defn- common-fields [workflow language]
  (let [organization (:organization workflow)
        organization-long (get-in organization [:organization/name language])
        organization-short (get-in organization [:organization/short-name language])
        title (:title workflow)
        workflow-type (get-in workflow [:workflow :type])
        handlers (for [handler (get-in workflow [:workflow :handlers])]
                   (enrich-user handler))
        active? (status-flags/active? workflow)
        forms (get-in workflow [:workflow :forms])
        licenses (get-in workflow [:workflow :licenses])]
    [collapsible/component
     {:id "workflow-common-fields"
      :title (text-format :t.label/default organization-short title)
      :always [:div.fields
               [inline-info-field (text :t.administration/organization) organization-long]
               [inline-info-field (text :t.administration/title) title]
               [inline-info-field (text :t.administration/type) (localize-workflow workflow-type)]
               [inline-info-field (text :t.create-workflow/handlers) (str/join ", " (map :display handlers))]
               [inline-info-field (text :t.administration/active) [readonly-checkbox {:value active?}]]
               [inline-info-field (text :t.administration/forms) (if (seq forms)
                                                                   [render-forms forms]
                                                                   (text :t.administration/no-forms))]
               [inline-info-field (text :t.administration/licenses) (if (seq licenses)
                                                                      [render-licenses licenses]
                                                                      (text :t.administration/no-licenses))]]}]))

(defn- anonymize-handling-fields [workflow]
  (when (get-in workflow [:workflow :anonymize-handling])
    [collapsible/component
     {:id "workflow-anonymize-handling-fields"
      :title (text :t.administration/anonymize-handling)
      :always [:<>
               [:div.alert.alert-info (text :t.administration/workflow-anonymize-handling-explanation)]
               [inline-info-field (text :t.administration/anonymize-handling) [readonly-checkbox {:value true}]]]}]))

(defn- voting-fields [workflow]
  (when-let [voting (get-in workflow [:workflow :voting])]
    [collapsible/component
     {:id "workflow-voting-fields"
      :title (text :t.administration/voting)
      :always [:<>
               [:div.alert.alert-info (text :t.create-workflow/voting-explanation)]
               [inline-info-field
                (text :t.administration/voting)
                (text (keyword (str "t" ".administration") (:type voting)))]]}]))

(defn- disable-commands-fields [workflow]
  (when (seq (get-in workflow [:workflow :disable-commands]))
    [collapsible/component
     {:id "workflow-disable-commands-fields"
      :title (text :t.administration/disabled-commands)
      :always [:<>
               [:div.alert.alert-info (text :t.administration/workflow-disabled-commands-explanation)]
               [disable-commands-table]]}]))

(defn- processing-states-fields [workflow]
  (when (seq (get-in workflow [:workflow :processing-states]))
    [collapsible/component
     {:id "workflow-processing-states-fields"
      :title (text :t.administration/processing-states)
      :always [:<>
               [:div.alert.alert-info (text :t.administration/workflow-processing-states-explanation)]
               [processing-states-table]]}]))

(defn edit-workflow-action [workflow-id]
  (atoms/edit-action
   {:class "edit-workflow"
    :url (str "/administration/workflows/edit/" workflow-id)}))

(defn workflow-page []
  [:div
   [administration/navigator]
   [document-title (text :t.administration/workflow)]
   [flash-message/component :top]

   [:div#workflow-view.d-flex.flex-column.gap-4
    (b/cond
      @(rf/subscribe [::loading?])
      [spinner/big]

      :let [language @(rf/subscribe [:language])
            workflow @(rf/subscribe [::workflow])]
      [:<>
       [common-fields workflow language]
       [anonymize-handling-fields workflow]
       [voting-fields workflow]
       [disable-commands-fields workflow]
       [processing-states-fields workflow]

       [:div.col.commands
        [administration/back-button "/administration/workflows"]
        [roles/show-when roles/+admin-write-roles+
         [atoms/action-button (edit-workflow-action (:id workflow))]
         [status-flags/enabled-toggle workflow #(rf/dispatch [:rems.administration.workflows/set-workflow-enabled %1 %2 [::enter-page (:id workflow)]])]
         [status-flags/archived-toggle workflow #(rf/dispatch [:rems.administration.workflows/set-workflow-archived %1 %2 [::enter-page (:id workflow)]])]]]])]])
