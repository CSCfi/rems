(ns rems.actions.decide
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button comment-field action-form-view button-wrapper command!]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "decide")

(rf/reg-event-fx
 ::open-form
 (fn [_ _]
   {:dispatch-n [[:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-attachments action-form-id []]]}))

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
                  :class "btn-primary"
                  :text (text :t.actions/decide)
                  :on-click #(rf/dispatch [::open-form])}])

(defn decide-view
  [{:keys [application-id on-send]}]
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
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)}]
    [action-attachment {:field-key action-form-id
                        :application-id application-id}]]])

(defn decide-form [application-id on-finished]
  (let [comment @(rf/subscribe [:rems.actions.components/comment action-form-id])
        attachments @(rf/subscribe [:rems.actions.components/attachments action-form-id])]
    [decide-view {:application-id application-id
                  :on-send #(rf/dispatch [::send-decide {:application-id application-id
                                                         :comment comment
                                                         :attachments attachments
                                                         :decision %
                                                         :on-finished on-finished}])}]))
