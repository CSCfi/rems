(ns rems.administration.administration
  (:require [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.guide-utils :refer [component-info example]]
            [rems.navbar :as navbar]
            [rems.text :refer [text]]))

(rf/reg-event-db
 ::remember-current-page
 (fn [db _]
   (assoc db ::previous-page js/window.location.href)))

(rf/reg-sub ::previous-page (fn [db] (::previous-page db)))

(defn back-button [href]
  (let [previous-page @(rf/subscribe [::previous-page])]
    [atoms/link {:class "btn btn-secondary"}
     (or previous-page href)
     (text :t.administration/back)]))

(defn navigator []
  [:div#administration-menu.navbar.mb-4.mr-auto.ml-auto
   [navbar/nav-link "/administration/catalogue-items" (text :t.administration/catalogue-items)]
   [navbar/nav-link "/administration/resources" (text :t.administration/resources)]
   [navbar/nav-link "/administration/forms" (text :t.administration/forms)]
   [navbar/nav-link "/administration/workflows" (text :t.administration/workflows)]
   [navbar/nav-link "/administration/licenses" (text :t.administration/licenses)]
   [navbar/nav-link "/administration/reports" (text :t.administration/reports)]
   [navbar/nav-link "/administration/blacklist" (text :t.administration/blacklist)]
   [navbar/nav-link "/administration/organizations" (text :t.administration/organizations)]])

(defn guide []
  [:div
   (component-info navigator)
   (example "navigator"
            [navigator])])
