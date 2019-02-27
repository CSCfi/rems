(ns rems.actions.close
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper]]
            [rems.atoms :refer [textarea]]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(defn open-form
  [{:keys [db]} _]
  {:db (assoc db ::comment "")})

(rf/reg-event-fx ::open-form open-form)

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-event-fx
 ::send-close
 (fn [_ [_ {:keys [application-id comment on-finished]}]]
   (status-modal/common-pending-handler (text :t.actions/close))
   (post! "/api/applications/command"
          {:params {:application-id application-id
                    :type :rems.workflow.dynamic/close
                    :comment comment}
           :handler (partial status-modal/common-success-handler on-finished)
           :error-handler status-modal/common-error-handler})
   {}))

(def ^:private action-form-id "close")

(defn close-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/close)
                  :on-click #(rf/dispatch [::open-form])}])

(defn close-view
  [{:keys [comment on-set-comment on-send]}]
  [action-form-view action-form-id
   (text :t.actions/close)
   [[button-wrapper {:id "close"
                     :text (text :t.actions/close)
                     :class "btn-danger"
                     :on-click on-send}]]
   [action-comment {:id action-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)
                    :comment comment
                    :on-comment on-set-comment}]])

(defn close-form [application-id on-finished]
  (let [comment @(rf/subscribe [::comment])]
    [close-view {:comment comment
                 :on-set-comment #(rf/dispatch [::set-comment %])
                 :on-send #(rf/dispatch [::send-close {:application-id application-id
                                                       :comment comment
                                                       :decision %
                                                       :on-finished on-finished}])}]))
