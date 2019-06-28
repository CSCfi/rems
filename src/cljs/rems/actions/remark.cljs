(ns rems.actions.remark
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper]]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db
               ::comment ""
               ::public false)}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-sub ::public (fn [db _] (::public db)))
(rf/reg-event-db ::set-public (fn [db [_ value]] (assoc db ::public value)))

(rf/reg-event-fx
 ::send-remark
 (fn [{:keys [db]} [_ {:keys [application-id on-finished]}]]
   (status-modal/common-pending-handler! (text :t.actions/remark))
   (post! "/api/applications/remark"
          {:params {:application-id application-id
                    :comment (::comment db)
                    :public (::public db)}
           :handler (partial status-modal/common-success-handler! on-finished)
           :error-handler status-modal/common-error-handler!})
   {}))

(def ^:private action-form-id "remark")

(defn remark-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/remark)
                  :on-click #(rf/dispatch [::open-form])}])

(defn remark-view
  [{:keys [comment on-set-comment public on-set-public on-send]}]
  [action-form-view action-form-id
   (text :t.actions/remark)
   [[button-wrapper {:id "comment"
                     :text (text :t.actions/remark)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:div [action-comment {:id action-form-id
                          :label (text :t.form/add-remark)
                          :comment comment
                          :on-comment on-set-comment}]
         [:input {:type "checkbox"
                  :id "public"
                  :name "public"
                  :value public
                  ;:checked (contains? selected-keys key)
                  :on-change #(on-set-public (.. % -target -checked))}]
         [:label.form-check-label {:for "public"}
          (text :t.actions/remark-public)]]])


(defn remark-form [application-id on-finished]
  [remark-view {:comment @(rf/subscribe [::comment])
                :on-set-comment #(rf/dispatch [::set-comment %])
                :public @(rf/subscribe [::public])
                :on-set-public #(rf/dispatch [::set-public %])
                :on-send #(rf/dispatch [::send-remark {:application-id application-id
                                                       :on-finished on-finished}])}])
