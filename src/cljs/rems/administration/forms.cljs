(ns rems.administration.forms
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.form :as form]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch-forms]
                 [:rems.table/reset]
                 [:rems.administration.administration/remember-current-page]]}))

(rf/reg-event-db
 ::fetch-forms
 (fn [db]
   (let [description [text :t.administration/forms]]
     (fetch "/api/forms"
            {:url-params {:disabled true
                          :archived (status-flags/display-archived? db)}
             :handler #(rf/dispatch [::fetch-forms-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
   (assoc db ::loading? true)))

(rf/reg-event-db
 ::fetch-forms-result
 (fn [db [_ forms]]
   (-> db
       (assoc ::forms forms)
       (dissoc ::loading?))))

(rf/reg-sub ::forms (fn [db _] (::forms db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::set-form-archived
 (fn [_ [_ form description dispatch-on-finished]]
   (put! "/api/forms/archived"
         {:params {:id (:form/id form)
                   :archived (:archived form)}
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(rf/reg-event-fx
 ::set-form-enabled
 (fn [_ [_ form description dispatch-on-finished]]
   (put! "/api/forms/enabled"
         {:params {:id (:form/id form)
                   :enabled (:enabled form)}
          :handler (flash-message/status-update-handler
                    :top description #(rf/dispatch dispatch-on-finished))
          :error-handler (flash-message/default-error-handler :top description)})
   {}))

(defn- to-create-form []
  [atoms/link {:class "btn btn-primary" :id :create-form}
   "/administration/forms/create"
   (text :t.administration/create-form)])

(defn- to-view-form [form]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/forms/" (:form/id form))
   (text :t.administration/view)])

(defn- copy-as-new-action [form]
  {:label (text :t.administration/copy-as-new)
   :url (str "/administration/forms/create/" (:form/id form))})

(defn- errors-symbol []
  [:i.fa.fa-exclamation-triangle {:aria-label (text :t.administration/has-errors)
                                  :title (text :t.administration/has-errors)}])

(defn- modify-form-dropdown [form]
  [atoms/commands-group-button
   {:label (text :t.actions/modify)}
   (when (roles/can-modify-organization-item? form)
     (form/edit-action (:form/id form)))

   (when (apply roles/has-roles? roles/+admin-write-roles+)
     (copy-as-new-action form)) ; anyone can copy

   (when (roles/can-modify-organization-item? form)
     (list
      (status-flags/enabled-toggle-action {:on-change #(rf/dispatch [::set-form-enabled %1 %2 [::fetch-forms]])} form)
      (status-flags/archived-toggle-action {:on-change #(rf/dispatch [::set-form-archived %1 %2 [::fetch-forms]])} form)))])

(rf/reg-sub
 ::forms-table-rows
 (fn [_ _]
   [(rf/subscribe [::forms])
    (rf/subscribe [:language])])
 (fn [[forms language] _]
   (map (fn [form]
          {:key (:form/id form)
           :organization {:value (get-in form [:organization :organization/short-name language])}
           :internal-name {:value (get-in form [:form/internal-name])}
           :external-title {:value (get-in form [:form/external-title language])}
           :active (let [checked? (status-flags/active? form)]
                     {:display-value [readonly-checkbox {:value checked?}]
                      :sort-value (if checked? 1 2)})
           :errors (if-some [errors (seq (:form/errors form))]
                     {:value errors
                      :display-value [errors-symbol]
                      :sort-value 2}
                     {:value nil
                      :sort-value 1})
           :commands {:display-value [:div.commands
                                      [to-view-form form]
                                      [modify-form-dropdown form]]}})
        forms)))

(defn- forms-list []
  (let [forms-table {:id ::forms
                     :columns [{:key :organization
                                :title (text :t.administration/organization)}
                               {:key :internal-name
                                :title (text :t.administration/internal-name)}
                               {:key :active
                                :title (text :t.administration/active)
                                :filterable? false}
                               {:key :errors
                                :when-rows (fn [rows] (some (comp seq :value :errors) rows))
                                :title (text :t.administration/has-errors)
                                :filterable? false}
                               {:key :commands
                                :sortable? false
                                :filterable? false
                                :aria-label (text :t.actions/commands)}]
                     :rows [::forms-table-rows]
                     :default-sort-column :internal-name}]
    [:div.mt-3
     [table/search forms-table]
     [table/table forms-table]]))

(defn forms-page []
  (into [:div
         [administration/navigator]
         [document-title (text :t.administration/forms)]
         [flash-message/component :top]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[roles/show-when roles/+admin-write-roles+
            [atoms/commands [to-create-form]]
            [status-flags/status-flags-intro #(rf/dispatch [::fetch-forms])]]
           [forms-list]])))
