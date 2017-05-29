(ns rems.applications
  (:require [clj-time.core :as time]
            [clj-time.format :as format]
            [rems.db.applications :refer [get-applications]]
            [rems.guide :refer :all]
            [rems.text :refer [text]]))

(defn localize-state [state]
  (case state
    "draft" :t.applications.states/draft
    "applied" :t.applications.states/applied
    "approved" :t.applications.states/approved
    "rejected" :t.applications.states/rejected
    "returned" :t.applications.states/returned
    "closed" :t.applications.states/closed
    :t.applications.states/unknown))

(def ^:private time-format (format/formatter "yyyy-MM-dd HH:mm"
                                             (time/default-time-zone)))

(defn- applications-item [app]
  [:tr.application
   [:td {:data-th (text :t.applications/application)} (:id app)]
   [:td {:data-th (text :t.applications/resource)} (get-in app [:catalogue-item :title])]
   [:td {:data-th (text :t.applications/state)} (text (localize-state (:state app)))]
   [:td {:data-th (text :t.applications/created)} (format/unparse time-format (:start app))]
   [:td [:a.btn.btn-primary
         {:href (str "/form/" (:catid app) "/" (:id app))}
         (text :t/applications.view)]]])

(defn applications
  ([]
   (applications (get-applications)))
  ([apps]
   (if (empty? apps)
    [:div.applications.alert.alert-success (text :t/applications.empty)]
    [:table.rems-table.applications
     [:tr
      [:th (text :t.applications/application)]
      [:th (text :t.applications/resource)]
      [:th (text :t.applications/state)]
      [:th (text :t.applications/created)]]
     (for [app apps]
       (applications-item app))])))

(defn guide
  []
  (list
   (example "applications empty"
            (applications []))
   (example "applications"
            (applications
             [{:id 1 :catalogue-item {:title "Draft application"} :state "draft" :applicantuserid "alice"}
              {:id 2 :catalogue-item {:title "Applied application"} :state "applied" :applicantuserid "bob"}
              {:id 3 :catalogue-item {:title "Approved application"} :state "approved" :applicantuserid "charlie"}
              {:id 4 :catalogue-item {:title "Rejected application"} :state "rejected" :applicantuserid "david"}
              {:id 5 :catalogue-item {:title "Closed application"} :state "closed" :applicantuserid "ernie"}]))))
