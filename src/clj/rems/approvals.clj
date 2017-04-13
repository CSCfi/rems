(ns rems.approvals
  (:require [rems.context :as context]
            [clj-time.core :as time]
            [clj-time.format :as format]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :refer [redirect]]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.text :refer [text]]
            [rems.util :refer [errorf]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.db.core :as db]
            [rems.db.approvals :refer [get-approvals approve reject]]))

(def ^:private time-format (format/formatter "yyyy-MM-dd HH:mm"
                                             (time/default-time-zone)))

(defn view-button [app]
  [:a.btn.btn-primary
   {:href (str "/form/" (:catid app) "/" (:id app))}
   (text :t.applications/view)])

(defn- approve-button []
  [:button.btn.btn-success {:type "submit" :name "approve"}
   (text :t.approvals/approve)])

(defn- reject-button []
  [:button.btn.btn-danger {:type "submit" :name "reject"}
   (text :t.approvals/reject)])

(defn- approve-form-attrs [app]
  {:method "post"
   :action (str "/approvals/" (:id app) "/" (:curround app))})

(defn approve-buttons [app]
  [:form.inline (approve-form-attrs app)
   (anti-forgery-field)
   (approve-button)
   (reject-button)])

(defn approve-form [app]
  [:form (approve-form-attrs app)
   (anti-forgery-field)
   [:div.form-group
    [:label {:for "comment"} (text :t.approvals/comment)]
    [:textarea.form-control {:name "comment"}]]
   [:div.actions
    (approve-button)
    (reject-button)]])

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
   (approvals (get-approvals)))
  ([apps]
   [:table.rems-table.approvals
    [:tr
     [:th (text :t.approvals/application)]
     [:th (text :t.approvals/resource)]
     [:th (text :t.approvals/applicant)]
     [:th (text :t.approvals/created)]
     [:th]]
    (for [app (sort-by :id apps)]
      (approvals-item app))]))

(defn guide
  []
  (example "approvals"
           (approvals
            [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid "alice"}
             {:id 3 :catalogue-item {:title "bbbbbb"} :applicantuserid "bob"}])))

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
                           :else (errorf "Unknown action!"))
              comment (get input "comment")
              comment (when-not (empty? comment) comment)]
          (case action
            :approve (approve id round comment)
            :reject (reject id round comment))
          (assoc (redirect "/approvals" :see-other)
                 :flash {:status :success
                         :contents (case action
                                     :approve (text :t.approvals/approve-success)
                                     :reject (text :t.approvals/reject-success))}))))
