(ns rems.actions.approve-reject
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper]]
            [rems.atoms :refer [textarea]]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(defn open-form
  [{:keys [db]} _]
  {:db (assoc db ::comment "")})

(rf/reg-event-fx ::open-form open-form)

(rf/reg-sub ::comment (fn [db _] (::comment db)))

(rf/reg-event-db
 ::set-comment
 (fn [db [_ value]] (assoc db ::comment value)))

(defn- send-approve! [{:keys [application-id comment on-success on-error]}]
  (post! "/api/applications/command"
         {:params {:application-id application-id
                   :type :rems.workflow.dynamic/approve
                   :comment comment}
          :handler on-success ; TODO interpret :errors as failure
          :error-handler on-error}))

(defn- send-reject! [{:keys [application-id comment on-success on-error]}]
  (post! "/api/applications/command"
         {:params {:application-id application-id
                   :type :rems.workflow.dynamic/reject
                   :comment comment}
          :handler on-success ; TODO interpret :errors as failure
          :error-handler on-error}))

(rf/reg-event-fx
 ::send-approve
 (fn [{:keys [db]} [_ {:keys [application-id comment on-pending on-success on-error]}]]
   (send-approve! {:application-id application-id
                   :comment comment
                   :on-success on-success
                   :on-error on-error})
   (on-pending)
   {}))

(rf/reg-event-fx
 ::send-reject
 (fn [{:keys [db]} [_ {:keys [application-id comment on-pending on-success on-error]}]]
   (send-reject! {:application-id application-id
                  :comment comment
                  :on-success on-success
                  :on-error on-error})
   (on-pending)
   {}))

(def ^:private action-form-id "approve-reject")

(defn approve-reject-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/approve-reject)
                  :class "btn-primary"
                  :on-click #(rf/dispatch [::open-form])}])

(defn approve-reject-view
  [{:keys [comment on-set-comment on-approve on-reject]}]
  [action-form-view action-form-id
   (text :t.actions/comment)
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
  (let [comment (rf/subscribe [::comment])
        description (text :t.actions/comment)
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
                              :description (text :t.actions/comment)
                              :on-close on-modal-close)])
       [approve-reject-view {:comment @comment
                             :on-set-comment #(rf/dispatch [::set-comment %])
                             :on-approve #(rf/dispatch [::send-approve {:application-id application-id
                                                                        :comment @comment
                                                                        :on-pending on-pending
                                                                        :on-success on-success
                                                                        :on-error on-error}])
                             :on-reject #(rf/dispatch [::send-reject {:application-id application-id
                                                                      :comment @comment
                                                                      :on-pending on-pending
                                                                      :on-success on-success
                                                                      :on-error on-error}])}]])))
