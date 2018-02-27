(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [ajax.core :refer [GET]]
            [clojure.string :as str]
            [re-frame.core :as re-frame]
            [rems.collapsible :as collapsible]
            [rems.guide-functions]
            [rems.text :refer [localize-state localize-time text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn- fetch-actions []
  (GET "/api/actions/" {:handler #(re-frame/dispatch [::fetch-actions-result %])
                        :response-format :json
                        :keywords? true}))

(re-frame/reg-event-db
 ::fetch-actions-result
 (fn [db [_ result]]
   (assoc db ::actions result) ))


(re-frame/reg-sub
 ::actions
 (fn [db _]
   (::actions db)))

(defn- view-button [app]
  [:a.btn.btn-primary
   {:href (str "/form/" (:id app))}
   (text :t.applications/view)])

;; TODO not implemented
(defn- load-application-states-button []
  [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#load-application-states-modal" :disabled true}
   (text :t.actions/load-application-states)])

(defn- export-entitlements-button []
  [:a.btn.btn-secondary
   {:href "/entitlements.csv"}
   (text :t.actions/export-entitlements)])

;; TODO not implemented
(defn- show-publications-button []
  [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#show-publications-modal" :disabled true}
   (text :t.actions/show-publications)])

;; TODO not implemented
(defn- show-throughput-times-button []
  [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#show-throughput-times-modal" :disabled true}
   (text :t.actions/show-throughput-times)])

(defn- report-buttons []
  [:div.form-actions.inline
   [load-application-states-button]
   [export-entitlements-button]
   [show-publications-button]
   [show-throughput-times-button]])

(defn- actions [apps]
  (if (empty? apps)
    [:div.actions.alert.alert-success (text :t.actions/empty)]
    [:table.rems-table.actions
     (into [:tbody
            [:tr
             [:th (text :t.actions/application)]
             [:th (text :t.actions/resource)]
             [:th (text :t.actions/applicant)]
             [:th (text :t.actions/created)]
             [:th]]]
           (for [app (sort-by :id apps)]
             [:tr.action
              [:td {:data-th (text :t.actions/application)} (:id app)]
              [:td {:data-th (text :t.actions/resource)} (str/join ", " (map :title (:catalogue-items app)))]
              [:td {:data-th (text :t.actions/applicant)} (:applicantuserid app)]
              [:td {:data-th (text :t.actions/created)} (localize-time (:start app))]
              [:td.commands (view-button app)]]))]))

(defn- open-reviews [apps]
  [actions apps])

(defn- open-approvals [apps]
  [actions apps])

(defn- handled-applications
  "Creates a table containing a list of handled applications.

  The function takes the following parameters as arguments:
  apps:        collection of apps to be shown
  top-buttons: a set of extra buttons that will be shown on top of the table. This could include f.ex 'export as pdf' button."
  [apps top-buttons]
  (if (empty? apps)
    [:div.actions.alert.alert-success (text :t.actions/no-handled-yet)]
    [:div
     top-buttons
     [:table.rems-table.actions
      (into [:tbody
             [:tr
              [:th (text :t.actions/application)]
              [:th (text :t.actions/resource)]
              [:th (text :t.actions/applicant)]
              [:th (text :t.actions/state)]
              [:th (text :t.actions/handled)]
              [:th]]]
            (for [app (sort-by :handled apps)]
              [:tr.action
               [:td {:data-th (text :t.actions/application)} (:id app)]
               [:td {:data-th (text :t.actions/resource)} (str/join ", " (map :title (:catalogue-items app)))]
               [:td {:data-th (text :t.actions/applicant)} (:applicantuserid app)]
               [:td {:data-th (text :t.actions/state)} (localize-state (:state app))]
               [:td {:data-th (text :t.actions/handled)} (localize-time (:handled app))]
               [:td.commands [view-button app]]]))]]))

(defn- handled-approvals [apps]
  [handled-applications apps [report-buttons]])

(defn- handled-reviews
  [apps]
  [handled-applications apps nil])

;; TODO ensure ::actions is loaded when navigating to page
(defn actions-page [reviews]
  (fetch-actions)
  (let [actions @(re-frame/subscribe [::actions])]
    [:div
     (when (:reviewer? actions)
       [:div
        [:div
         [collapsible/component
          {:id "open-reviews"
           :open? true
           :title (text :t.actions/open-reviews)
           :collapse [open-reviews (:reviews actions)]}]
         [:div.my-3
          [collapsible/component
           {:id "handled-reviews"
            :title (text :t.actions/handled-reviews)
            :collapse [handled-reviews (:handled-reviews actions)]}]]]])
     (when (:approver? actions)
       [:div
        [:div
         [collapsible/component
          {:id "open-approvals"
           :open? true
           :title (text :t.actions/open-approvals)
           :collapse [open-approvals (:approvals actions)]}]
         [:div.mt-3
          [collapsible/component
           {:id "handled-approvals"
            :title (text :t.actions/handled-approvals)
            :collapse [handled-approvals (:handled-approvals actions)]}]]]])]))

(defn guide
  []
  [:div
   (component-info open-reviews)
   (example "open-reviews empty"
            [open-reviews []])
   (example "open-reviews"
            [open-reviews
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :applicantuserid "bob"}]])

   (component-info handled-reviews)
   (example "handled-reviews"
            [handled-reviews
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :state "approved" :applicantuserid "bob"}]])

   (component-info open-approvals)
   (example "open-approvals empty"
            [open-approvals []])
   (example "open-approvals"
            [open-approvals
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :applicantuserid "bob"}]])

   (component-info handled-approvals)
   (example "handled-approvals"
            [handled-approvals
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :state "approved" :applicantuserid "bob"}]])])
