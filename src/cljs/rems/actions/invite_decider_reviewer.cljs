(ns rems.actions.invite-decider-reviewer
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-link action-form-view button-wrapper command!
                                             comment-field email-field name-field]]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]))


(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} [_ field-key]]
   {:dispatch-n [[:rems.actions.components/set-name field-key ""]
                 [:rems.actions.components/set-email field-key ""]
                 [:rems.actions.components/set-comment field-key ""]
                 [:rems.actions.components/set-attachments field-key []]]}))

(rf/reg-sub ::role (fn [db _] (get db ::role)))

(defn- validate-invitation [{:keys [name email]}]
  (when (or (empty? name)
            (empty? email))
    [{:type :t.actions/name-and-email-required}]))

(def decider-form-id "invite-decider")
(def reviewer-form-id "invite-reviewer")

(rf/reg-event-fx
 ::send-invite-decider
 (fn [_ [_ {:keys [decider comment attachments application-id on-finished]}]]
   (let [description [text :t.actions/request-decision]]
     (if-let [errors (validate-invitation decider)]
       (flash-message/show-error! :actions (flash-message/format-errors errors))
       (command! :application.command/invite-decider
                 {:application-id application-id
                  :comment comment
                  :attachments attachments
                  :decider decider}
                 {:description description
                  :collapse decider-form-id
                  :on-finished on-finished})))
   {}))

(rf/reg-event-fx
 ::send-invite-reviewer
 (fn [_ [_ {:keys [reviewer comment attachments application-id on-finished]}]]
   (let [description [text :t.actions/request-review]]
     (if-let [errors (validate-invitation reviewer)]
       (flash-message/show-error! :actions (flash-message/format-errors errors))
       (command! :application.command/invite-reviewer
                 {:application-id application-id
                  :comment comment
                  :attachments attachments
                  :reviewer reviewer}
                 {:description description
                  :collapse reviewer-form-id
                  :on-finished on-finished})))
   {}))

(defn invite-decider-action-link []
  [action-link {:id decider-form-id
                :text (text :t.actions/request-decision-dropdown-via-email)
                :on-click #(rf/dispatch [::open-form decider-form-id])}])

(defn invite-reviewer-action-link []
  [action-link {:id reviewer-form-id
                :text (text :t.actions/request-review-dropdown-via-email)
                :on-click #(rf/dispatch [::open-form reviewer-form-id])}])

(defn invite-decider-view
  [{:keys [application-id on-send disabled]}]
  [action-form-view
   decider-form-id
   (text :t.actions/request-decision-via-email)
   [[button-wrapper {:id "invite-decider"
                     :text (text :t.actions/request-decision)
                     :class "btn-primary"
                     :on-click on-send
                     :disabled disabled}]]
   [:<>
    [name-field {:field-key decider-form-id}]
    [email-field {:field-key decider-form-id}]
    [comment-field {:field-key decider-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)}]
    [action-attachment {:field-key decider-form-id
                        :application-id application-id}]]])

(defn invite-reviewer-view
  [{:keys [application-id on-send disabled]}]
  [action-form-view
   reviewer-form-id
   (text :t.actions/request-review-via-email)
   [[button-wrapper {:id "invite-reviewer"
                     :text (text :t.actions/request-review)
                     :class "btn-primary"
                     :on-click on-send
                     :disabled disabled}]]
   [:<>
    [name-field {:field-key reviewer-form-id}]
    [email-field {:field-key reviewer-form-id}]
    [comment-field {:field-key reviewer-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)}]
    [action-attachment {:field-key reviewer-form-id
                        :application-id application-id}]]])

(defn invite-decider-form [application-id on-finished]
  (let [name @(rf/subscribe [:rems.actions.components/name decider-form-id])
        email @(rf/subscribe [:rems.actions.components/email decider-form-id])
        comment @(rf/subscribe [:rems.actions.components/comment decider-form-id])
        attachments @(rf/subscribe [:rems.actions.components/attachments decider-form-id])]
    [invite-decider-view {:application-id application-id
                          :disabled (empty? email)
                          :on-send #(rf/dispatch [::send-invite-decider {:application-id application-id
                                                                         :decider {:name name :email email}
                                                                         :comment comment
                                                                         :attachments attachments
                                                                         :on-finished on-finished}])}]))

(defn invite-reviewer-form [application-id on-finished]
  (let [name @(rf/subscribe [:rems.actions.components/name reviewer-form-id])
        email @(rf/subscribe [:rems.actions.components/email reviewer-form-id])
        comment @(rf/subscribe [:rems.actions.components/comment reviewer-form-id])
        attachments @(rf/subscribe [:rems.actions.components/attachments reviewer-form-id])]
    [invite-reviewer-view {:application-id application-id
                           :disabled (empty? email)
                           :on-send #(rf/dispatch [::send-invite-reviewer {:application-id application-id
                                                                           :reviewer {:name name :email email}
                                                                           :comment comment
                                                                           :attachments attachments
                                                                           :on-finished on-finished}])}]))
