(ns rems.administration.resources
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.atoms :refer [external-link readonly-checkbox]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.table :as table]
            [rems.text :refer [localize-time text]]
            [rems.util :refer [dispatch! fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::display-archived? false)
    :dispatch [::fetch-resources]}))

(rf/reg-event-fx
 ::fetch-resources
 (fn [{:keys [db]}]
   (fetch "/api/resources" {:url-params {:disabled true
                                         :archived (::display-archived? db)}
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
 (fn [_ [_ item]]
   ;; TODO: create API
   (put! "/api/resources/update"
         {:params (select-keys item [:id :enabled :archived])
          :handler #(rf/dispatch [::fetch-resources])
          :error-handler status-modal/common-error-handler!})
   {}))

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


;;; Archiving
;; TODO: deduplicate

(rf/reg-event-fx
 ::set-display-archived?
 (fn [{:keys [db]} [_ display-archived?]]
   {:db (assoc db ::display-archived? display-archived?)
    :dispatch [::fetch-resources]}))
(rf/reg-sub ::display-archived? (fn [db _] (::display-archived? db)))

(defn- disable-button [item]
  [:button.btn.btn-secondary.button-min-width
   {:type "button"
    :on-click #(rf/dispatch [::update-resource (assoc item :enabled false)])}
   (text :t.administration/disable)])

(defn- enable-button [item]
  [:button.btn.btn-primary.button-min-width
   {:type "button"
    :on-click #(rf/dispatch [::update-resource (assoc item :enabled true)])}
   (text :t.administration/enable)])

(defn- toggle-enabled-button [item]
  (if (:enabled item)
    [disable-button item]
    [enable-button item]))

(defn- archive-button [item]
  [:button.btn.btn-secondary.button-min-width
   {:type "button"
    :on-click #(rf/dispatch [::update-resource (assoc item :archived true)])}
   (text :t.administration/archive)])

(defn- unarchive-button [item]
  [:button.btn.btn-primary.button-min-width
   {:type "button"
    :on-click #(rf/dispatch [::update-resource (assoc item :archived false)])}
   (text :t.administration/unarchive)])

(defn- toggle-archived-button [item]
  (if (:archived item)
    [unarchive-button item]
    [archive-button item]))

(defn- display-archived-resources []
  (let [display-archived? @(rf/subscribe [::display-archived?])
        toggle #(rf/dispatch [::set-display-archived? (not display-archived?)])]
    [:div.form-check.form-check-inline {:style {:float "right"}}
     [:input.form-check-input {:type "checkbox"
                               :id "display-archived"
                               :checked display-archived?
                               :on-change toggle}]
     [:label.form-check-label {:for "display-archived"}
      (text :t.administration/display-archived)]]))


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
   :commands {:values (fn [resource]
                        [[to-view-resource (:id resource)]
                         [toggle-enabled-button resource]
                         [toggle-archived-button resource]])
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
           [display-archived-resources]
           [resources-list
            @(rf/subscribe [::resources])
            (assoc @(rf/subscribe [::sorting]) :set-sorting #(rf/dispatch [::set-sorting %]))
            (assoc @(rf/subscribe [::filtering]) :set-filtering #(rf/dispatch [::set-filtering %]))]])))
