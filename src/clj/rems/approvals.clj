(ns rems.approvals
  (:require [rems.context :as context]
            [clj-time.core :as time]
            [clj-time.format :as format]
            [compojure.core :refer [defroutes GET POST]]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.text :refer [text]]
            [rems.db.core :as db]
            [rems.db.approvals :refer [get-approvals]]))

(def ^:private time-format (format/formatter "yyyy-MM-dd HH:mm"
                                             (time/default-time-zone)))

(defn- approvals-item [app]
  [:tr.approval
   [:td {:data-th (text :t.approvals/application)} (:id app)]
   [:td {:data-th (text :t.approvals/resource)} (get-in app [:catalogue-item :title])]
   [:td {:data-th (text :t.approvals/applicant)} (:applicantuserid app)]
   [:td {:data-th (text :t.approvals/created)} (format/unparse time-format (:start app))]
   [:td ]])

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
  (GET "/approvals" [] (approvals-page)))
