(ns rems.actions.remove-member
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper]]
            [rems.atoms :refer [textarea]]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]
            [clojure.string :as str]))

(defn open-form
  [{:keys [db]} _]
  (merge {:db (assoc db ::comment "")}))

(rf/reg-event-fx ::open-form open-form)

(rf/reg-sub ::comment (fn [db _] (::comment db)))

(rf/reg-event-db
 ::set-comment
 (fn [db [_ value]] (assoc db ::comment value)))

(defn- send-remove-member! [{:keys [application-id member comment on-success on-error]}]
  (post! "/api/applications/command"
         {:params {:application-id application-id
                   :type (if (:userid member)
                           :rems.workflow.dynamic/remove-member
                           :rems.workflow.dynamic/uninvite-member)
                   :member (if (:userid member)
                             (select-keys member [:userid])
                             (select-keys member [:name :email]))
                   :comment comment}
          :handler on-success ; TODO interpret :errors as failure
          :error-handler on-error}))

(rf/reg-event-fx
 ::send-remove-member
 (fn [{:keys [db]} [_ {:keys [application-id member comment on-pending on-success on-error]}]]
   (send-remove-member! {:application-id application-id
                         :comment comment
                         :member member
                         :on-success on-success
                         :on-error on-error})
   (on-pending)
   {}))

(defn remove-member-action-button [member-collapse-id]
  [action-button {:id (str member-collapse-id "-remove-member")
                  :text (text :t.actions/remove-member)
                  :on-click #(rf/dispatch [::open-form])}])


(defn remove-member-view
  [{:keys [member-collapse-id comment on-set-comment on-send]}]
  [action-form-view (str member-collapse-id "-remove-member")
   (text :t.actions/remove-member)
   [[button-wrapper {:id (str member-collapse-id "-remove-member")
                     :text (text :t.actions/remove-member)
                     :class "btn-primary"
                     :on-click on-send}]]
   [action-comment {:id (str member-collapse-id "-remove-member-comment")
                    :label (text :t.form/add-comments-shown-to-applicant)
                    :comment comment
                    :on-comment on-set-comment}]
   {:collapse-id member-collapse-id}])

(defn remove-member-form [application-id member-collapse-id member on-finished]
  (let [comment (rf/subscribe [::comment])
        description (text :t.actions/remove-member)
        state (r/atom nil)
        on-pending #(reset! state {:status :pending})
        on-success #(reset! state {:status :saved})
        on-error #(reset! state {:status :failed :error %})
        on-modal-close #(do (reset! state nil)
                            (on-finished))]
    (fn [application-id]
      [:div
       (when (:status @state)
         [status-modal (assoc @state
                              :description (text :t.actions/remove-member)
                              :on-close on-modal-close)])
       [remove-member-view {:member-collapse-id member-collapse-id
                            :comment @comment
                            :on-set-comment #(rf/dispatch [::set-comment %])
                            :on-send #(rf/dispatch [::send-remove-member {:application-id application-id
                                                                          :comment @comment
                                                                          :member member
                                                                          :on-pending on-pending
                                                                          :on-success on-success
                                                                          :on-error on-error}])}]])))
