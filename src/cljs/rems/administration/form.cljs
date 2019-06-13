(ns rems.administration.form
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.create-form :refer [form-preview]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :refer [info-field readonly-checkbox document-title]]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [andstr]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [localize-time text text-format]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ form-id]]
   {:db (assoc db ::loading? true)
    ::fetch-form [form-id]}))

(defn- fetch-form [form-id]
  (fetch (str "/api/forms/" form-id)
         {:handler #(rf/dispatch [::fetch-form-result %])}))

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
   (status-modal/set-pending! {:title (text :t.administration/edit)})
   (fetch (str "/api/forms/" id "/editable")
          {:handler #(if (:success %)
                       (dispatch! (str "/#/administration/edit-form/" id))
                       (status-flags/update-error-handler! %))
           :error-handler status-modal/common-error-handler!})
   {}))

(defn- back-button []
  [:button.btn.btn-secondary
   {:type :button
    :on-click #(dispatch! "/#/administration/forms")}
   (text :t.administration/back)])

(defn- edit-button [id]
  [:button.btn.btn-primary
   {:type :button
    :on-click #(rf/dispatch [::edit-form id])}
   (text :t.administration/edit)])

(defn- copy-as-new-button [id]
  [:button.btn.btn-primary
   {:type :button
    :on-click #(dispatch! (str "/#/administration/create-form/" id))}
   (text :t.administration/copy-as-new)])

(defn form-view [form language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "form"
     :title [:span (andstr (:organization form) "/") (:title form)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (:organization form)]
              [inline-info-field (text :t.administration/title) (:title form)]
              [inline-info-field (text :t.administration/start) (localize-time (:start form))]
              [inline-info-field (text :t.administration/end) (localize-time (:end form))]
              [inline-info-field (text :t.administration/active) [readonly-checkbox (not (:expired form))]]]}]
   [:div.col.commands
    [back-button]
    [edit-button (:id form)]
    [copy-as-new-button (:id form)]]
   [form-preview form]])
;; TODO Do we support form licenses?

(defn form-page []
  (let [form (rf/subscribe [::form])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator-container]
       [document-title (text :t.administration/form)]
       (if @loading?
         [spinner/big]
         [form-view @form @language])])))
