(ns rems.administration
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [external-link]]
            [rems.db.catalogue :refer [urn-catalogue-item? get-catalogue-item-title disabled-catalogue-item?]]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::loading? true)
    ::fetch-catalogue nil}))


; catalogue items

;; TODO copypaste from rems.catalogue, move to rems.db.catalogue?
(defn- fetch-catalogue []
  (fetch "/api/catalogue-items/" {:handler #(rf/dispatch [::fetch-catalogue-result %])}))

(rf/reg-fx
 ::fetch-catalogue
 (fn [_]
   (fetch-catalogue)))

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
                                       :handler #(rf/dispatch [::enter-page])}))

(rf/reg-event-fx
 ::update-catalogue-item
 (fn [_ [_ id state]]
   (update-catalogue-item id state)
   {}))

(rf/reg-event-db
 ::set-sorting
 (fn [db [_ sorting]]
   (assoc db ::sorting sorting)))

(rf/reg-sub
 ::sorting
 (fn [db _]
   (or (::sorting db)
       {:sort-column :name
        :sort-order :asc})))

;;;; UI

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

(defn- to-create-catalogue-item-button []
  [:button.btn.btn-primary
   {:type "submit"
    :on-click #(dispatch! "/#/create-catalogue-item")}
   (text :t.administration/create-catalogue-item)])

(defn- to-create-form-button []
  [:button.btn.btn-primary
   {:type "submit"
    :on-click #(dispatch! "/#/create-form")}
   (text :t.administration/create-form)])

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
  [items language sorting]
  [table/component (catalogue-columns language) [:name :commands]
   sorting
   #(rf/dispatch [::set-sorting %])
   :id
   items])

(defn administration-page []
  (let [catalogue (rf/subscribe [::catalogue])
        language (rf/subscribe [:language])
        sorting (rf/subscribe [::sorting])
        loading? (rf/subscribe [::loading?])]
    (fn []
      (into [:div
             [:h2 (text :t.navigation/administration)]]
            (if @loading?
              [[spinner/big]]
              [[:div
                [:div.col.commands
                 [to-create-workflow-button]
                 [to-create-form-button]
                 [to-create-license-button]
                 [to-create-resource-button]
                 [to-create-catalogue-item-button]]
                [catalogue-list @catalogue @language @sorting]]])))))
