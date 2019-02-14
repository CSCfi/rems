(ns rems.administration.licenses
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.atoms :refer [external-link readonly-checkbox]]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::loading? true)
    ::fetch-licenses nil}))

(defn- fetch-licenses []
  (fetch "/api/licenses/" {:handler #(rf/dispatch [::fetch-licenses-result %])}))

(rf/reg-fx ::fetch-licenses (fn [_] (fetch-licenses)))

(rf/reg-event-db
 ::fetch-licenses-result
 (fn [db [_ licenses]]
   (-> db
       (assoc ::licenses licenses)
       (dissoc ::loading?))))

(rf/reg-sub ::licenses (fn [db _] (::licenses db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))

(rf/reg-sub
 ::sorting
 (fn [db _]
   (or (::sorting db)
       {:sort-column :title
        :sort-order :asc})))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (::filtering db)))

(defn- to-create-licenses []
  [:a.btn.btn-primary
   {:href "/#/administration/create-license"}
   (text :t.administration/create-license)])

(defn- to-view-license [license-id]
  [:a.btn.btn-primary
   {:href (str "/#/administration/licenses/" license-id)}
   (text :t.administration/view)])

(defn- licenses-columns []
  {:title {:header #(text :t.administration/licenses)
           :value :title}
   :type {:header #(text :t.administration/type)
          :value :licensetype}
   :start {:header #(text :t.administration/created)
           :value (comp localize-time :start)}
   :end {:header #(text :t.administration/end)
         :value (comp localize-time :end)}
   :active {:header #(text :t.administration/active)
            :value (comp readonly-checkbox :active)}
   :commands {:value (fn [license] [to-view-license (:id license)])
              :sortable? false
              :filterable? false}})

(defn- licenses-list
  "List of licenses"
  [licenses sorting filtering]
  [table/component
   {:column-definitions (licenses-columns)
    :visible-columns [:title :type :start :end :active :commands]
    :sorting sorting
    :filtering filtering
    :id-function :id
    :items licenses}])

(defn licenses-page []
  (let [licenses (rf/subscribe [::licenses])
        sorting (rf/subscribe [::sorting])
        filtering (rf/subscribe [::filtering])
        loading? (rf/subscribe [::loading?])]
    (fn []
      (into [:div
             [administration-navigator-container]
             [:h2 (text :t.administration/licenses)]]
            (if @loading?
              [[spinner/big]]
              [[to-create-licenses]
               [licenses-list
                @licenses
                (assoc @sorting :set-sorting    #(rf/dispatch [::set-sorting %]))
                (assoc @filtering :set-filtering    #(rf/dispatch [::set-filtering %]))]])))))
