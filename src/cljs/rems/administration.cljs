(ns rems.administration
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :refer [external-link]]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.db.catalogue :refer [urn-catalogue-item? get-catalogue-item-title disabled-catalogue-item?]]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch put!]]))

;; TODO copypaste from rems.catalogue, move to rems.db.catalogue?

(defn- fetch-catalogue []
  (fetch "/api/catalogue-items/" {:handler #(rf/dispatch [::fetch-catalogue-result %])}))

(rf/reg-fx
 ::fetch-catalogue
 (fn [_]
   (fetch-catalogue)))

(rf/reg-event-fx
 ::start-fetch-catalogue
 (fn [{:keys [db]}]
   {:db (assoc db ::loading? true)
    ::fetch-catalogue []}))

(rf/reg-event-db
 ::fetch-catalogue-result
 (fn [db [_ catalogue]]
   (-> db
       (assoc ::catalogue catalogue)
       (dissoc ::loading?))))

(rf/reg-sub
 ::catalogue
 (fn [db _]
   (::catalogue db)))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (::loading? db)))

(defn- update-catalogue-item [id state]
  (put! "/api/catalogue-items/update" {:params {:id id :state state}
                                       :handler #(rf/dispatch [::start-fetch-catalogue])}))

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

(defn- to-create-license-button []
  [:button.btn.btn-primary
   {:type "submit"
    :on-click #(dispatch! "/#/create-license")}
   (text :t.administration/create-license)])

(defn- to-create-resource-button []
  [:button.btn.btn-primary
   {:type "submit"
    :on-click #(dispatch! "/#/create-resource")}
   (text :t.administration/create-resource)])

(defn- to-create-workflow-button []
  [:button.btn.btn-primary
   {:type "submit"
    :on-click #(dispatch! "/#/create-workflow")}
   (text :t.administration/create-workflow)])

(defn- to-create-catalogue-item-button []
  [:button.btn.btn-primary
   {:type "submit"
    :on-click #(dispatch! "/#/create-catalogue-item")}
   (text :t.administration/create-catalogue-item)])

(defn- catalogue-item-button [item]
  (if (disabled-catalogue-item? item)
    [enable-button item]
    [disable-button item]))

(defn- catalogue-columns [language]
  {:name {:header #(text :t.catalogue/header)
          :value #(get-catalogue-item-title % language)}
   :commands {:value catalogue-item-button
              :sortable? false
              :filterable? false}})

(defn- catalogue-list
  "List of catalogue items"
  [items language]
  [table/component (catalogue-columns language) [:name :commands]
   {:sort-column :name
    :sort-order  :asc}
   (fn [_]) ; TODO: changing sorting
   :id items])

(defn administration-page []
  (let [catalogue (rf/subscribe [::catalogue])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      (into [:div
             [:h2 (text :t.navigation/administration)]]
            (if @loading?
              [[spinner/big]]
              [[:div
                [:div.col.commands
                 [to-create-license-button]
                 [to-create-resource-button]
                 [to-create-workflow-button]
                 [to-create-catalogue-item-button]]
                [catalogue-list @catalogue @language]]])))))
