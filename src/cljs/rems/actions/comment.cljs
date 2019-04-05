(ns rems.actions.comment
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper]]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::comment "")}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-event-fx
 ::send-comment
 (fn [_ [_ {:keys [application-id comment on-finished]}]]
   (status-modal/common-pending-handler! (text :t.actions/comment))
   (post! "/api/applications/comment"
          {:params {:application-id application-id
                    :comment comment}
           :handler (partial status-modal/common-success-handler! on-finished)
           :error-handler status-modal/common-error-handler!})
   {}))

(def ^:private action-form-id "comment")

(defn comment-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/comment)
                  :on-click #(rf/dispatch [::open-form])}])

(defn comment-view
  [{:keys [comment on-set-comment on-send]}]
  [action-form-view action-form-id
   (text :t.actions/comment)
   [[button-wrapper {:id "comment"
                     :text (text :t.actions/comment)
                     :class "btn-primary"
                     :on-click on-send}]]
   [action-comment {:id action-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)
                    :comment comment
                    :on-comment on-set-comment}]])

(defn comment-form [application-id on-finished]
  (let [comment @(rf/subscribe [::comment])]
    [comment-view {:comment comment
                   :on-set-comment #(rf/dispatch [::set-comment %])
                   :on-send #(rf/dispatch [::send-comment {:application-id application-id
                                                           :comment comment
                                                           :on-finished on-finished}])}]))
