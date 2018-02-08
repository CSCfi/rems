(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [clojure.string :as str]
            [re-frame.core :as re-frame]
            [rems.collapsible :as collapsible]
            [rems.guide-functions]
            [rems.text :refer [localize-state localize-time text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))


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
  [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#show-throughput-times-modal"}
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
    (into [:table.rems-table.actions
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
             [:td.commands (view-button app)]]))))

(defn- reviews [apps]
  [actions apps])

(defn- approvals [apps]
  [actions apps])

(defn- handled-applications
  "Creates a table containing a list of handled applications.

  The function takes the following parameters as arguments:
  apps:        collection of apps to be shown
  top-buttons: a set of extra buttons that will be shown on top of the table. This could include f.ex 'export as pdf' button."
  ([apps]
   (handled-applications apps nil))
  ([apps top-buttons]
   (when-not (empty? apps)
     [:div
      top-buttons
      (into [:table.rems-table.actions
             [:tr
              [:th (text :t.actions/application)]
              [:th (text :t.actions/resource)]
              [:th (text :t.actions/applicant)]
              [:th (text :t.actions/state)]
              [:th (text :t.actions/handled)]
              [:th]]
             (for [app (sort-by :handled apps)]
               [:tr.action
                [:td {:data-th (text :t.actions/application)} (:id app)]
                [:td {:data-th (text :t.actions/resource)} (str/join ", " (map :title (:catalogue-items app)))]
                [:td {:data-th (text :t.actions/applicant)} (:applicantuserid app)]
                [:td {:data-th (text :t.actions/state)} (text (localize-state (:state app)))]
                [:td {:data-th (text :t.actions/handled)} (localize-time (:handled app))]
                [:td.commands (view-button app)]])])])))

(defn- handled-approvals [apps]
  [handled-applications apps [report-buttons]])

(defn- handled-reviews
  [apps]
  [handled-applications apps])

;; TODO ensure ::actions is loaded when navigating to page
(defn- actions-page [reviews]
  (let [actions @(re-frame/subscribe [::actions])
        roles @(re-frame/subscribe [:roles])]
    [:div
     (when (:reviewer roles)
       [:div
        [collapsible/component
         {:id "open-reviews"
          :open? true
          :title (text :t.actions/open-reviews)
          :collapse [reviews (:reviews actions)]}]
        [:div.mt-3
         [collapsible/component
          {:id "handled-reviews"
           :title (text :t.actions/handled-reviews)
           :collapse [handled-reviews (:handled-reviews actions)]}]]])
     (when (:approver roles)
       [:div
        [collapsible/component
         {:id "open-approvals"
          :open? true
          :title (text :t.actions/open-approvals)
          :collapse [approvals (:approvals actions)]}]
        [:div.mt-3
         [collapsible/component
          {:id "handled-approvals"
           :title (text :t.actions/handled-approvals)
           :collapse [handled-approvals (:handled-approvals actions)]}]]])]))

(defn guide
  []
  [:div
   (component-info reviews)
   (example "reviews empty"
            [reviews []])
   (example "reviews"
            [reviews
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :applicantuserid "bob"}]])

   (component-info handled-reviews)
   (example "handled reviews"
            [handled-reviews
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :state "approved" :applicantuserid "bob"}]])

   (component-info approvals)
   (example "approvals empty"
            [approvals []])
   (example "approvals"
            [approvals
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :applicantuserid "bob"}]])

   (component-info handled-approvals)
   (example "handled approvals"
            [handled-approvals
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :state "approved" :applicantuserid "bob"}]])])
