(ns rems.administration.form
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.create-form :refer [form-preview]]
            [rems.atoms :refer [info-field readonly-checkbox document-title]]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [andstr]]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-time text text-format]]
            [rems.util :refer [dispatch! fetch]]))

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

(defn- back-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration/forms")}
   (text :t.administration/back)])

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
   [:div.col.commands [back-button]]
   [form-preview form]])
;; TODO Do we support form licenses?

(defn form-page []
  (let [form (rf/subscribe [::form])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator-container]
       [:h1 [document-title (text :t.administration/form)]]
       (if @loading?
         [spinner/big]
         [form-view @form @language])])))
