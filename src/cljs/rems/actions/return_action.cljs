(ns rems.actions.return-action
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-attachment action-button action-comment action-form-view button-wrapper command!]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "return")

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::comment "")
    :dispatch [:rems.actions.action/set-attachments action-form-id []]}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-event-fx
 ::send-return
 (fn [_ [_ {:keys [application-id attachments comment on-finished]}]]
   (command! :application.command/return
             {:application-id application-id
              :comment comment
              :attachments attachments}
             {:description [text :t.actions/return]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn return-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/return)
                  :on-click #(rf/dispatch [::open-form])}])

(defn return-view
  [{:keys [application-id comment on-set-comment on-send]}]
  [action-form-view action-form-id
   (text :t.actions/return)
   [[button-wrapper {:id "return"
                     :text (text :t.actions/return)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:<>
    [action-comment {:id action-form-id
                     :label (text :t.form/add-comments-shown-to-applicant)
                     :comment comment
                     :on-comment on-set-comment}]
    [action-attachment {:application-id application-id
                        :key action-form-id}]]])

(defn return-form [application-id on-finished]
  (let [comment @(rf/subscribe [::comment])
        attachments @(rf/subscribe [:rems.actions.action/attachments action-form-id])]
    [return-view {:application-id application-id
                  :comment comment
                  :on-set-comment #(rf/dispatch [::set-comment %])
                  :on-send #(rf/dispatch [::send-return {:application-id application-id
                                                         :comment comment
                                                         :attachments attachments
                                                         :on-finished on-finished}])}]))
