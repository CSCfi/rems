(ns rems.actions.request-review
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button action-link comment-field action-form-view button-wrapper command! user-selection]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "request-review")

(rf/reg-event-fx
 ::open-form
 (fn
   [_ _]
   {:dispatch-n [[:rems.actions.components/reviewers]
                 [:rems.actions.components/set-users action-form-id nil]
                 [:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-attachments action-form-id []]]}))

(rf/reg-event-fx
 ::send-request-review
 (fn [_ [_ {:keys [application-id reviewers comment attachments on-finished]}]]
   (command! :application.command/request-review
             {:application-id application-id
              :comment comment
              :reviewers (map :userid reviewers)
              :attachments attachments}
             {:description [text :t.actions/request-review]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn request-review-action-link []
  [action-link {:id action-form-id
                :text (text :t.actions/request-review-dropdown-from-user)
                :on-click #(rf/dispatch [::open-form])}])

(defn request-review-view
  [{:keys [application-id disabled on-send]}]
  [action-form-view action-form-id
   (text :t.actions/request-review-from-user)
   [[button-wrapper {:id "request-review-button"
                     :text (text :t.actions/request-review)
                     :class "btn-primary"
                     :on-click on-send
                     :disabled disabled}]]
   [:div
    [user-selection {:field-key action-form-id
                     :subscription [:rems.actions.components/reviewers]}]
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)}]
    [action-attachment {:field-key action-form-id
                        :application-id application-id}]]])

(defn request-review-form [application-id on-finished]
  (let [selected-reviewers @(rf/subscribe [:rems.actions.components/users action-form-id])
        comment @(rf/subscribe [:rems.actions.components/comment action-form-id])
        attachments @(rf/subscribe [:rems.actions.components/attachments action-form-id])]
    [request-review-view {:application-id application-id
                          :disabled (empty? selected-reviewers)
                          :on-send #(rf/dispatch [::send-request-review {:application-id application-id
                                                                         :reviewers selected-reviewers
                                                                         :comment comment
                                                                         :attachments attachments
                                                                         :on-finished on-finished}])}]))
