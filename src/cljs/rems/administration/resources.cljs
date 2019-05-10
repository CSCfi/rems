(ns rems.administration.resources
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :refer [external-link readonly-checkbox]]
            [rems.catalogue-util :refer [get-catalogue-item-title]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-old? false)
    :dispatch [::fetch-resources]}))

(rf/reg-event-fx
 ::fetch-resources
 (fn [{:keys [db]}]
   (fetch "/api/resources" {:url-params {:disabled true
                                         :expired (::display-old? db)
                                         :archived (::display-old? db)}
                            :handler #(rf/dispatch [::fetch-resources-result %])
                            :error-handler status-modal/common-error-handler!})
   {:db (assoc db ::loading? true)}))

(rf/reg-event-db
 ::fetch-resources-result
 (fn [db [_ resources]]
   (-> db
       (assoc ::resources resources)
       (dissoc ::loading?))))

(rf/reg-sub ::resources (fn [db _] (::resources db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::update-resource
 (fn [{:keys [db]} [_ item description]]
   (status-modal/common-pending-handler! description)
   (put! "/api/resources/update"
         {:params (select-keys item [:id :enabled :archived])
          :handler (partial status-flags/common-update-handler! #(rf/dispatch [::fetch-resources]))
          :error-handler status-modal/common-error-handler!})
   {}))

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))
(rf/reg-sub ::sorting (fn [db _] (::sorting db {:sort-order :asc
                                                :sort-column :title})))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (or (::filtering db))))

(rf/reg-event-fx
 ::set-display-old?
 (fn [{:keys [db]} [_ display-old?]]
   {:db (assoc db ::display-old? display-old?)
    :dispatch [::fetch-resources]}))
(rf/reg-sub ::display-old? (fn [db _] (::display-old? db)))

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
            :value (comp readonly-checkbox not :expired)}
   :commands {:values (fn [resource]
                        [[to-view-resource (:id resource)]
                         [status-flags/enabled-toggle resource #(rf/dispatch [::update-resource %1 %2])]
                         [status-flags/archived-toggle resource #(rf/dispatch [::update-resource %1 %2])]])
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
           [status-flags/display-old-toggle
            @(rf/subscribe [::display-old?])
            #(rf/dispatch [::set-display-old? %])]
           [resources-list
            @(rf/subscribe [::resources])
            (assoc @(rf/subscribe [::sorting]) :set-sorting #(rf/dispatch [::set-sorting %]))
            (assoc @(rf/subscribe [::filtering]) :set-filtering #(rf/dispatch [::set-filtering %]))]])))
