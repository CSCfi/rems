(ns rems.administration.workflows
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :refer [readonly-checkbox document-title]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! put! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-old? false)
    :dispatch [::fetch-workflows]}))

(rf/reg-event-db
 ::fetch-workflows
 (fn [db]
   (fetch "/api/workflows/" {:url-params {:disabled true
                                          :archived (::display-old? db)
                                          :expired (::display-old? db)}
                             :handler #(rf/dispatch [::fetch-workflows-result %])})
   (assoc db ::loading? true)))

(rf/reg-event-db
 ::fetch-workflows-result
 (fn [db [_ workflows]]
   (-> db
       (assoc ::workflows workflows)
       (dissoc ::loading?))))

(rf/reg-sub ::workflows (fn [db _] (::workflows db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(rf/reg-event-fx
 ::update-workflow
 (fn [_ [_ item description]]
   (status-modal/common-pending-handler! description)
   (put! "/api/workflows/update"
         {:params (select-keys item [:id :enabled :archived])
          :handler (partial status-flags/common-update-handler! #(rf/dispatch [::fetch-workflows]))
          :error-handler status-modal/common-error-handler!})
   {}))

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))

(rf/reg-sub
 ::sorting
 (fn [db _]
   (or (::sorting db)
       {:sort-column :title
        :sort-order :asc})))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (::filtering db)))

(rf/reg-event-fx
 ::set-display-old?
 (fn [{:keys [db]} [_ display-old?]]
   {:db (assoc db ::display-old? display-old?)
    :dispatch [::fetch-workflows]}))
(rf/reg-sub ::display-old? (fn [db _] (::display-old? db)))


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
            :value (comp readonly-checkbox not :expired)}
   :commands {:values (fn [workflow]
                        [[to-view-workflow (:id workflow)]
                         [status-flags/enabled-toggle workflow #(rf/dispatch [::update-workflow %1 %2])]
                         [status-flags/archived-toggle workflow #(rf/dispatch [::update-workflow %1 %2])]])
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
         [:h1 [document-title (text :t.administration/workflows)]]]
        (if @(rf/subscribe [::loading?])
          [[spinner/big]]
          [[to-create-workflow]
           [status-flags/display-old-toggle
            @(rf/subscribe [::display-old?])
            #(rf/dispatch [::set-display-old? %])]
           [workflows-list
            @(rf/subscribe [::workflows])
            (assoc @(rf/subscribe [::sorting]) :set-sorting #(rf/dispatch [::set-sorting %]))
            (assoc @(rf/subscribe [::filtering]) :set-filtering #(rf/dispatch [::set-filtering %]))]])))
