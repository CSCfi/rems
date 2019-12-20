(ns rems.actions.close
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper command!]]
            [rems.text :refer [text]]))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::comment "")}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(def ^:private action-form-id "close")

(rf/reg-event-fx
 ::send-close
 (fn [_ [_ {:keys [application-id comment on-finished]}]]
   (command! :application.command/close
             {:application-id application-id :comment comment}
             {:description [text :t.actions/close]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn close-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/close)
                  :on-click #(rf/dispatch [::open-form])}])

(defn close-view
  [{:keys [comment show-comment-field? on-set-comment on-send]}]
  [action-form-view action-form-id
   (text :t.actions/close)
   [[button-wrapper {:id "close"
                     :text (text :t.actions/close)
                     :class "btn-danger"
                     :on-click on-send}]]
   [:div
    (text :t.actions/close-intro)
    (when show-comment-field?
      [action-comment {:id action-form-id
                       :label (text :t.form/add-comments-not-shown-to-applicant)
                       :comment comment
                       :on-comment on-set-comment}])]])

(defn close-form [application-id show-comment-field? on-finished]
  (let [comment @(rf/subscribe [::comment])]
    [close-view {:comment comment
                 :show-comment-field? show-comment-field?
                 :on-set-comment #(rf/dispatch [::set-comment %])
                 :on-send #(rf/dispatch [::send-close {:application-id application-id
                                                       :comment comment
                                                       :on-finished on-finished}])}]))
