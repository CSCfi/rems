(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [compojure.core :refer [GET defroutes routes]]
            [clojure.string :as string]
            [rems.collapsible :as collapsible]
            [rems.db.applications :as applications]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.roles :refer [when-role]]
            [rems.text :refer [localize-state localize-time text]]))


(defn- view-button [app]
  [:a.btn.btn-primary
   {:href (str "/form/" (:id app))}
   (text :t.applications/view)])

(defn- not-implemented-modal [name-field action-title]
  [:div.modal.fade {:id (str name-field "-modal") :tabindex "-1" :role "dialog" :aria-labelledby "confirmModalLabel" :aria-hidden "true"}
   [:div.modal-dialog {:role "document"}
    [:div.modal-content
     [:div.modal-header
      [:h5#confirmModalLabel.modal-title (text :t.actions/not-implemented) " " action-title]
      [:button.close {:type "button" :data-dismiss "modal" :aria-label (text :t.actions/cancel)}
       [:span {:aria-hidden "true"} "&times;"]]]
     [:div.modal-footer
      [:button.btn.btn-secondary {:data-dismiss "modal"} (text :t.actions/cancel)]
      ]]]])

(defn- load-application-states-button []
  (list
   [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#load-application-states-modal"}
    (text :t.actions/load-application-states)]
   (not-implemented-modal "load-application-states" (text :t.actions/load-application-states))))

(defn- export-entitlements-button []
  [:a.btn.btn-secondary
   {:href "/entitlements.csv"}
   (text :t.actions/export-entitlements)])

(defn- show-publications-button []
  (list
   [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#show-publications-modal"}
    (text :t.actions/show-publications)]
   (not-implemented-modal "show-publications" (text :t.actions/show-publications))))

(defn- show-throughput-times-button []
  (list
   [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#show-throughput-times-modal"}
    (text :t.actions/show-throughput-times)]
   (not-implemented-modal "show-throughput-times" (text :t.actions/show-throughput-times))))

(defn- report-buttons []
  [:div.form-actions.inline
   (load-application-states-button)
   (export-entitlements-button)
   (show-publications-button)
   (show-throughput-times-button)])

(defn- actions [apps]
  (if (empty? apps)
    [:div.actions.alert.alert-success (text :t.actions/empty)]
    [:table.rems-table.actions
     [:tr
      [:th (text :t.actions/application)]
      [:th (text :t.actions/resource)]
      [:th (text :t.actions/applicant)]
      [:th (text :t.actions/created)]
      [:th]]
     (for [app (sort-by :id apps)]
       [:tr.action
        [:td {:data-th (text :t.actions/application)} (:id app)]
        [:td {:data-th (text :t.actions/resource)} (string/join ", " (map :title (:catalogue-items app)))]
        [:td {:data-th (text :t.actions/applicant)} (:applicantuserid app)]
        [:td {:data-th (text :t.actions/created)} (localize-time (:start app))]
        [:td.commands (view-button app)]])]))

(defn- reviews
  ([]
   (reviews (applications/get-applications-to-review)))
  ([apps]
   (actions apps)))

(defn- approvals
  ([]
   (approvals (applications/get-approvals)))
  ([apps]
   (actions apps)))

(defn- handled-applications
  "Creates a table containing a list of handled applications.

  The function takes the following parameters as arguments:
  apps:        collection of apps to be shown
  top-buttons: a set of extra buttons that will be shown on top of the table. This could include f.ex 'export as pdf' button."
  ([apps]
   (handled-applications apps nil))
  ([apps top-buttons]
   (when-not (empty? apps)
     (list
      top-buttons
      [:table.rems-table.actions
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
          [:td {:data-th (text :t.actions/resource)} (string/join ", " (map :title (:catalogue-items app)))]
          [:td {:data-th (text :t.actions/applicant)} (:applicantuserid app)]
          [:td {:data-th (text :t.actions/state)} (text (localize-state (:state app)))]
          [:td {:data-th (text :t.actions/handled)} (localize-time (:handled app))]
          [:td.commands (view-button app)]])]))))

(defn- handled-approvals
  ([]
   (handled-approvals (applications/get-handled-approvals)))
  ([apps]
   (handled-applications apps (report-buttons))))

(defn- handled-reviews
  ([]
   (handled-reviews (applications/get-handled-reviews)))
  ([apps]
   (handled-applications apps)))


(defn guide
  []
  (list
   (example "reviews empty"
            (reviews []))
   (example "reviews"
            (reviews
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :applicantuserid "bob"}]))
   (example "handled reviews"
            (handled-reviews
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :state "approved" :applicantuserid "bob"}]))
   (example "approvals empty"
            (approvals []))
   (example "approvals"
            (approvals
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :applicantuserid "bob"}]))
   (example "handled approvals"
            (handled-approvals
             [{:id 1 :catalogue-items [{:title "AAAAAAAAAAAAAA"}] :applicantuserid "alice"}
              {:id 3 :catalogue-items [{:title "bbbbbb"}] :state "approved" :applicantuserid "bob"}]))))

(defn- actions-page []
  (layout/render
   "actions"
   [:div
    (when-role :reviewer
      (list
       (collapsible/component
        {:id "open-reviews"
         :open? true
         :title (text :t.actions/open-reviews)
         :collapse (reviews)})
       [:div.mt-3
        (collapsible/component
         {:id "handled-reviews"
          :title (text :t.actions/handled-reviews)
          :collapse (handled-reviews)})]))
    (when-role :approver
      (list
       (collapsible/component
        {:id "open-approvals"
         :open? true
         :title (text :t.actions/open-approvals)
         :collapse (approvals)})
       [:div.mt-3
        (collapsible/component
         {:id "handled-approvals"
          :title (text :t.actions/handled-approvals)
          :collapse (handled-approvals)})]))]))

(defroutes actions-routes
  (GET "/actions" [] (actions-page)))
