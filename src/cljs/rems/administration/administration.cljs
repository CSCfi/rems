(ns rems.administration.administration
  (:require [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.common.roles :as roles]
            [rems.guide-util :refer [component-info example]]
            [rems.navbar :as navbar]
            [rems.text :refer [text]]))

(rf/reg-event-db
 ::remember-current-page
 (fn [db _]
   (assoc db ::previous-page js/window.location.href)))

(rf/reg-sub ::previous-page (fn [db] (::previous-page db)))

(defn back-button [href]
  (let [previous-page @(rf/subscribe [::previous-page])]
    [atoms/link {:class "btn btn-secondary" :id :back}
     (or previous-page href)
     (text :t.administration/back)]))

(defn navigator []
  [:div#administration-menu.navbar.mt-2.mb-4.mx-auto
   [navbar/nav-link "/administration/catalogue-items" (text :t.administration/catalogue-items)]
   [navbar/nav-link "/administration/resources" (text :t.administration/resources)]
   [navbar/nav-link "/administration/forms" (text :t.administration/forms)]
   [navbar/nav-link "/administration/workflows" (text :t.administration/workflows)]
   [navbar/nav-link "/administration/licenses" (text :t.administration/licenses)]
   [navbar/nav-link "/administration/organizations" (text :t.administration/organizations)]
   [:div.mx-4]
   [navbar/nav-link "/administration/blacklist" (text :t.administration/blacklist)]
   [roles/show-when #{:owner :reporter}
    [navbar/nav-link "/administration/reports" (text :t.administration/reports)]]])

(rf/reg-event-fx
 ::set-display-own-organization-only
 (fn [{:keys [db]} [_ display-own-organization-only?]]
   {:db (assoc db ::display-own-organization-only display-own-organization-only?)}))

(rf/reg-sub ::display-own-organization-only (fn [db _] (::display-own-organization-only db true)))

(rf/reg-sub
 ::personal-organizations
 (fn [_ _]
   [(rf/subscribe [:owned-organizations])
    (rf/subscribe [:handled-organizations])])
 (fn [[owned-organizations handled-organizations] _]
   (concat owned-organizations handled-organizations)))

(rf/reg-sub
 ::displayed-organization-ids
 (fn [_ _]
   [(rf/subscribe [::display-own-organization-only])
    (rf/subscribe [:organizations])
    (rf/subscribe [::personal-organizations])])
 (fn [[display-own-organization-only? all-organizations personal-organizations] _]
   (if (or (not display-own-organization-only?)
           (roles/has-roles? :owner :reporter))
     (set (map :organization/id all-organizations))
     (set (map :organization/id personal-organizations)))))

(defn filter-by-displayed-organization [displayed-organization-ids f coll]
  (filter (fn [x] (contains? displayed-organization-ids (f x)))
          coll))

(defn display-own-organization-only []
  (let [display-own-organization-only? @(rf/subscribe [::display-own-organization-only])
        on-change (fn [] (rf/dispatch [::set-display-own-organization-only (not display-own-organization-only?)]))]
    [:div.form-check.form-check-inline.pointer
     [atoms/checkbox {:id :display-archived
                      :class :form-check-input
                      :value display-own-organization-only?
                      :on-change on-change}]
     [:label.form-check-label {:for :display-archived :on-click on-change}
      (text :t.administration/display-own-organization-only)]]))

(defn own-organization-selection []
  [roles/show-when-not #{:owner :reporter}
   (let [organizations @(rf/subscribe [::personal-organizations])]
     (when (> (count organizations) 1) ; don't bother showing if there is only 1
       [:div.mt-1.d-flex.flex-row.align-items-start.justify-content-between {:style {:gap "2rem"}}
        [:p (text :t.administration/display-own-organization-only-explanation)]
        [display-own-organization-only]]))])

(defn guide []
  [:div
   (component-info navigator)
   (example "navigator"
            [navigator])])
