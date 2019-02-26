(ns rems.administration.catalogue-items
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.atoms :refer [external-link readonly-checkbox]]
            [rems.catalogue-util :refer [get-catalogue-item-title]]
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
        {:params {:id id
                  :enabled (= "enabled" state)}
         :handler #(rf/dispatch [::enter-page])}))

(rf/reg-fx ::update-catalogue-item update-catalogue-item)

(rf/reg-event-fx
 ::update-catalogue-item
 (fn [_ [_ id state]]
   {::update-catalogue-item [id state]}))

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))

(rf/reg-sub
 ::sorting
 (fn [db _]
   (or (::sorting db)
       {:sort-column :name
        :sort-order :asc})))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (::filtering db)))

(defn- to-create-catalogue-item []
  [:a.btn.btn-primary
   {:href "/#/administration/create-catalogue-item"}
   (text :t.administration/create-catalogue-item)])

(defn- to-catalogue-item [catalogue-item-id]
  [:a.btn.btn-primary
   {:href (str "/#/administration/catalogue-items/" catalogue-item-id)}
   (text :t.administration/view)])

(defn- disable-button [item]
  [:button.btn.btn-secondary.button-min-width
   {:type "submit"
    :on-click #(rf/dispatch [::update-catalogue-item (:id item) "disabled"])}
   (text :t.administration/disable)])

(defn- enable-button [item]
  [:button.btn.btn-primary.button-min-width
   {:type "submit"
    :on-click #(rf/dispatch [::update-catalogue-item (:id item) "enabled"])}
   (text :t.administration/enable)])

(defn- toggle-enabled-button [item]
  (if (:enabled item)
    [disable-button item]
    [enable-button item]))

(defn- catalogue-columns [language]
  {:name {:header #(text :t.catalogue/header)
          :value #(get-catalogue-item-title % language)}
   :resource {:header #(text :t.administration/resource)
              :value (fn [row]
                       [:a {:href (str "#/administration/resources/" (:resource-id row))}
                        (:resource-name row)])
              :sort-value :resource-name
              :filter-value :resource-name}
   :form {:header #(text :t.administration/form)
          :value (fn [row]
                   [:a {:href (str "#/administration/forms/" (:formid row))}
                    (:form-name row)])
          :sort-value :form-name
          :filter-value :form-name}
   :workflow {:header #(text :t.administration/workflow)
              :value (fn [row]
                       [:a {:href (str "#/administration/workflows/" (:wfid row))}
                        (:workflow-name row)])
              :sort-value :workflow-name
              :filter-value :workflow-name}
   :created {:header #(text :t.administration/created)
             :value (comp localize-time :start)}
   :end {:header #(text :t.administration/end)
         :value (comp localize-time :end)}
   :active {:header #(text :t.administration/active)
            :value (comp readonly-checkbox :enabled)}
   :commands {:values (fn [item]
                        [[to-catalogue-item (:id item)]
                         [toggle-enabled-button item]])
              :sortable? false
              :filterable? false}})

(defn- catalogue-list
  "List of catalogue items"
  [items language sorting filtering]
  [table/component
   {:column-definitions (catalogue-columns language)
    :visible-columns [:name :resource :form :workflow :created :active :commands]
    :sorting sorting
    :filtering filtering
    :id-function :id
    :items items}])

(defn catalogue-items-page []
  (into [:div
         [administration-navigator-container]
         [:h2 (text :t.administration/catalogue-items)]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-catalogue-item]
           [catalogue-list
            @(rf/subscribe [::catalogue])
            @(rf/subscribe [:language])
            (assoc @(rf/subscribe [::sorting]) :set-sorting #(rf/dispatch [::set-sorting %]))
            (assoc @(rf/subscribe [::filtering]) :set-filtering #(rf/dispatch [::set-filtering %]))]])))
