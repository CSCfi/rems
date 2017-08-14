(ns rems.reviews
  (:require [clj-time.core :as time]
            [clj-time.format :as format]
            [compojure.core :refer [GET defroutes]]
            [rems.db.applications :as applications]
            [rems.layout :as layout]
            [rems.text :refer [text]]))

(def ^:private time-format (format/formatter "yyyy-MM-dd HH:mm"
                                             (time/default-time-zone)))

(defn- review-item [rev]
  [:tr.review
   [:td {:data-th (text :t.reviews/application)} (:id rev)]
   [:td {:data-th (text :t.reviews/resource)} (get-in rev [:catalogue-item :title])]
   [:td {:data-th (text :t.reviews/applicant)} (:applicantuserid rev)]
   [:td {:data-th (text :t.reviews/created)} (format/unparse time-format (:start rev))]
   [:td.actions ""]])

(defn reviews
  ([]
   (reviews (applications/get-reviews)))
  ([revs]
   (if (empty? revs)
     [:div.reviews.alert.alert-success (text :t/reviews.empty)]
     [:table.rems-table.reviews
      [:tr
       [:th (text :t.reviews/application)]
       [:th (text :t.reviews/resource)]
       [:th (text :t.reviews/applicant)]
       [:th (text :t.reviews/created)]
       [:th]]
      (for [rev (sort-by :id revs)]
        (review-item rev))])))

(defn reviews-page []
  (layout/render
    "reviews"
    (reviews)))

(defroutes reviews-routes
  (GET "/reviews" [] (reviews-page)))
