(ns rems.actions.revoke
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

(def ^:private action-form-id "revoke")

(rf/reg-event-fx
 ::send-revoke
 (fn [_ [_ {:keys [application-id comment on-finished]}]]
   (let [description [text :t.actions/revoke]]
     (post! "/api/applications/revoke"
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

(defn revoke-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/revoke)
                  :on-click #(rf/dispatch [::open-form])}])

(defn revoke-view
  [{:keys [comment on-set-comment on-send]}]
  [action-form-view action-form-id
   (text :t.actions/revoke)
   [[button-wrapper {:id "revoke"
                     :text (text :t.actions/revoke)
                     :class "btn-danger"
                     :on-click on-send}]]
   [action-comment {:id action-form-id
                    :label (text :t.form/add-comments-shown-to-applicant)
                    :comment comment
                    :on-comment on-set-comment}]])

(defn revoke-form [application-id on-finished]
  (let [comment @(rf/subscribe [::comment])]
    [revoke-view {:comment comment
                  :on-set-comment #(rf/dispatch [::set-comment %])
                  :on-send #(rf/dispatch [::send-revoke {:application-id application-id
                                                         :comment comment
                                                         :decision %
                                                         :on-finished on-finished}])}]))
