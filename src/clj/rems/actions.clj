(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [clj-time.core :as time]
            [clj-time.format :as format]
            [compojure.core :refer [GET defroutes]]
            [rems.collapsible :as collapsible]
            [rems.db.applications :as applications]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.role-switcher :refer [when-role]]
            [rems.text :refer [localize-state text]]))

(def ^:private time-format (format/formatter "yyyy-MM-dd HH:mm"
                                             (time/default-time-zone)))

(defn view-button [app]
  [:a.btn.btn-secondary
   {:href (str "/form/" (:catid app) "/" (:id app))}
   (text :t.applications/view)])

(defn not-implemented-modal [name-field action-title]
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

(defn- view-rights-button []
  (list
   [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#view-rights-modal"}
    (text :t.actions/view-rights)]
   (not-implemented-modal "view-rights" (text :t.actions/view-rights))))

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

(defn report-buttons []
  [:div.form-actions.inline
   (load-application-states-button)
   (view-rights-button)
   (show-publications-button)
   (show-throughput-times-button)])

(defn actions [apps]
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
        [:td {:data-th (text :t.actions/resource)} (get-in app [:catalogue-item :title])]
        [:td {:data-th (text :t.actions/applicant)} (:applicantuserid app)]
        [:td {:data-th (text :t.actions/created)} (format/unparse time-format (:start app))]
        [:td.commands (view-button app)]])]))

(defn reviews
  ([]
   (reviews (applications/get-applications-to-review)))
  ([apps]
   (actions apps)))

(defn approvals
  ([]
   (approvals (applications/get-approvals)))
  ([apps]
   (actions apps)))

(defn handled-applications
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
          [:td {:data-th (text :t.actions/resource)} (get-in app [:catalogue-item :title])]
          [:td {:data-th (text :t.actions/applicant)} (:applicantuserid app)]
          [:td {:data-th (text :t.actions/state)} (text (localize-state (:state app)))]
          [:td {:data-th (text :t.actions/handled)} (format/unparse time-format (:handled app))]
          [:td.commands (view-button app)]])]))))

(defn handled-approvals
  ([]
   (handled-approvals (applications/get-handled-approvals)))
  ([apps]
   (handled-applications apps (report-buttons))))

(defn handled-reviews
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
             [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid "alice"}
              {:id 3 :catalogue-item {:title "bbbbbb"} :applicantuserid "bob"}]))
   (example "handled reviews"
            (handled-reviews
             [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid "alice"}
              {:id 3 :catalogue-item {:title "bbbbbb"} :state "approved" :applicantuserid "bob"}]))
   (example "approvals empty"
            (approvals []))
   (example "approvals"
            (approvals
             [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid "alice"}
              {:id 3 :catalogue-item {:title "bbbbbb"} :applicantuserid "bob"}]))
   (example "handled approvals"
            (handled-approvals
             [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid "alice"}
              {:id 3 :catalogue-item {:title "bbbbbb"} :state "approved" :applicantuserid "bob"}]))))

(defn actions-page []
  (layout/render
   "actions"
   [:div
    (when-role :reviewer
      (list
       (collapsible/component "open-reviews"
                              true
                              (text :t.actions/open-reviews)
                              (reviews))
       [:div.mt-3
        (collapsible/component "handled-reviews"
                               false
                               (text :t.actions/handled-reviews)
                               (handled-reviews))]))
    (when-role :approver
      (list
       (collapsible/component "open-approvals"
                              true
                              (text :t.actions/open-approvals)
                              (approvals))
       [:div.mt-3
        (collapsible/component "handled-approvals"
                               false
                               (text :t.actions/handled-approvals)
                               (handled-approvals))]))]))

;; TODO handle closing when no draft or anything saved yet
(defroutes actions-routes
  (GET "/actions" [] (actions-page)))
