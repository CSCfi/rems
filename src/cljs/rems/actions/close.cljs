(ns rems.actions.close
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button comment-field action-form-view button-wrapper command!]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "close")

(rf/reg-event-fx
 ::open-form
 (fn [_ _]
   {:dispatch-n [[:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-attachments action-form-id []]]}))

(rf/reg-event-fx
 ::send-close
 (fn [_ [_ {:keys [application-id attachments comment on-finished]}]]
   (command! :application.command/close
             {:application-id application-id
              :comment comment
              :attachments attachments}
             {:description [text :t.actions/close]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn close-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/close)
                  :on-click #(rf/dispatch [::open-form])}])

(defn close-view
  [{:keys [application-id show-comment-field? on-send]}]
  [action-form-view action-form-id
   (text :t.actions/close)
   [[button-wrapper {:id "close"
                     :text (text :t.actions/close)
                     :class "btn-danger"
                     :on-click on-send}]]
   [:div
    (text :t.actions/close-intro)
    (when show-comment-field?
      [:<>
       [comment-field {:field-key action-form-id
                       :label (text :t.form/add-comments-shown-to-applicant)}]
       [action-attachment {:field-key action-form-id
                           :application-id application-id}]])]])

(defn close-form [application-id show-comment-field? on-finished]
  (let [comment @(rf/subscribe [:rems.actions.components/comment action-form-id])
        attachments @(rf/subscribe [:rems.actions.components/attachments action-form-id])]
    [close-view {:application-id application-id
                 :show-comment-field? show-comment-field?
                 :on-send #(rf/dispatch [::send-close {:application-id application-id
                                                       :comment comment
                                                       :attachments attachments
                                                       :on-finished on-finished}])}]))
