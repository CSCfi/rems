(ns rems.actions.decide
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-attachment action-button action-comment action-form-view button-wrapper command!]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "decide")

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::comment "")
    :dispatch [:rems.actions.action/set-attachments action-form-id []]}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-event-fx
 ::send-decide
 (fn [_ [_ {:keys [application-id comment attachments decision on-finished]}]]
   (command! :application.command/decide
             {:application-id application-id
              :decision decision
              :comment comment
              :attachments attachments}
             {:description [text :t.actions/decide]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn decide-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/decide)
                  :on-click #(rf/dispatch [::open-form])}])

(defn decide-view
  [{:keys [application-id comment on-set-comment on-send]}]
  [action-form-view action-form-id
   (text :t.actions/decide)
   [[button-wrapper {:id "decide-reject"
                     :text (text :t.actions/reject)
                     :class "btn-danger"
                     :on-click #(on-send :rejected)}]
    [button-wrapper {:id "decide-approve"
                     :text (text :t.actions/approve)
                     :class "btn-success"
                     :on-click #(on-send :approved)}]]
   [:<>
    [action-comment {:id action-form-id
                     :label (text :t.form/add-comments-not-shown-to-applicant)
                     :comment comment
                     :on-comment on-set-comment}]
    [action-attachment {:application-id application-id
                        :key action-form-id}]]])

(defn decide-form [application-id on-finished]
  (let [comment @(rf/subscribe [::comment])
        attachments @(rf/subscribe [:rems.actions.action/attachments action-form-id])]
    [decide-view {:application-id application-id
                  :comment comment
                  :on-set-comment #(rf/dispatch [::set-comment %])
                  :on-send #(rf/dispatch [::send-decide {:application-id application-id
                                                         :comment comment
                                                         :attachments attachments
                                                         :decision %
                                                         :on-finished on-finished}])}]))
