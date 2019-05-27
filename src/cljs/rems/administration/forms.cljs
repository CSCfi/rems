(ns rems.administration.forms
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :refer [readonly-checkbox document-title]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.table2 :as table2]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-old? false)
    :dispatch-n [[::fetch-forms]
                 [:rems.table2/reset]]}))

(rf/reg-event-db
 ::fetch-forms
 (fn [db]
   (fetch "/api/forms/" {:url-params {:disabled true
                                      :expired (::display-old? db)
                                      :archived (::display-old? db)}
                         :handler #(rf/dispatch [::fetch-forms-result %])})
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
 ::update-form
 (fn [_ [_ item description]]
   (status-modal/common-pending-handler! description)
   (put! "/api/forms/update"
         {:params (select-keys item [:id :enabled :archived])
          :handler (partial status-flags/common-update-handler! #(rf/dispatch [::fetch-forms]))
          :error-handler status-modal/common-error-handler!})
   {}))

(rf/reg-event-fx
 ::set-display-old?
 (fn [{:keys [db]} [_ display-old?]]
   {:db (assoc db ::display-old? display-old?)
    :dispatch [::fetch-forms]}))

(rf/reg-sub ::display-old? (fn [db _] (::display-old? db)))

(defn- to-create-form []
  [:a.btn.btn-primary
   {:href "/#/administration/create-form"}
   (text :t.administration/create-form)])

(defn- to-view-form [form]
  [:a.btn.btn-primary
   {:href (str "/#/administration/forms/" (:id form))}
   (text :t.administration/view)])

(rf/reg-sub
 ::forms-table-rows
 (fn [_ _]
   [(rf/subscribe [::forms])])
 (fn [[forms] _]
   (map (fn [form]
          {:key (:id form)
           :organization {:value (:organization form)}
           :title {:value (:title form)}
           :start (let [value (:start form)]
                    {:value value
                     :display-value (localize-time value)})
           :end (let [value (:end form)]
                  {:value value
                   :display-value (localize-time value)})
           :active (let [checked? (not (:expired form))]
                     {:td [:td.active
                           [readonly-checkbox checked?]]
                      :sort-value (if checked? 1 2)})
           :commands {:td [:td.commands
                           [to-view-form form]
                           [status-flags/enabled-toggle form #(rf/dispatch [::update-form %1 %2])]
                           [status-flags/archived-toggle form #(rf/dispatch [::update-form %1 %2])]]}})
        forms)))

(defn- forms-list []
  (let [forms-table {:id ::forms
                     :columns [{:key :organization
                                :title (text :t.administration/organization)}
                               {:key :title
                                :title (text :t.administration/title)}
                               {:key :start
                                :title (text :t.administration/created)}
                               {:key :end
                                :title (text :t.administration/end)}
                               {:key :active
                                :title (text :t.administration/active)
                                :filterable? false}
                               {:key :commands
                                :sortable? false
                                :filterable? false}]
                     :rows [::forms-table-rows]
                     :default-sort-column :title}]
    [:div
     [table2/search forms-table]
     [table2/table forms-table]]))

(defn forms-page []
  (into [:div
         [administration-navigator-container]
         [document-title (text :t.administration/forms)]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-form]
           [status-flags/display-old-toggle
            @(rf/subscribe [::display-old?])
            #(rf/dispatch [::set-display-old? %])]
           [forms-list]])))
