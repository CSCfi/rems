(ns rems.actions.invite-decider-reviewer
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-button action-form-view button-wrapper command!
                                             email-field name-field]]
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

(defn- validate-member [{:keys [name email]}] ; TODO rename
  (when (or (empty? name)
            (empty? email))
    [{:type :t.actions/name-and-email-required}]))

(def decider-form-id "invite-decider")
(def reviewer-form-id "invite-reviewer")

(rf/reg-event-fx
 ::send-invite-decider
 (fn [_ [_ {:keys [decider comment attachments application-id on-finished]}]]
   (let [description [text :t.actions/invite-decider]]
     (if-let [errors (validate-member decider)]
       (flash-message/show-error! :actions (flash-message/format-errors errors))
       (command! :application.command/invite-decider
                 {:application-id application-id
                  ;;:comment comment ; TODO
                  ;;:attachments attachments ; TODO
                  :decider decider}
                 {:description description
                  :collapse decider-form-id
                  :on-finished on-finished})))
   {}))

(rf/reg-event-fx
 ::send-invite-reviewer
 (fn [_ [_ {:keys [reviewer comment attachments application-id on-finished]}]]
   (let [description [text :t.actions/invite-reviewer]]
     (if-let [errors (validate-member reviewer)]
       (flash-message/show-error! :actions (flash-message/format-errors errors))
       (command! :application.command/invite-reviewer
                 {:application-id application-id
                  ;;:comment comment ; TODO
                  ;;:attachments attachments ; TODO
                  :reviewer reviewer}
                 {:description description
                  :collapse reviewer-form-id
                  :on-finished on-finished})))
   {}))

(defn invite-decider-action-button []
  [action-button {:id decider-form-id
                  :text (text :t.actions/invite-decider)
                  :on-click #(rf/dispatch [::open-form :decider])}])

(defn invite-reviewer-action-button []
  [action-button {:id reviewer-form-id
                  :text (text :t.actions/invite-reviewer)
                  :on-click #(rf/dispatch [::open-form :reviewer])}])

(defn invite-decider-reviewer-view
  [{:keys [role on-send]}]
  [action-form-view
   (case role
     :decider decider-form-id
     :reviewer reviewer-form-id)
   (case role
     :decider (text :t.actions/invite-decider)
     :reviewer (text :t.actions/invite-reviewer))
   [[button-wrapper {:id "invite-decider-reviewer"
                     :text (case role
                             :decider (text :t.actions/invite-decider)
                             :reviewer (text :t.actions/invite-reviewer))
                     :class "btn-primary"
                     :on-click on-send}]]
   [:<>
    [name-field {:field-key field-key}]
    [email-field {:field-key field-key}]]]) ; TODO comment, attachments

(defn invite-decider-reviewer-form [application-id on-finished]
  (let [role @(rf/subscribe [::role])
        name @(rf/subscribe [:rems.actions.components/name field-key])
        email @(rf/subscribe [:rems.actions.components/email field-key])]
    (when role
      [invite-decider-reviewer-view {:role role
                                     :on-send #(rf/dispatch (case role
                                                              :decider [::send-invite-decider {:application-id application-id
                                                                                               :decider {:name name :email email}
                                                                                               :on-finished on-finished}]
                                                              :reviewer [::send-invite-reviewer {:application-id application-id
                                                                                                 :reviewer {:name name :email email}
                                                                                                 :on-finished on-finished}]))}])))
