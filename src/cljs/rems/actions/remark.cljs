(ns rems.actions.remark
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button action-form-view comment-field button-wrapper command! comment-public-field]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "remark")

(rf/reg-event-fx
 ::open-form
 (fn [_ _]
   {:dispatch-n [[:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-comment-public action-form-id false]
                 [:rems.actions.components/set-attachments action-form-id []]]}))

(rf/reg-event-fx
 ::send-remark
 (fn [_ [_ {:keys [application-id comment public attachments on-finished]}]]
   (command! :application.command/remark
             {:application-id application-id
              :comment comment
              :public public
              :attachments attachments}
             {:description [text :t.actions/remark]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn remark-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/remark)
                  :on-click #(rf/dispatch [::open-form])}])

(defn remark-view
  [{:keys [application-id disabled on-send]}]
  [action-form-view action-form-id
   (text :t.actions/remark)
   [[button-wrapper {:id action-form-id
                     :text (text :t.actions/remark)
                     :class "btn-primary"
                     :disabled disabled
                     :on-click on-send}]]
   [:div
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-remark)}]
    [comment-public-field {:field-key action-form-id
                           :label (text :t.actions/remark-public)}]
    [action-attachment {:field-key action-form-id
                        :application-id application-id}]]])

(defn remark-form [application-id on-finished]
  (let [attachments @(rf/subscribe [:rems.actions.components/attachments action-form-id])
        comment @(rf/subscribe [:rems.actions.components/comment action-form-id])
        public @(rf/subscribe [:rems.actions.components/comment-public action-form-id])]
    [remark-view {:application-id application-id
                  :disabled (and (empty? comment) (empty? attachments))
                  :on-send #(rf/dispatch [::send-remark {:application-id application-id
                                                         :comment comment
                                                         :public public
                                                         :attachments attachments
                                                         :on-finished on-finished}])}]))
