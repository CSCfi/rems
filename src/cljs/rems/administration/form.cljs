(ns rems.administration.form
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field perform-action-button]]
            [rems.administration.create-form :refer [form-preview format-validation-errors]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.collapsible :as collapsible]
            [rems.common.util :refer [andstr]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [localized text]]
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

(defn edit-action [form-id]
  (atoms/edit-action
   {:class "edit-form"
    :on-click #(rf/dispatch [::edit-form form-id])}))

(defn edit-button [form-id]
  [atoms/action-button (edit-action form-id)])

(defn- copy-as-new-button [id]
  [atoms/link {:class "btn btn-secondary"}
   (str "/administration/forms/create/" id)
   (text :t.administration/copy-as-new)])

(defn- toggle-enabled [form]
  (status-flags/enabled-toggle-action
   {:on-change #(rf/dispatch [:rems.administration.forms/set-form-enabled %1 %2 [::enter-page (:form/id form)]])}
   form))

(defn- toggle-archived [form]
  (status-flags/archived-toggle-action
   {:on-change #(rf/dispatch [:rems.administration.forms/set-form-archived %1 %2 [::enter-page (:form/id form)]])}
   form))

(defn form-view [form]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "form"
     :title [:span (andstr (localized (get-in form [:organization :organization/short-name])) "/") (:form/internal-name form)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (localized (get-in form [:organization :organization/name]))]
              [inline-info-field (text :t.administration/internal-name) (get-in form [:form/internal-name])]
              (doall (for [[langcode title] (:form/external-title form)]
                       ^{:key langcode}
                       [inline-info-field (str (text :t.administration/external-title)
                                               " (" (str/upper-case (name langcode)) ")")
                        title]))
              [inline-info-field (text :t.administration/active) [readonly-checkbox {:value (status-flags/active? form)}]]]}]
   (let [id (:form/id form)]
     [:div.col.commands
      [administration/back-button "/administration/forms"]
      [roles/show-when roles/+admin-write-roles+
       [copy-as-new-button id]]
      [roles/show-when-can-modify-organization-item form
       [edit-button id]
       [perform-action-button (toggle-enabled form)]
       [perform-action-button (toggle-archived form)]]])
   (when-let [errors (:form/errors form)]
     [:div.alert.alert-danger
      [text :t.administration/has-errors]
      [format-validation-errors errors form]])
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
