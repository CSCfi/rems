(ns rems.administration
  (:require [ajax.core :refer [GET]]
            [re-frame.core :as rf]
            [rems.atoms :refer [external-link]]
            [rems.db.catalogue :refer [urn-catalogue-item? get-catalogue-item-title disabled-catalogue-item?]]
            [rems.text :refer [text]]))

;; TODO copypaste from rems.catalogue, move to rems.db.catalogue?

(rf/reg-event-db
 ::catalogue
 (fn [db [_ catalogue]]
   (assoc db ::catalogue catalogue)))

(rf/reg-sub
 ::catalogue
 (fn [db _]
   (::catalogue db)))

(defn- fetch-catalogue []
  (GET "/api/catalogue/" {:handler #(rf/dispatch [::catalogue %])
                          :response-format :json
                          :keywords? true}))

(defn- disable-button []
  [:button.btn.btn-secondary
   {:type "submit"
    :on-click nil}
   (text :t.administration/disable)])

(defn- enable-button []
  [:button.btn.btn-primary
   {:type "submit"
    :on-click nil}
   (text :t.administration/enable)])

;; TODO make a generic table component? This is now copypasted from rems.catalogue
(defn- catalogue-item
  "Single catalogue item"
  [item language]
  (let [resid (:resid item)
        title (get-catalogue-item-title item language)
        component (if (urn-catalogue-item? item)
                    [:a.catalogue-item-link {:href resid :target :_blank} title " " [external-link]]
                    title)]
    [:tr
     [:td {:data-th (text :t.catalogue/header)} component]
     [:td.commands {:data-th ""} (if (disabled-catalogue-item? item)
                                   [enable-button item]
                                   [disable-button item])]]))

(defn- catalogue-list
  "List of catalogue items"
  [items language]
  [:table.rems-table.catalogue
   (into [:tbody
          [:tr
           [:th (text :t.catalogue/header)]]]
         (for [item items]
           [catalogue-item item language]))])

(defn administration-page []
  (fetch-catalogue)
  [:div
   [:h1 (text :t.navigation/administration)]
   [catalogue-list @(rf/subscribe [::catalogue]) @(rf/subscribe [:language])]])
