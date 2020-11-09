(ns rems.actions.return-action
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button comment-field action-form-view button-wrapper command!]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "return")

(rf/reg-event-fx
 ::open-form
 (fn [_ _]
   {:dispatch-n [[:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-attachments action-form-id []]]}))

(rf/reg-event-fx
 ::send-return
 (fn [_ [_ {:keys [application-id attachments comment on-finished]}]]
   (command! :application.command/return
             {:application-id application-id
              :comment comment
              :attachments attachments}
             {:description [text :t.actions/return]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn return-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/return)
                  :on-click #(rf/dispatch [::open-form])}])

(defn return-view
  [{:keys [application-id on-send]}]
  [action-form-view action-form-id
   (text :t.actions/return)
   [[button-wrapper {:id "return"
                     :text (text :t.actions/return)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:<>
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-comments-shown-to-applicant)}]
    [action-attachment {:application-id application-id
                        :field-key action-form-id}]]])

(defn return-form [application-id on-finished]
  (let [comment @(rf/subscribe [:rems.actions.components/comment action-form-id])
        attachments @(rf/subscribe [:rems.actions.components/attachments action-form-id])]
    [return-view {:application-id application-id
                  :on-send #(rf/dispatch [::send-return {:application-id application-id
                                                         :comment comment
                                                         :attachments attachments
                                                         :on-finished on-finished}])}]))
