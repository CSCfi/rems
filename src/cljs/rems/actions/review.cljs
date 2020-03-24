(ns rems.actions.review
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-attachment action-button action-comment action-form-view button-wrapper command!]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "review")

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::comment "")
    :dispatch [:rems.actions.action/set-attachments action-form-id []]}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-event-fx
 ::send-review
 (fn [_ [_ {:keys [application-id comment attachments on-finished]}]]
   (command! :application.command/review
             {:application-id application-id
              :comment comment
              :attachments attachments}
             {:description [text :t.actions/review]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn review-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/review)
                  :on-click #(rf/dispatch [::open-form])}])

(defn review-view
  [{:keys [application-id comment on-set-comment on-send]}]
  [action-form-view action-form-id
   (text :t.actions/review)
   [[button-wrapper {:id "review-button"
                     :text (text :t.actions/review)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:<>
    [action-comment {:id action-form-id
                     :label (text :t.form/add-comments-not-shown-to-applicant)
                     :comment comment
                     :on-comment on-set-comment}]
    [action-attachment {:key action-form-id
                        :application-id application-id}]]])

(defn review-form [application-id on-finished]
  (let [comment @(rf/subscribe [::comment])
        attachments @(rf/subscribe [:rems.actions.action/attachments action-form-id])]
    [review-view {:application-id application-id
                  :comment comment
                  :on-set-comment #(rf/dispatch [::set-comment %])
                  :on-send #(rf/dispatch [::send-review {:application-id application-id
                                                         :comment comment
                                                         :attachments attachments
                                                         :on-finished on-finished}])}]))
