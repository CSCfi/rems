(ns rems.administration.workflows
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
    ::fetch-workflows nil}))

(defn- fetch-workflows []
  (fetch "/api/workflows/" {:handler #(rf/dispatch [::fetch-workflows-result %])}))

(rf/reg-fx ::fetch-workflows (fn [_] (fetch-workflows)))

(rf/reg-event-db
 ::fetch-workflows-result
 (fn [db [_ workflows]]
   (-> db
       (assoc ::workflows workflows)
       (dissoc ::loading?))))

(rf/reg-sub ::workflows (fn [db _] (::workflows db)))
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

(defn- to-create-workflow []
  [:a.btn.btn-primary
   {:href "/#/administration/create-workflow"}
   (text :t.administration/create-workflow)])

(defn- to-view-workflow [workflow-id]
  [:a.btn.btn-primary
   {:href (str "/#/administration/workflows/" workflow-id)}
   (text :t.administration/view)])

(defn- workflows-columns []
  {:organization {:header #(text :t.administration/organization)
                  :value :organization}
   :title {:header #(text :t.administration/workflow)
           :value :title}
   :start {:header #(text :t.administration/created)
           :value (comp localize-time :start)}
   :end {:header #(text :t.administration/end)
         :value (comp localize-time :end)}
   :active {:header #(text :t.administration/active)
            :value (comp readonly-checkbox :active)}
   :commands {:value (fn [workflow] [to-view-workflow (:id workflow)])
              :sortable? false
              :filterable? false}})

(defn- workflows-list
  "List of workflows"
  [workflows sorting filtering]
  [table/component
   {:column-definitions (workflows-columns)
    :visible-columns [:organization :title :start :end :active :commands]
    :sorting sorting
    :filtering filtering
    :id-function :id
    :items workflows}])

;; TODO Very similar components are used in here, licenses, forms, resources
(defn workflows-page []
  (into [:div
         [administration-navigator-container]
         [:h2 (text :t.administration/workflows)]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-workflow]
           [workflows-list
            @(rf/subscribe [::workflows])
            (assoc @(rf/subscribe [::sorting]) :set-sorting  #(rf/dispatch [::set-sorting %]))
            (assoc @(rf/subscribe [::filtering]) :set-filtering  #(rf/dispatch [::set-filtering %]))]])))
