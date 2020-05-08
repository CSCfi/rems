(ns rems.administration.form
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.create-form :refer [form-preview format-validation-errors]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.collapsible :as collapsible]
            [rems.common.util :refer [andstr]]
            [rems.flash-message :as flash-message]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [navigate! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ form-id]]
   {:db (assoc db ::loading? true)
    ::fetch-form [form-id]}))

(defn- fetch-form [form-id]
  (fetch (str "/api/forms/" form-id)
         {:handler #(rf/dispatch [::fetch-form-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch form")}))

(rf/reg-fx ::fetch-form (fn [[form-id]] (fetch-form form-id)))

(rf/reg-event-db
 ::fetch-form-result
 (fn [db [_ form]]
   (-> db
       (assoc ::form form)
       (dissoc ::loading?))))

(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::edit-form
 (fn [_ [_ id]]
   (let [description [text :t.administration/edit]]
     (fetch (str "/api/forms/" id "/editable")
            {:handler (fn [response]
                        (if (:success response)
                          (navigate! (str "/administration/forms/edit/" id))
                          (flash-message/show-default-error!
                           :top description [status-flags/format-update-failure response])))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(defn edit-button [id]
  [:button.btn.btn-primary
   {:type :button
    :on-click (fn []
                (rf/dispatch [:rems.spa/user-triggered-navigation])
                (rf/dispatch [::edit-form id]))}
   (text :t.administration/edit)])

(defn- copy-as-new-button [id]
  [atoms/link {:class "btn btn-secondary"}
   (str "/administration/forms/create/" id)
   (text :t.administration/copy-as-new)])

(defn form-view [form]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "form"
     :title [:span (andstr (:form/organization form) "/") (:form/title form)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (:form/organization form)]
              [inline-info-field (text :t.administration/title) (:form/title form)]
              [inline-info-field (text :t.administration/active) [readonly-checkbox {:value (status-flags/active? form)}]]]}]
   (let [id (:form/id form)]
     [:div.col.commands
      [administration/back-button "/administration/forms"]
      [roles/when roles/show-admin-edit-buttons?
       [edit-button id]
       [copy-as-new-button id]
       [status-flags/enabled-toggle form #(rf/dispatch [:rems.administration.forms/set-form-enabled %1 %2 [::enter-page id]])]
       [status-flags/archived-toggle form #(rf/dispatch [:rems.administration.forms/set-form-archived %1 %2 [::enter-page id]])]]])
   (when-let [errors (:form/errors form)]
     [:div.alert.alert-danger
      [text :t.administration/has-errors]
      [format-validation-errors errors form @(rf/subscribe [:language])]])
   [form-preview form]])
;; TODO Do we support form licenses?

(defn form-page []
  (let [form @(rf/subscribe [::form])
        loading? @(rf/subscribe [::loading?])]
    [:div
     [administration/navigator]
     [document-title (text :t.administration/form)]
     [flash-message/component :top]
     (if loading?
       [spinner/big]
       [form-view form])]))
