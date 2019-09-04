(ns rems.actions.invite-member
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view button-wrapper collapse-action-form]]
            [rems.atoms :as atoms]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-db
 ::open-form
 (fn [db _]
   (assoc db
          ::done? false
          ::name ""
          ::email "")))

(rf/reg-event-db ::set-name (fn [db [_ name]] (assoc db ::name name)))
(rf/reg-sub ::name (fn [db _] (::name db)))

(rf/reg-event-db ::set-email (fn [db [_ email]] (assoc db ::email email)))
(rf/reg-sub ::email (fn [db _] (::email db)))

(rf/reg-event-db ::set-done? (fn [db [_ done]] (assoc db ::done? done)))
(rf/reg-sub ::done? (fn [db _] (::done? db)))

(def ^:private action-form-id "invite-member")

(defn- validate-member [{:keys [name email]}]
  (when (or (empty? name)
            (empty? email))
    [{:type :t.actions/name-and-email-required}]))

(rf/reg-event-fx
 ::send-invite-member
 (fn [_ [_ {:keys [member application-id on-finished]}]]
   (if-let [errors (validate-member member)]
     (status-modal/set-error! {:result {:errors errors}})
     (post! "/api/applications/invite-member"
            {:params {:application-id application-id
                      :member member}
             :handler (fn [_]
                        (collapse-action-form action-form-id)
                        (rf/dispatch [::set-done? true])
                        (on-finished))
             :error-handler status-modal/common-error-handler!}))
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
  [{:keys [name email done? on-send]}]
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

(defn invite-member-status []
  (when @(rf/subscribe [::done?])
    [atoms/flash-message {:status :success
                          :contents (text :t.actions/member-invited)}]))

(defn invite-member-form [application-id on-finished]
  (let [name @(rf/subscribe [::name])
        email @(rf/subscribe [::email])
        done? @(rf/subscribe [::done?])]
    [invite-member-view {:name name
                         :email email
                         :on-send #(rf/dispatch [::send-invite-member {:application-id application-id
                                                                       :member {:name name :email email}
                                                                       :on-finished on-finished}])}]))
