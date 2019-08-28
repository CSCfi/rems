(ns rems.administration.forms
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [readonly-checkbox document-title]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-old? false)
    :dispatch-n [[::fetch-forms]
                 [:rems.table/reset]]}))

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
 (fn [_ [_ form description]]
   (status-modal/common-pending-handler! description)
   (put! "/api/forms/update"
         {:params {:id (:form/id form)
                   :enabled (:enabled form)
                   :archived (:archived form)}
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
  [atoms/link {:class "btn btn-primary"}
   "/#/administration/create-form"
   (text :t.administration/create-form)])

(defn- to-view-form [form]
  [atoms/link {:class "btn btn-primary"}
   (str "/#/administration/forms/" (:form/id form))
   (text :t.administration/view)])

(defn- to-edit-form [form]
  [:button.btn.btn-primary
   {:type :button
    :on-click (fn []
                (rf/dispatch [:rems.spa/user-triggered-navigation])
                (rf/dispatch [:rems.administration.form/edit-form (:form/id form)]))}
   (text :t.administration/edit)])

(defn- copy-as-new-form [form]
  [atoms/link {:class "btn btn-primary"}
   (str "/#/administration/create-form/" (:form/id form))
   (text :t.administration/copy-as-new)])

(rf/reg-sub
 ::forms-table-rows
 (fn [_ _]
   [(rf/subscribe [::forms])])
 (fn [[forms] _]
   (map (fn [form]
          {:key (:form/id form)
           :organization {:value (:form/organization form)}
           :title {:value (:form/title form)}
           :start (let [value (:start form)]
                    {:value value
                     :display-value (localize-time value)})
           :end (let [value (:end form)]
                  {:value value
                   :display-value (localize-time value)})
           :active (let [checked? (status-flags/active? form)]
                     {:td [:td.active
                           [readonly-checkbox checked?]]
                      :sort-value (if checked? 1 2)})
           :commands {:td [:td.commands
                           [to-view-form form]
                           [to-edit-form form]
                           [copy-as-new-form form]
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
    [:div.mt-3
     [table/search forms-table]
     [table/table forms-table]]))

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
