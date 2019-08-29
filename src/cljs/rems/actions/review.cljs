(ns rems.actions.review
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::comment "")}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(def ^:private action-form-id "review")

(rf/reg-event-fx
 ::send-review
 (fn [_ [_ {:keys [application-id comment on-finished]}]]
   (let [description (text :t.actions/review)]
     (post! "/api/applications/comment"
            {:params {:application-id application-id
                      :comment comment}
             :handler (flash-message/default-success-handler
                       description
                       (fn [_]
                         (collapse-action-form action-form-id)
                         (on-finished)))
             :error-handler (flash-message/default-error-handler description)}))
   {}))

(defn review-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/review)
                  :on-click #(rf/dispatch [::open-form])}])

(defn review-view
  [{:keys [comment on-set-comment on-send]}]
  [action-form-view action-form-id
   (text :t.actions/review)
   [[button-wrapper {:id "review-button"
                     :text (text :t.actions/review)
                     :class "btn-primary"
                     :on-click on-send}]]
   [action-comment {:id action-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)
                    :comment comment
                    :on-comment on-set-comment}]])

(defn review-form [application-id on-finished]
  (let [comment @(rf/subscribe [::comment])]
    [review-view {:comment comment
                  :on-set-comment #(rf/dispatch [::set-comment %])
                  :on-send #(rf/dispatch [::send-review {:application-id application-id
                                                         :comment comment
                                                         :on-finished on-finished}])}]))
