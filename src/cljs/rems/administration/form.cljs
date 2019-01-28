(ns rems.administration.form
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.atoms :refer [info-field]]
            [rems.collapsible :as collapsible]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-time text]]
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

(defn- back-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration/forms")}
   (text :t.administration/back)])

(defn- to-create-form []
  [:a.btn.btn-primary
   {:href "/#/administration/create-form"}
   (text :t.administration/create-form)])

(defn form-view [form]
  [collapsible/component
   {:id "form"
    :title (text :t.administration/form)
    :always [:div
             [info-field (text :t.administration/id) (:id form)]
             [info-field (text :t.administration/organization) (:organization form)]
             [info-field (text :t.administration/title) (:title form)]
             [info-field (text :t.administration/start) (localize-time (:start form))]
             [info-field (text :t.administration/end) (localize-time (:end form))]
             [info-field (text :t.administration/active) (str (:active form))]
             [:div.col.commands
              [back-button]]]}])

(defn form-page []
  (let [form (rf/subscribe [::form])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator-container]
       [:h2 (text :t.administration/form)]
       (if @loading?
         [spinner/big]
         [form-view @form])])))
