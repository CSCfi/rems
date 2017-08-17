(ns rems.actions
  (:require [clj-time.core :as time]
            [clj-time.format :as format]
            [compojure.core :refer [GET POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.applications :refer [localize-state]]
            [rems.collapsible :as collapsible]
            [rems.db.applications :as applications]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.role-switcher :refer [when-role has-roles?]]
            [rems.text :refer [text]]
            [rems.util :refer [errorf]]
            [ring.util.response :refer [redirect]]))

(def ^:private time-format (format/formatter "yyyy-MM-dd HH:mm"
                                             (time/default-time-zone)))

(defn view-button [app]
  [:a.btn.btn-secondary
   {:href (str "/form/" (:catid app) "/" (:id app))}
   (text :t.applications/view)])

(defn- actions-form-attrs [app]
  {:method "post"
   :action (str "/event/" (:id app) "/" (:curround app))})

(defn- confirm-modal [name-field action-title app role]
  [:div.modal.fade {:id (str name-field "-modal") :tabindex "-1" :role "dialog" :aria-labelledby "confirmModalLabel" :aria-hidden "true"}
   [:div.modal-dialog {:role "document"}
    [:div.modal-content
     [:form (actions-form-attrs app)
      (anti-forgery-field)
      [:div.modal-header
       [:h5#confirmModalLabel.modal-title (if (has-roles? role) (text :t.form/add-comments) (text :t.form/add-comments-applicant))]
       [:button.close {:type "button" :data-dismiss "modal" :aria-label (text :t.actions/cancel)}
        [:span {:aria-hidden "true"} "&times;"]]]
      [:div.modal-body
       [:div.form-group
        [:textarea.form-control {:name "comment"}]]]
      [:div.modal-footer
       [:button.btn.btn-secondary {:data-dismiss "modal"} (text :t.actions/cancel)]
       [:button.btn.btn-primary {:type "submit" :name name-field} action-title]]]]]])

(defn approval-confirm-modal [name-field action-title app]
  (confirm-modal name-field action-title app :approver))

(defn review-confirm-modal [name-field action-title app]
  (confirm-modal name-field action-title app :reviewer))

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

(defn- approve-button [app]
  (list
   [:button.btn.btn-primary {:type "button" :data-toggle "modal" :data-target "#approve-modal"}
    (text :t.actions/approve)]
   (approval-confirm-modal "approve" (text :t.actions/approve) app)))

(defn- reject-button [app]
  (list
   [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#reject-modal"}
    (text :t.actions/reject)]
   (approval-confirm-modal "reject" (text :t.actions/reject) app)))

(defn review-button [app]
  (list
   [:button.btn.btn-primary {:type "button" :data-toggle "modal" :data-target "#review-modal"}
    (text :t.reviews/review)]
   (review-confirm-modal "review" (text :t.reviews/review) app)))

(defn- return-button [app]
  (list
   [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#return-modal"}
    (text :t.actions/return)]
   (approval-confirm-modal "return" (text :t.actions/return) app)))

(defn- close-button [app]
  (list
   [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#close-modal"}
    (text :t.actions/close)]
   (approval-confirm-modal "close" (text :t.actions/close) app)))

(defn back-to-actions-button []
  [:a.btn.btn-secondary.pull-left {:href "/actions"} (text :t.form/back-actions)])

(defn approve-buttons [app]
  [:div.form-actions.inline
   (reject-button app)
   (approve-button app)])

(defn- export-pdf-button [app]
  (list
   [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#not-implemented-export-pdf-modal"}
    (text :t.actions/export-pdf)]
   (not-implemented-modal "not-implemented-export-pdf" (text :t.actions/export-pdf))))

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

(defn approve-form [app]
  [:div.commands
   (when-role :approver
     (back-to-actions-button))
   (close-button app)
   (reject-button app)
   (return-button app)
   (approve-button app)])

(defn review-form [app]
  [:div.commands
   (when-role :reviewer
     (back-to-actions-button))
   (review-button app)])

(defn- actions-item [app btn-fns]
  [:tr.action
   [:td {:data-th (text :t.actions/application)} (:id app)]
   [:td {:data-th (text :t.actions/resource)} (get-in app [:catalogue-item :title])]
   [:td {:data-th (text :t.actions/applicant)} (:applicantuserid app)]
   [:td {:data-th (text :t.actions/created)} (format/unparse time-format (:start app))]
   [:td.commands
    (view-button app)
    (btn-fns app)]])

(defn- handled-approvals-item [app]
  [:tr.approval
   [:td {:data-th (text :t.actions/application)} (:id app)]
   [:td {:data-th (text :t.actions/resource)} (get-in app [:catalogue-item :title])]
   [:td {:data-th (text :t.actions/applicant)} (:applicantuserid app)]
   [:td {:data-th (text :t.actions/state)} (text (localize-state (:state app)))]
   [:td {:data-th (text :t.actions/handled)} (format/unparse time-format (:handled app))]
   [:td.commands
    (view-button app)
    (export-pdf-button app)]])

(defn actions [apps btn-fns]
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
       (actions-item app btn-fns))]))

(defn reviews
  ([]
   (reviews (applications/get-application-to-review)))
  ([apps]
   (actions apps review-button)))

(defn approvals
  ([]
   (approvals (applications/get-approvals)))
  ([apps]
   (actions apps approve-buttons)))

(defn handled-approvals
  ([]
   (handled-approvals (applications/get-handled-approvals)))
  ([apps]
   (if (empty? apps)
     nil
     (list
      (report-buttons)
      [:table.rems-table.approvals
       [:tr
        [:th (text :t.actions/application)]
        [:th (text :t.actions/resource)]
        [:th (text :t.actions/applicant)]
        [:th (text :t.actions/state)]
        [:th (text :t.actions/handled)]
        [:th]]
       (for [app (sort-by :handled apps)]
         (handled-approvals-item app))
       ]))))

(defn guide
  []
  (list
   (example "reviews empty"
             (reviews []))
   (example "reviews"
            (reviews
             [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid "alice"}
              {:id 3 :catalogue-item {:title "bbbbbb"} :applicantuserid "bob"}]))
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
    (when-role :reviewer (reviews))
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
  (GET "/actions" [] (actions-page))
  (POST "/event/:id/:round" [id round :as request]
        (let [id (Long/parseLong id)
              round (Long/parseLong round)
              input (:form-params request)
              action (cond (get input "approve") :approve
                           (get input "reject") :reject
                           (get input "return") :return
                           (get input "review") :review
                           (get input "withdraw") :withdraw
                           (get input "close") :close
                           :else (errorf "Unknown action!"))
              comment (get input "comment")
              comment (when-not (empty? comment) comment)]
          (case action
            :approve (applications/approve-application id round comment)
            :reject (applications/reject-application id round comment)
            :return (applications/return-application id round comment)
            :review (applications/review-application id round comment)
            :withdraw (applications/withdraw-application id round comment)
            :close (applications/close-application id round comment))
          (assoc (redirect (if (or (has-roles? :approver) (has-roles? :reviewer)) "/actions" "/applications") :see-other)
                 :flash [{:status :success
                          :contents (case action
                                      :approve (text :t.actions/approve-success)
                                      :reject (text :t.actions/reject-success)
                                      :return (text :t.actions/return-success)
                                      :review (text :t.reviews/review-success)
                                      :withdraw (text :t.actions/withdraw-success)
                                      :close (text :t.actions/close-success))}]))
        ))
