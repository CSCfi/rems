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

(defn guide []
  [:div
   (component-info navigator)
   (example "navigator"
            [navigator])])
