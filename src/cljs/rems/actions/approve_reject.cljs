(ns rems.actions.approve-reject
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::comment "")}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(def ^:private action-form-id "approve-reject")

(rf/reg-event-fx
 ::send-approve
 (fn [_ [_ {:keys [application-id comment on-finished]}]]
   (let [description (text :t.actions/approve)]
     (post! "/api/applications/approve"
            {:params {:application-id application-id
                      :comment comment}
             :handler (flash-message/default-success-handler
                       :actions
                       description
                       (fn [_]
                         (collapse-action-form action-form-id)
                         (on-finished)))
             :error-handler (flash-message/default-error-handler :actions description)}))
   {}))

(rf/reg-event-fx
 ::send-reject
 (fn [_ [_ {:keys [application-id comment on-finished]}]]
   (let [description (text :t.actions/reject)]
     (post! "/api/applications/reject"
            {:params {:application-id application-id
                      :comment comment}
             :handler (flash-message/default-success-handler
                       :actions
                       description
                       (fn [_]
                         (collapse-action-form action-form-id)
                         (on-finished)))
             :error-handler (flash-message/default-error-handler :actions description)}))
   {}))

(defn approve-reject-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/approve-reject)
                  :class "btn-primary"
                  :on-click #(rf/dispatch [::open-form])}])

(defn approve-reject-view
  [{:keys [comment on-set-comment on-approve on-reject]}]
  [action-form-view action-form-id
   (text :t.actions/approve-reject)
   [[button-wrapper {:id "reject"
                     :text (text :t.actions/reject)
                     :class "btn-danger"
                     :on-click on-reject}]
    [button-wrapper {:id "approve"
                     :text (text :t.actions/approve)
                     :class "btn-success"
                     :on-click on-approve}]]
   [action-comment {:id action-form-id
                    :label (text :t.form/add-comments-shown-to-applicant)
                    :comment comment
                    :on-comment on-set-comment}]])

(defn approve-reject-form [application-id on-finished]
  (let [comment @(rf/subscribe [::comment])]
    [approve-reject-view {:comment comment
                          :on-set-comment #(rf/dispatch [::set-comment %])
                          :on-approve #(rf/dispatch [::send-approve {:application-id application-id
                                                                     :comment comment
                                                                     :on-finished on-finished}])
                          :on-reject #(rf/dispatch [::send-reject {:application-id application-id
                                                                   :comment comment
                                                                   :on-finished on-finished}])}]))
