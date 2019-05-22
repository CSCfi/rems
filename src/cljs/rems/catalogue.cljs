(ns rems.catalogue
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.application-util :refer [form-fields-editable?]]
            [rems.atoms :refer [external-link document-title document-title]]
            [rems.cart :as cart]
            [rems.catalogue-util :refer [get-catalogue-item-title urn-catalogue-item-link urn-catalogue-item?]]
            [rems.guide-functions]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table2 :as table2]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [fetch unauthorized!]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   (if (roles/is-logged-in? (get-in db [:identity :roles]))
     {:db (dissoc db ::catalogue ::draft-applications)
      :dispatch-n [[::fetch-catalogue]
                   [::fetch-drafts]]}
     (do
       (unauthorized!)
       {}))))

;;;; catalogue

(rf/reg-event-fx
 ::fetch-catalogue
 (fn [{:keys [db]} _]
   (fetch "/api/catalogue"
          {:handler #(rf/dispatch [::fetch-catalogue-result %])})
   {:db (assoc db ::loading-catalogue? true)}))

(rf/reg-event-db
 ::fetch-catalogue-result
 (fn [db [_ catalogue]]
   (-> db
       (assoc ::catalogue catalogue)
       (dissoc ::loading-catalogue?))))

(rf/reg-sub ::full-catalogue (fn [db _] (::catalogue db)))

(rf/reg-sub
 ::catalogue
 (fn [_ _]
   (rf/subscribe [::full-catalogue]))
 (fn [catalogue _]
   (->> catalogue
        (filter :enabled)
        (remove :expired))))

(rf/reg-sub ::loading-catalogue? (fn [db _] (::loading-catalogue? db)))

;;;; draft applications

(rf/reg-event-fx
 ::fetch-drafts
 (fn [{:keys [db]} _]
   (fetch "/api/my-applications"
          {:handler #(rf/dispatch [::fetch-drafts-result %])})
   {:db (assoc db ::loading-drafts? true)}))

(rf/reg-event-db
 ::fetch-drafts-result
 (fn [db [_ applications]]
   (-> db
       (assoc ::draft-applications (filter form-fields-editable? applications))
       (dissoc ::loading-drafts?))))

(rf/reg-sub ::draft-applications (fn [db _] (::draft-applications db)))
(rf/reg-sub ::loading-drafts? (fn [db _] (::loading-drafts? db)))

;;;; UI

(defn- catalogue-item-more-info [item config]
  (when (urn-catalogue-item? item)
    [:a.btn.btn-secondary {:href (urn-catalogue-item-link item config) :target :_blank}
     (text :t.catalogue/more-info) " " [external-link]]))

(rf/reg-sub
 ::catalogue-table-rows
 (fn [_ _]
   [(rf/subscribe [::catalogue])
    (rf/subscribe [:language])])
 (fn [[catalogue language] _]
   (map (fn [item]
          (let [title (get-catalogue-item-title item language)]
            {:key (:id item)
             ;; TODO: helpers for the common case of simple values
             :name {:td [:td.name title]
                    :sort-value title
                    :filter-value (str/lower-case title)}
             :commands {:td [:td.commands
                             [catalogue-item-more-info item {}]
                             [cart/add-to-cart-button item]]}}))
        catalogue)))

(defn draft-application-list [drafts]
  (when (seq drafts)
    [:div.drafts
     [:h2 (text :t.catalogue/continue-existing-application)]
     [application-list/component
      {:visible-columns [:resource :last-activity :view]
       :items drafts}]]))

(defn catalogue-page []
  (let [language @(rf/subscribe [:language])
        loading-catalogue? @(rf/subscribe [::loading-catalogue?])
        drafts @(rf/subscribe [::draft-applications])
        loading-drafts? @(rf/subscribe [::loading-drafts?])
        catalogue-table {:id ::catalogue
                         :columns [{:key :name
                                    :title (text :t.catalogue/header)
                                    :sortable? true
                                    :filterable? true}
                                   {:key :commands}]
                         :rows [::catalogue-table-rows]
                         :default-sort-column :name}]
    [:div
     [document-title (text :t.catalogue/catalogue)]
     (if (or loading-catalogue? loading-drafts?)
       [spinner/big]
       [:div
        [draft-application-list drafts]
        [:h2 (text :t.catalogue/apply-resources)]
        [cart/cart-list-container language]
        [table2/search catalogue-table]
        [table2/table catalogue-table]])]))

(defn guide []
  [:div
   (component-info draft-application-list)
   (example "draft-list empty"
            [draft-application-list []])
   (example "draft-list with two drafts"
            [draft-application-list [{:application/id 1
                                      :application/resources [{:catalogue-item/title {:en "Item 5"}}]
                                      :application/state :application.state/draft
                                      :application/applicant "alice"
                                      :application/created "1980-01-02T13:45:00.000Z"
                                      :application/last-activity "2017-01-01T01:01:01:001Z"}
                                     {:application/id 2
                                      :application/resources [{:catalogue-item/title {:en "Item 3"}}]
                                      :application/state :application.state/draft
                                      :application/applicant "bob"
                                      :application/created "1971-02-03T23:59:00.000Z"
                                      :application/last-activity "2017-01-01T01:01:01:001Z"}]])])
