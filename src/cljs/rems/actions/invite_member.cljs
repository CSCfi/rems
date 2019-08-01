(ns rems.actions.invite-member
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view button-wrapper collapse-action-form]]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-db
 ::open-form
 (fn [db _]
   (assoc db
          ::name ""
          ::email "")))

(rf/reg-event-db ::set-name (fn [db [_ name]] (assoc db ::name name)))
(rf/reg-sub ::name (fn [db _] (::name db)))

(rf/reg-event-db ::set-email (fn [db [_ email]] (assoc db ::email email)))
(rf/reg-sub ::email (fn [db _] (::email db)))

(def ^:private action-form-id "invite-member")

(rf/reg-event-fx
 ::send-invite-member
 (fn [_ [_ {:keys [member application-id on-finished]}]]
   (status-modal/common-pending-handler! (text :t.actions/invite-member))
   (post! "/api/applications/invite-member"
          {:params {:application-id application-id
                    :member member}
           :handler (partial status-modal/common-success-handler! (fn [_]
                                                                    (collapse-action-form action-form-id)
                                                                    (on-finished)))
           :error-handler status-modal/common-error-handler!})
   {}))

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
  [{:keys [name email on-send]}]
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
  (let [name @(rf/subscribe [::name])
        email @(rf/subscribe [::email])]
    [invite-member-view {:name name
                         :email email
                         :on-send #(rf/dispatch [::send-invite-member {:application-id application-id
                                                                       :member {:name name :email email}
                                                                       :on-finished on-finished}])}]))
