(ns rems.actions.request-decision
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-link comment-field action-form-view button-wrapper command! user-selection]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "request-decision")

(rf/reg-event-fx
 ::open-form
 (fn [_ _]
   {:dispatch-n [[:rems.actions.components/deciders]
                 [:rems.actions.components/set-users action-form-id nil]
                 [:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-attachments action-form-id []]]}))

(rf/reg-event-fx
 ::send-request-decision
 (fn [_ [_ {:keys [deciders application-id comment attachments on-finished]}]]
   (command! :application.command/request-decision
             {:application-id application-id
              :comment comment
              :attachments attachments
              :deciders (map :userid deciders)}
             {:description [text :t.actions/request-decision]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn request-decision-action-link []
  [action-link {:id action-form-id
                :text (text :t.actions/request-decision-dropdown-from-user)
                :on-click #(rf/dispatch [::open-form])}])

(defn request-decision-view
  [{:keys [application-id disabled on-send]}]
  [action-form-view action-form-id
   (text :t.actions/request-decision-from-user)
   [[button-wrapper {:id "request-decision"
                     :text (text :t.actions/request-decision)
                     :class "btn-primary"
                     :on-click on-send
                     :disabled disabled}]]
   [:div
    [user-selection {:field-key action-form-id
                     :subscription [:rems.actions.components/deciders]}]
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)}]
    [action-attachment {:field-key action-form-id
                        :application-id application-id}]]])

(defn request-decision-form [application-id on-finished]
  (let [selected-deciders @(rf/subscribe [:rems.actions.components/users action-form-id])
        comment @(rf/subscribe [:rems.actions.components/comment action-form-id])
        attachments @(rf/subscribe [:rems.actions.components/attachments action-form-id])]
    [request-decision-view {:application-id application-id
                            :disabled (empty? selected-deciders)
                            :on-send #(rf/dispatch [::send-request-decision {:application-id application-id
                                                                             :deciders selected-deciders
                                                                             :comment comment
                                                                             :attachments attachments
                                                                             :on-finished on-finished}])}]))
