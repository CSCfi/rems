(ns rems.actions.close
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper]]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::comment "")}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-event-fx
 ::send-close
 (fn [_ [_ {:keys [application-id comment on-finished]}]]
   (status-modal/common-pending-handler! (text :t.actions/close))
   (post! "/api/applications/close"
          {:params {:application-id application-id
                    :comment comment}
           :handler (partial status-modal/common-success-handler! on-finished)
           :error-handler status-modal/common-error-handler!})
   {}))

(def ^:private action-form-id "close")

(defn close-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/close)
                  :on-click #(rf/dispatch [::open-form])}])

(defn close-view
  [{:keys [comment see-everything? on-set-comment on-send]}]
  [action-form-view action-form-id
   (text :t.actions/close)
   [[button-wrapper {:id "close"
                     :text (text :t.actions/close)
                     :class "btn-danger"
                     :on-click on-send}]]
   (when see-everything?
     [action-comment {:id action-form-id
                      :label (text :t.form/add-comments-not-shown-to-applicant)
                      :comment comment
                      :on-comment on-set-comment}])])

(defn close-form [application-id see-everything? on-finished]
  (let [comment @(rf/subscribe [::comment])]
    [close-view {:comment comment
                 :see-everything? see-everything?
                 :on-set-comment #(rf/dispatch [::set-comment %])
                 :on-send #(rf/dispatch [::send-close {:application-id application-id
                                                       :comment comment
                                                       :decision %
                                                       :on-finished on-finished}])}]))
