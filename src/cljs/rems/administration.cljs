(ns rems.administration
  (:require [ajax.core :refer [GET PUT]]
            [re-frame.core :as rf]
            [rems.atoms :refer [external-link]]
            [rems.db.catalogue :refer [urn-catalogue-item? get-catalogue-item-title disabled-catalogue-item?]]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [redirect-when-unauthorized]]))

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
                          :error-handler redirect-when-unauthorized
                          :response-format :json
                          :keywords? true}))

(defn- update-catalogue-item [id state]
  (PUT "/api/catalogue/update" {:format :json
                                :params {:id id :state state}
                                ;; TODO error handling
                                :error-handler redirect-when-unauthorized
                                :handler (fn [resp]
                                           (fetch-catalogue))}))

(rf/reg-event-fx
 ::update-catalogue-item
 (fn [_ [_ id state]]
   (update-catalogue-item id state)
   {}))

;;;; UI ;;;;

(defn- disable-button [item]
  [:button.btn.btn-secondary
   {:type "submit"
    :on-click #(rf/dispatch [::update-catalogue-item (:id item) "disabled"])}
   (text :t.administration/disable)])

(defn- enable-button [item]
  [:button.btn.btn-primary
   {:type "submit"
    :on-click #(rf/dispatch [::update-catalogue-item (:id item) "enabled"])}
   (text :t.administration/enable)])

(defn- catalogue-item-button [item]
  (if (disabled-catalogue-item? item)
    [enable-button item]
    [disable-button item]))

(defn- catalogue-columns [language]
  {:name {:header #(text :t.catalogue/header)
          :value #(get-catalogue-item-title % language)}
   :button {:value catalogue-item-button
            :sortable? false}})

(defn- catalogue-list
  "List of catalogue items"
  [items language]
  ;; TODO no sorting yet
  (table/component (catalogue-columns language) [:name :button]
                   [:name :asc] (fn [_])
                   :id items))

(defn administration-page []
  (fetch-catalogue)
  [:div
   [:h1 (text :t.navigation/administration)]
   [catalogue-list @(rf/subscribe [::catalogue]) @(rf/subscribe [:language])]])
