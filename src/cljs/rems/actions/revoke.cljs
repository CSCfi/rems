(ns rems.actions.revoke
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button comment-field action-form-view button-wrapper command!]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "revoke")

(rf/reg-event-fx
 ::open-form
 (fn [_ _]
   {:dispatch-n [[:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-attachments action-form-id []]]}))

(rf/reg-event-fx
 ::send-revoke
 (fn [_ [_ {:keys [application-id comment attachments on-finished]}]]
   (command! :application.command/revoke
             {:application-id application-id
              :comment comment
              :attachments attachments}
             {:description [text :t.actions/revoke]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn revoke-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/revoke)
                  :on-click #(rf/dispatch [::open-form])}])

(defn revoke-view
  [{:keys [application-id on-send]}]
  [action-form-view action-form-id
   (text :t.actions/revoke)
   [[button-wrapper {:id "revoke"
                     :text (text :t.actions/revoke)
                     :class "btn-danger"
                     :on-click on-send}]]
   [:<>
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-comments-shown-to-applicant)}]
    [action-attachment {:application-id application-id
                        :field-key action-form-id}]]])

(defn revoke-form [application-id on-finished]
  (let [comment @(rf/subscribe [:rems.actions.components/comment action-form-id])
        attachments @(rf/subscribe [:rems.actions.components/attachments action-form-id])]
    [revoke-view {:application-id application-id
                  :on-send #(rf/dispatch [::send-revoke {:application-id application-id
                                                         :comment comment
                                                         :attachments attachments
                                                         :on-finished on-finished}])}]))
