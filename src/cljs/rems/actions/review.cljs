(ns rems.actions.review
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button comment-field action-form-view button-wrapper command!]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "review")

(rf/reg-event-fx
 ::open-form
 (fn [_ _]
   {:dispatch-n [[:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-attachments action-form-id []]]}))

(rf/reg-event-fx
 ::send-review
 (fn [_ [_ {:keys [application-id comment attachments on-finished]}]]
   (command! :application.command/review
             {:application-id application-id
              :comment comment
              :attachments attachments}
             {:description [text :t.actions/review]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn review-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/review)
                  :on-click #(rf/dispatch [::open-form])}])

(defn review-view
  [{:keys [application-id disabled on-send]}]
  [action-form-view action-form-id
   (text :t.actions/review)
   [[button-wrapper {:id "review-button"
                     :text (text :t.actions/review)
                     :class "btn-primary"
                     :disabled disabled
                     :on-click on-send}]]
   [:<>
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)}]
    [action-attachment {:field-key action-form-id
                        :application-id application-id}]]])

(defn review-form [application-id on-finished]
  (let [comment @(rf/subscribe [:rems.actions.components/comment action-form-id])
        attachments @(rf/subscribe [:rems.actions.components/attachments action-form-id])]
    [review-view {:application-id application-id
                  :disabled (and (empty? comment) (empty? attachments))
                  :on-send #(rf/dispatch [::send-review {:application-id application-id
                                                         :comment comment
                                                         :attachments attachments
                                                         :on-finished on-finished}])}]))
