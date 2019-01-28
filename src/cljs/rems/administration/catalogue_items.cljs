(ns rems.administration.catalogue-items
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.atoms :refer [external-link readonly-checkbox]]
            [rems.catalogue-util :refer [get-catalogue-item-title disabled-catalogue-item?]]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::loading? true)
    ::fetch-catalogue nil}))

(defn- fetch-catalogue []
  (fetch "/api/catalogue-items/" {:url-params {:expand :names}
                                  :handler #(rf/dispatch [::fetch-catalogue-result %])}))

(rf/reg-fx ::fetch-catalogue (fn [_] (fetch-catalogue)))

(rf/reg-event-db
 ::fetch-catalogue-result
 (fn [db [_ catalogue]]
   (-> db
       (assoc ::catalogue catalogue)
       (dissoc ::loading?))))

(rf/reg-sub ::catalogue (fn [db _] (::catalogue db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- update-catalogue-item [[id state]]
  (put! "/api/catalogue-items/update"
        {:params {:id id :state state}
         :handler #(rf/dispatch [::enter-page])}))

(rf/reg-fx ::update-catalogue-item update-catalogue-item)

(rf/reg-event-fx
 ::update-catalogue-item
 (fn [_ [_ id state]]
   {::update-catalogue-item [id state]}))

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

(defn- to-create-catalogue-item []
  [:a.btn.btn-primary
   {:href "/#/administration/create-catalogue-item"}
   (text :t.administration/create-catalogue-item)])

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

(defn- toggle-state-button [item]
  (if (disabled-catalogue-item? item)
    [enable-button item]
    [disable-button item]))

(defn- catalogue-columns [language]
  {:name {:header #(text :t.catalogue/header)
          :value #(get-catalogue-item-title % language)}
   :resource {:header #(text :t.administration/resource)
              :value :resource-name}
   :form {:header #(text :t.administration/form)
          :value :form-name}
   :workflow {:header #(text :t.administration/workflow)
              :value :workflow-name}
   :created {:header #(text :t.administration/created)
             :value (comp localize-time :start)}
   :end {:header #(text :t.administration/end)
         :value (comp localize-time :end)}
   :active {:header #(text :t.administration/active)
            :value (comp readonly-checkbox :active)}
   :commands {:value toggle-state-button
              :sortable? false
              :filterable? false}})

(defn- catalogue-list
  "List of catalogue items"
  [items language sorting]
  [table/component
   (catalogue-columns language)
   [:name :resource :form :workflow :created :active :commands]
   sorting
   #(rf/dispatch [::set-sorting %])
   :id
   items])

(defn catalogue-items-page []
  (let [catalogue (rf/subscribe [::catalogue])
        language (rf/subscribe [:language])
        sorting (rf/subscribe [::sorting])
        loading? (rf/subscribe [::loading?])]
    (fn []
      (into [:div
             [administration-navigator-container]
             [:h2 (text :t.administration/catalogue-items)]]
            (if @loading?
              [[spinner/big]]
              [[to-create-catalogue-item]
               [catalogue-list @catalogue @language @sorting]])))))
