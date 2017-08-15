(ns rems.reviews
  (:require [clj-time.core :as time]
            [clj-time.format :as format]
            [compojure.core :refer [GET POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
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

(defn- review-form-attrs [app]
  {:method "post"
   :action (str "/reviews/" (:id app) "/" (:curround app))})

(defn confirm-modal [name-field action-title app]
  [:div.modal.fade {:id (str name-field "-modal") :tabindex "-1" :role "dialog" :aria-labelledby "confirmModalLabel" :aria-hidden "true"}
   [:div.modal-dialog {:role "document"}
    [:div.modal-content
     [:form (review-form-attrs app)
      (anti-forgery-field)
      [:div.modal-header
       [:h5#confirmModalLabel.modal-title (if (has-roles? :reviewer) (text :t.form/add-comments) (text :t.form/add-comments-applicant))]
       [:button.close {:type "button" :data-dismiss "modal" :aria-label (text :t.reviews/cancel)}
        [:span {:aria-hidden "true"} "&times;"]]]
      [:div.modal-body
       [:div.form-group
        [:textarea.form-control {:name "comment"}]]]
      [:div.modal-footer
       [:button.btn.btn-secondary {:data-dismiss "modal"} (text :t.reviews/cancel)]
       [:button.btn.btn-primary {:type "submit" :name name-field} action-title]]]]]])

(defn back-to-reviews-button []
  [:a.btn.btn-secondary.pull-left {:href "/reviews"} (text :t.form/back-reviews)])

(defn review-button [app]
  (list
   [:button.btn.btn-primary {:type "button" :data-toggle "modal" :data-target "#review-modal"}
    (text :t.reviews/review)]
   (confirm-modal "review" (text :t.reviews/review) app)))

(defn review-form [app]
  [:div.actions
   (when-role :reviewer
     (back-to-reviews-button))
   (review-button app)])

(defn- review-item [app]
  [:tr.review
   [:td {:data-th (text :t.reviews/application)} (:id app)]
   [:td {:data-th (text :t.reviews/resource)} (get-in app [:catalogue-item :title])]
   [:td {:data-th (text :t.reviews/applicant)} (:applicantuserid app)]
   [:td {:data-th (text :t.reviews/created)} (format/unparse time-format (:start app))]
   [:td.actions
    (view-button app)
    (review-button app)]])

(defn reviews
  ([]
   (reviews (applications/get-application-to-review)))
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

(defn guide
  []
  (list
   (example "reviews empty"
             (reviews []))
   (example "reviews"
            (reviews
             [{:id 1 :catalogue-item {:title "AAAAAAAAAAAAAA"} :applicantuserid "alice"}
              {:id 3 :catalogue-item {:title "bbbbbb"} :applicantuserid "bob"}]))
    ))

(defn reviews-page []
  (layout/render
    "reviews"
    (reviews)))

(defroutes reviews-routes
  (GET "/reviews" [] (reviews-page))
  (POST "/reviews/:id/:round" [id round :as request]
        (let [id (Long/parseLong id)
              round (Long/parseLong round)
              input (:form-params request)
              comment-msg (get input "comment")
              comment-msg (when-not (empty? comment-msg) comment-msg)]
          (when-not (get input "review")
            (errorf "Unknown action!"))
          (applications/review-application id round comment-msg)
          (assoc (redirect (if (has-roles? :reviewer) "/reviews" "/applications") :see-other)
                 :flash [{:status :success
                          :contents (text :t.reviews/review-success)}]))
        ))
