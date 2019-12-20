(ns rems.actions.revoke
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper command!]]
            [rems.text :refer [text]]))

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
   (command! :application.command/revoke
             {:application-id application-id :comment comment}
             {:description [text :t.actions/revoke]
              :collapse action-form-id
              :on-finished on-finished})
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
                                                         :on-finished on-finished}])}]))
