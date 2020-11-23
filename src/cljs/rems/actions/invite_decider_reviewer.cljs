(ns rems.actions.invite-decider-reviewer
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-link action-form-view button-wrapper command!
                                             comment-field email-field name-field]]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]))


(def ^:private field-key "invite-decider-reviewer")

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} [_ role]]
   {:db (assoc db ::role role)
    :dispatch-n [[:rems.actions.components/set-name field-key ""]
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
                :on-click #(rf/dispatch [::open-form :decider])}])

(defn invite-reviewer-action-link []
  [action-link {:id reviewer-form-id
                :text (text :t.actions/request-review-dropdown-via-email)
                :on-click #(rf/dispatch [::open-form :reviewer])}])

(defn invite-decider-reviewer-view
  [{:keys [role application-id on-send disabled]}]
  [action-form-view
   (if (= :decider role)
     decider-form-id
     reviewer-form-id)
   (if (= :decider role)
     (text :t.actions/request-decision-via-email)
     (text :t.actions/request-review-via-email))
   [[button-wrapper {:id "invite-decider-reviewer"
                     :text (if (= :decider role)
                             (text :t.actions/request-decision)
                             (text :t.actions/request-review))
                     :class "btn-primary"
                     :on-click on-send
                     :disabled disabled}]]
   [:<>
    [name-field {:field-key field-key}]
    [email-field {:field-key field-key}]
    [comment-field {:field-key field-key
                    :label (text :t.form/add-comments-not-shown-to-applicant)}]
    [action-attachment {:field-key field-key
                        :application-id application-id}]]])

(defn invite-decider-reviewer-form [application-id on-finished]
  (let [role @(rf/subscribe [::role])
        name @(rf/subscribe [:rems.actions.components/name field-key])
        email @(rf/subscribe [:rems.actions.components/email field-key])
        comment @(rf/subscribe [:rems.actions.components/comment field-key])
        attachments @(rf/subscribe [:rems.actions.components/attachments field-key])]
    [invite-decider-reviewer-view {:application-id application-id
                                   :role role
                                   :disabled (empty? email)
                                   :on-send #(rf/dispatch (case role
                                                            :decider [::send-invite-decider {:application-id application-id
                                                                                             :decider {:name name :email email}
                                                                                             :comment comment
                                                                                             :attachments attachments
                                                                                             :on-finished on-finished}]
                                                            :reviewer [::send-invite-reviewer {:application-id application-id
                                                                                               :reviewer {:name name :email email}
                                                                                               :comment comment
                                                                                               :attachments attachments
                                                                                               :on-finished on-finished}]))}]))
