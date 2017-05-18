(ns rems.approvals
  (:require [clj-time.core :as time]
            [clj-time.format :as format]
            [compojure.core :refer [GET POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.db.applications :as applications]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.role-switcher :refer [when-role]]
            [rems.text :refer [text]]
            [rems.util :refer [errorf]]
            [ring.util.response :refer [redirect]]))

(def ^:private time-format (format/formatter "yyyy-MM-dd HH:mm"
                                             (time/default-time-zone)))

(defn view-button [app]
  [:a.btn.btn-secondary
   {:href (str "/form/" (:catid app) "/" (:id app))}
   (text :t.applications/view)])

(defn- approve-button []
  [:button.btn.btn-primary {:type "submit" :name "approve"}
   (text :t.approvals/approve)])

(defn- reject-button []
  [:button.btn.btn-secondary {:type "submit" :name "reject"}
   (text :t.approvals/reject)])

(defn- return-button []
  [:button.btn.btn-secondary {:type "submit" :name "return"}
   (text :t.approvals/return)])

(defn- back-to-approvals-button []
  [:a.btn.btn-secondary.pull-left {:href "/approvals"} (text :t.form/back-approvals)])

(defn- approve-form-attrs [app]
  {:method "post"
   :action (str "/approvals/" (:id app) "/" (:curround app))})

(defn approve-buttons [app]
  [:form.inline (approve-form-attrs app)
   (anti-forgery-field)
   [:div.form-actions
    (reject-button)
    (approve-button)]])

(defn approve-form [app]
  [:form (approve-form-attrs app)
   (anti-forgery-field)
   [:div.form-group
    [:label {:for "comment"} (text :t.form/add-comments)]
    [:textarea.form-control {:name "comment"}]]
   [:div.actions
    (when-role :approver
      (back-to-approvals-button))
    (reject-button)
    (return-button)
    (approve-button)]])

(defn- approvals-item [app]
  [:tr.approval
   [:td {:data-th (text :t.approvals/application)} (:id app)]
   [:td {:data-th (text :t.approvals/resource)} (get-in app [:catalogue-item :title])]
   [:td {:data-th (text :t.approvals/applicant)} (:applicantuserid app)]
   [:td {:data-th (text :t.approvals/created)} (format/unparse time-format (:start app))]
   [:td.actions
    (view-button app)
    (approve-buttons app)]])

(defn approvals
  ([]
   (approvals (applications/get-approvals)))
  ([apps]
   (if (empty? apps)
     [:div.approvals.alert.alert-success (text :t/approvals.empty)]
     [:table.rems-table.approvals
      [:tr
       [:th (text :t.approvals/application)]
       [:th (text :t.approvals/resource)]
       [:th (text :t.approvals/applicant)]
       [:th (text :t.approvals/created)]
       [:th]]
      (for [app (sort-by :id apps)]
        (approvals-item app))])))

(defn guide
  []
  (list
   (example "approvals empty"
            (approvals []))
   (example "approvals"
            (approvals
             [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid "alice"}
              {:id 3 :catalogue-item {:title "bbbbbb"} :applicantuserid "bob"}]))))

(defn approvals-page []
  (layout/render
   "approvals"
   (approvals)))

(defroutes approvals-routes
  (GET "/approvals" [] (approvals-page))
  (POST "/approvals/:id/:round" [id round :as request]
        (let [id (Long/parseLong id)
              round (Long/parseLong round)
              input (:form-params request)
              action (cond (get input "approve") :approve
                           (get input "reject") :reject
                           (get input "return") :return
                           :else (errorf "Unknown action!"))
              comment (get input "comment")
              comment (when-not (empty? comment) comment)]
          (case action
            :approve (applications/approve-application id round comment)
            :reject (applications/reject-application id round comment)
            :return (applications/return-application id round comment))
          (assoc (redirect "/approvals" :see-other)
                 :flash [{:status :success
                         :contents (case action
                                     :approve (text :t.approvals/approve-success)
                                     :reject (text :t.approvals/reject-success)
                                     :return (text :t.approvals/return-success))}]))))
