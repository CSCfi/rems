(ns rems.catalogue
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.common.application-util :refer [form-fields-editable?]]
            [rems.atoms :refer [external-link document-title document-title]]
            [rems.cart :as cart]
            [rems.common.catalogue-util :refer [urn-catalogue-item-link]]
            [rems.flash-message :as flash-message]
            [rems.guide-functions]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text get-localized-title]]
            [rems.util :refer [fetch unauthorized!]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   (if (roles/is-logged-in? (get-in db [:identity :roles]))
     {:db (dissoc db ::catalogue ::draft-applications)
      :dispatch-n [[::fetch-catalogue]
                   [::fetch-drafts]
                   [:rems.table/reset]]}
     (do
       (unauthorized!)
       {}))))

;;;; catalogue

(rf/reg-event-fx
 ::fetch-catalogue
 (fn [{:keys [db]} _]
   ;; TODO: better error handler, don't show spinner if request has failed
   (fetch "/api/catalogue"
          {:handler #(rf/dispatch [::fetch-catalogue-result %])
           :error-handler (flash-message/default-error-handler :top "Fetch catalogue")})
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
   ;; TODO: better error handler, don't show spinner if request has failed
   (fetch "/api/my-applications"
          {:handler #(rf/dispatch [::fetch-drafts-result %])
           :error-handler (flash-message/default-error-handler :top "Fetch drafts")})
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

(defn- catalogue-item-more-info [item language config]
  (let [urn-link (urn-catalogue-item-link item config)
        more-info-link (get-in item [:localizations language :infourl])
        link (or more-info-link
                 urn-link)]
    (when link
      [:a.btn.btn-secondary
       {:href link
        :target :_blank
        :aria-label (str (text :t.catalogue/more-info)
                         ": "
                         (get-localized-title item language)
                         ", "
                         (text :t.link/opens-in-new-window))}
       (text :t.catalogue/more-info) " " [external-link]])))

(rf/reg-sub
 ::catalogue-table-rows
 (fn [_ _]
   [(rf/subscribe [::catalogue])
    (rf/subscribe [:language])])
 (fn [[catalogue language] _]
   (map (fn [item]
          {:key (:id item)
           :name {:value (get-localized-title item language)}
           :commands {:td [:td.commands
                           [catalogue-item-more-info item language {}]
                           [cart/add-to-cart-button item language]]}})
        catalogue)))

(defn draft-application-list []
  (let [applications ::draft-applications]
    (when (seq @(rf/subscribe [applications]))
      [:div
       [:h2 (text :t.catalogue/continue-existing-application)]
       (text :t.catalogue/continue-existing-application-intro)
       [application-list/list
        {:id applications
         :applications applications
         :visible-columns #{:resource :last-activity :view}
         :default-sort-column :last-activity
         :default-sort-order :desc}]])))

(defn- catalogue-table []
  (let [catalogue {:id ::catalogue
                   :columns [{:key :name
                              :title (text :t.catalogue/header)}
                             {:key :commands
                              :sortable? false
                              :filterable? false}]
                   :rows [::catalogue-table-rows]
                   :default-sort-column :name}]
    [:div
     [table/search catalogue]
     [table/table catalogue]]))

(defn catalogue-page []
  (let [loading-catalogue? @(rf/subscribe [::loading-catalogue?])
        loading-drafts? @(rf/subscribe [::loading-drafts?])]
    [:div
     [document-title (text :t.catalogue/catalogue)]
     [flash-message/component :top]
     (text :t.catalogue/intro)
     (if (or loading-catalogue? loading-drafts?)
       [spinner/big]
       [:div
        [draft-application-list]
        [:h2 (text :t.catalogue/apply-resources)]
        [cart/cart-list-container]
        [catalogue-table]])]))
