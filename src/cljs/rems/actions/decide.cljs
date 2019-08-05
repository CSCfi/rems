(ns rems.actions.decide
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::comment "")}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(def ^:private action-form-id "decide")

(rf/reg-event-fx
 ::send-decide
 (fn [_ [_ {:keys [application-id comment decision on-finished]}]]
   (status-modal/common-pending-handler! (text :t.actions/decide))
   (post! "/api/applications/decide"
          {:params {:application-id application-id
                    :decision decision
                    :comment comment}
           :handler (partial status-modal/common-success-handler! (fn [_]
                                                                    (collapse-action-form action-form-id)
                                                                    (on-finished)))
           :error-handler status-modal/common-error-handler!})
   {}))

(defn decide-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/decide)
                  :on-click #(rf/dispatch [::open-form])}])

(defn decide-view
  [{:keys [comment on-set-comment on-send]}]
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
   [action-comment {:id action-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)
                    :comment comment
                    :on-comment on-set-comment}]])

(defn decide-form [application-id on-finished]
  (let [comment @(rf/subscribe [::comment])]
    [decide-view {:comment comment
                  :on-set-comment #(rf/dispatch [::set-comment %])
                  :on-send #(rf/dispatch [::send-decide {:application-id application-id
                                                         :comment comment
                                                         :decision %
                                                         :on-finished on-finished}])}]))
