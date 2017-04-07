(ns rems.approvals
  (:require [rems.context :as context]
            [clj-time.core :as time]
            [clj-time.format :as format]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :refer [redirect]]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.text :refer [text]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.db.core :as db]
            [rems.db.approvals :refer [get-approvals approve]]))

(def ^:private time-format (format/formatter "yyyy-MM-dd HH:mm"
                                             (time/default-time-zone)))

(defn- approvals-item [app]
  [:tr.approval
   [:td {:data-th (text :t.approvals/application)} (:id app)]
   [:td {:data-th (text :t.approvals/resource)} (get-in app [:catalogue-item :title])]
   [:td {:data-th (text :t.approvals/applicant)} (:applicantuserid app)]
   [:td {:data-th (text :t.approvals/created)} (format/unparse time-format (:start app))]
   [:td [:form.inline {:method "post"
                       :action (str "/approvals/" (:id app) "/" (:curround app) "/approve")}
         (anti-forgery-field)
         [:button.btn.btn-primary {:type "submit"}
          (text :t.approvals/approve)]]]])

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
  (POST "/approvals/:id/:round/approve" [id round]
        (let [id (Long/parseLong id)
              round (Long/parseLong round)]
          (approve id round "")
          (assoc (redirect "/approvals" :see-other)
                 :flash {:status :success
                         :contents (text :t.approvals/success)}))))
