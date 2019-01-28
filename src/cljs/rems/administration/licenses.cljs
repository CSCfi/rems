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

(rf/reg-event-db
 ::set-sorting
 (fn [db [_ sorting]]
   (assoc db ::sorting sorting)))

(rf/reg-sub
 ::sorting
 (fn [db _]
   (or (::sorting db)
       {:sort-column :title
        :sort-order :asc})))

(defn- to-create-licenses []
  [:a.btn.btn-primary
   {:href "/#/administration/create-license"}
   (text :t.administration/create-license)])

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
   :commands {:value :no-value
              :sortable? false
              :filterable? false}})

(defn- licenses-list
  "List of licenses"
  [licenses sorting]
  [table/component
   (licenses-columns)
   [:title :type :start :end :active :commands]
   sorting
   #(rf/dispatch [::set-sorting %])
   :id
   licenses])

(defn licenses-page []
  (let [licenses (rf/subscribe [::licenses])
        sorting (rf/subscribe [::sorting])
        loading? (rf/subscribe [::loading?])]
    (fn []
      (into [:div
             [administration-navigator-container]
             [:h2 (text :t.administration/licenses)]]
            (if @loading?
              [[spinner/big]]
              [[to-create-licenses]
               [licenses-list @licenses @sorting]])))))
