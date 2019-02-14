(ns rems.actions.invite-member
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.actions.action :refer [action-button action-form-view button-wrapper]]
            [rems.atoms :refer [textarea]]
            [rems.autocomplete :as autocomplete]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(defn open-form
  [{:keys [db]} _]
  {:db (assoc db
              ::name ""
              ::email "")})

(rf/reg-event-fx ::open-form open-form)

(rf/reg-event-db
 ::set-name
 (fn [db [_ name]]
   (assoc db ::name name)))

(rf/reg-sub ::name (fn [db _] (::name db)))

(rf/reg-event-db
 ::set-email
 (fn [db [_ email]]
   (assoc db ::email email)))

(rf/reg-sub ::email (fn [db _] (::email db)))

(defn- send-invite-member! [{:keys [member application-id on-success on-error]}]
  (post! "/api/applications/command"
         {:params {:application-id application-id
                   :type :rems.workflow.dynamic/invite-member
                   :member member}
          :handler (fn [response]
                     (if (:success response)
                       (on-success response)
                       (on-error (first (:errors response)))))
          :error-handler on-error}))

(rf/reg-event-fx
 ::send-invite-member
 (fn [{:keys [db]} [_ {:keys [application-id member on-pending on-success on-error]}]]
   (send-invite-member! {:member member
                         :application-id application-id
                         :on-success on-success
                         :on-error on-error})
   (on-pending)
   {}))

(def ^:private action-form-id "invite-member")

(defn invite-member-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/invite-member)
                  :on-click #(rf/dispatch [::open-form])}])

;; TODO refactor to common input-field ?
(defn input-field [{:keys [id label placeholder value type on-change normalizer]}]
  (let [normalizer (or normalizer identity)]
    [:div.form-group.field
     [:label {:for id} label]
     [:input.form-control {:type type
                           :id id
                           :placeholder placeholder
                           :value value
                           :on-change on-change}]]))

(defn invite-member-view
  [{:keys [name email on-invite-member on-remove-member on-send]}]
  [action-form-view action-form-id
   (text :t.actions/invite-member)
   [[button-wrapper {:id "invite-member"
                     :text (text :t.actions/invite-member)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:div
    [input-field {:id "member-name"
                  :label (text :t.actions/member-name)
                  :value name
                  :type :text
                  :on-change #(rf/dispatch [::set-name (.. % -target -value)])}]
    [input-field {:id "member-email"
                  :label (text :t.actions/member-email)
                  :value email
                  :type :text
                  :on-change #(rf/dispatch [::set-email (.. % -target -value)])}]]
   {:collapse-id "member-action-forms"}])

(defn invite-member-form [application-id on-finished]
  (let [name (rf/subscribe [::name])
        email (rf/subscribe [::email])
        description (text :t.actions/invite-member)
        state (r/atom nil)
        on-pending #(reset! state {:status :pending})
        on-success #(reset! state {:status :saved})
        on-error #(reset! state {:status :failed :error %})
        on-modal-close #(do (reset! state nil)
                            (on-finished))]
    (fn [application-id on-finished]
      [:div
       (when (:status @state)
         [status-modal (assoc @state
                              :description (text :t.actions/invite-member)
                              :on-close on-modal-close)])
       [invite-member-view {:name @name
                            :email @email
                            :on-invite-member #(rf/dispatch [::add-selected-member %])
                            :on-remove-member #(rf/dispatch [::remove-selected-member %])
                            :on-send #(rf/dispatch [::send-invite-member {:application-id application-id
                                                                          :member {:name @name :email @email}
                                                                          :on-pending on-pending
                                                                          :on-success on-success
                                                                          :on-error on-error}])}]])))
