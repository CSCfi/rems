(ns rems.administration.resources
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
    ::fetch-resources nil}))

(defn- fetch-resources []
  (fetch "/api/resources/" {:handler #(rf/dispatch [::fetch-resources-result %])}))

(rf/reg-fx ::fetch-resources (fn [_] (fetch-resources)))

(rf/reg-event-db
 ::fetch-resources-result
 (fn [db [_ resources]]
   (-> db
       (assoc ::resources resources)
       (dissoc ::loading?))))

(rf/reg-sub ::resources (fn [db _] (::resources db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))
(rf/reg-sub ::sorting (fn [db _] (::sorting db {:sort-order :asc
                                                :sort-column :title})))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (or (::filtering db))))

(defn- to-create-resource []
  [:a.btn.btn-primary
   {:href "/#/administration/create-resource"}
   (text :t.administration/create-resource)])

(defn- to-view-resource [resource-id]
  [:a.btn.btn-primary
   {:href (str "/#/administration/resources/" resource-id)}
   (text :t.administration/view)])

(defn- resources-columns []
  {:organization {:header #(text :t.administration/organization)
                  :value :organization}
   :title {:header #(text :t.administration/resource)
           :value :resid}
   :start {:header #(text :t.administration/created)
           :value (comp localize-time :start)}
   :end {:header #(text :t.administration/end)
         :value (comp localize-time :end)}
   :active {:header #(text :t.administration/active)
            :value (comp readonly-checkbox :active)}
   :commands {:value (fn [resource] [to-view-resource (:id resource)])
              :sortable? false
              :filterable? false}})

(defn- resources-list
  "List of resources"
  [resources sorting filtering]
  [table/component
   {:column-definitions (resources-columns)
    :visible-columns [:organization :title :start :end :active :commands]
    :sorting sorting
    :filtering filtering
    :id-function :id
    :items resources}])

(defn resources-page []
  (into [:div
         [administration-navigator-container]
         [:h2 (text :t.administration/resources)]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-resource]
           [resources-list
            @(rf/subscribe [::resources])
            (assoc @(rf/subscribe [::sorting]) :set-sorting #(rf/dispatch [::set-sorting %]))
            (assoc @(rf/subscribe [::filtering]) :set-filtering #(rf/dispatch [::set-filtering %]))]])))
