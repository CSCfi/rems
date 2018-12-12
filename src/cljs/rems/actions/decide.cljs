(ns rems.actions.decide
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.actions.action :refer [action-form-view button-wrapper]]
            [rems.atoms :refer [textarea]]
            [rems.autocomplete :as autocomplete]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(defn open-form
  [{:keys [db]} _]
  (merge {:db (assoc db ::comment "")}))

(rf/reg-event-fx ::open-form open-form)

(rf/reg-sub ::comment (fn [db _] (::comment db)))

(rf/reg-event-db
 ::set-comment
 (fn [db [_ value]] (assoc db ::comment value)))

(defn- send-decide! [{:keys [application-id comment decision on-success on-error]}]
  (post! "/api/applications/command"
         {:params {:application-id application-id
                   :type :rems.workflow.dynamic/decide
                   :decision decision
                   :comment comment}
          :handler on-success ; TODO interpret :errors as failure
          :error-handler on-error}))

(rf/reg-event-fx
 ::send-decide
 (fn [{:keys [db]} [_ {:keys [application-id comment decision on-pending on-success on-error]}]]
   (send-decide! {:application-id application-id
                  :comment comment
                  :decision decision
                  :on-success on-success
                  :on-error on-error})
   (on-pending)
   {}))

(defn decide-view
  [{:keys [comment on-set-comment on-send]}]
  [action-form-view "decide"
   (text :t.actions/decide)
   nil
   [[button-wrapper {:id "do-decide-reject"
                     :text (text :t.actions/reject)
                     :class "btn-danger"
                     :on-click #(on-send :rejected)}]
    [button-wrapper {:id "do-decide-approve"
                     :text (text :t.actions/approve)
                     :class "btn-success"
                     :on-click #(on-send :approved)}]]
   [:div [:div.form-group
          [:label {:for "comment-decide"} (text :t.form/add-comments-not-shown-to-applicant)]
          [textarea {:id "comment-decide"
                     :name "comment-decide"
                     :placeholder (text :t.form/comment)
                     :value comment
                     :on-change #(on-set-comment (.. % -target -value))}]]]
   nil
   nil])

(defn decide-form [application-id on-finished]
  (let [comment (rf/subscribe [::comment])
        description (text :t.actions/decide)
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
                              :description (text :t.actions/decide)
                              :on-close on-modal-close)])
       [decide-view {:comment @comment
                     :on-set-comment #(rf/dispatch [::set-comment %])
                     :on-send #(rf/dispatch [::send-decide {:application-id application-id
                                                            :comment @comment
                                                            :decision %
                                                            :on-pending on-pending
                                                            :on-success on-success
                                                            :on-error on-error}])}]])))
