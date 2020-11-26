(ns rems.actions.invite-member
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-button action-form-view button-wrapper collapse-action-form
                                             email-field name-field]]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))


(def ^:private action-form-id "invite-member")

(rf/reg-event-fx
 ::open-form
 (fn [_ _]
   {:dispatch-n [[:rems.actions.components/set-name action-form-id ""]
                 [:rems.actions.components/set-email action-form-id ""]]}))

(defn- validate-member [{:keys [name email]}]
  (when (or (empty? name)
            (empty? email))
    [{:type :t.actions/name-and-email-required}]))

(rf/reg-event-fx
 ::send-invite-member
 (fn [_ [_ {:keys [member application-id on-finished]}]]
   (let [description [text :t.actions/invite-member]]
     (if-let [errors (validate-member member)]
       (flash-message/show-error! :invite-member-errors (flash-message/format-errors errors))
       (post! "/api/applications/invite-member"
              {:params {:application-id application-id
                        :member member}
               :handler (flash-message/default-success-handler
                         :change-members
                         description
                         (fn [_]
                           (collapse-action-form action-form-id)
                           (on-finished)))
               :error-handler (flash-message/default-error-handler :invite-member-errors description)})))
   {}))

(defn invite-member-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/invite-member)
                  :on-click #(rf/dispatch [::open-form])}])

(defn invite-member-view
  [{:keys [on-send]}]
  [action-form-view action-form-id
   (text :t.actions/invite-member)
   [[button-wrapper {:id "invite-member"
                     :text (text :t.actions/invite-member)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:div
    [flash-message/component :invite-member-errors]
    [name-field {:field-key action-form-id}]
    [email-field {:field-key action-form-id}]]
   {:collapse-id "member-action-forms"}])

(defn invite-member-form [application-id on-finished]
  (let [name @(rf/subscribe [:rems.actions.components/name action-form-id])
        email @(rf/subscribe [:rems.actions.components/email action-form-id])]
    [invite-member-view {:on-send #(rf/dispatch [::send-invite-member {:application-id application-id
                                                                       :member {:name name :email email}
                                                                       :on-finished on-finished}])}]))
