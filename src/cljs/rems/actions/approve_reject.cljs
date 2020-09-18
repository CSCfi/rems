(ns rems.actions.approve-reject
  (:require [cljs-time.coerce :as time-coerce]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [re-frame.core :as rf]
            [rems.actions.action :refer [action-attachment action-button action-comment action-form-view button-wrapper command!]]
            [rems.atoms :refer [close-symbol]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "approve-reject")

(defn default-end [length]
  (when length
    (time-format/unparse (time-format/formatters :year-month-day)
                         (time/plus (time/now) (time/days length)))))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db ::comment "" ::entitlement-end (default-end (get-in db [:config :entitlement-default-length-days])))
    :dispatch [:rems.actions.action/set-attachments action-form-id []]}))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(rf/reg-sub ::entitlement-end (fn [db _] (::entitlement-end db)))
(rf/reg-event-db ::set-entitlement-end (fn [db [_ value]] (assoc db ::entitlement-end value)))

(rf/reg-event-fx
 ::send-approve
 (fn [_ [_ {:keys [application-id comment attachments end on-finished]}]]
   (command! :application.command/approve
             (merge {:application-id application-id
                     :comment comment
                     :attachments attachments}
                    (when end
                      ;; selecting an entitlement end of 2008-03-15 means the entitlement ends 2008-03-15T23:59:59
                      {:entitlement-end (-> (time-format/parse (time-format/formatters :year-month-day) end)
                                            (time/plus (time/days 1))
                                            (time/minus (time/seconds 1))
                                            (time-coerce/to-date))}))
             {:description [text :t.actions/approve]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(rf/reg-event-fx
 ::send-reject
 (fn [_ [_ {:keys [application-id comment attachments on-finished]}]]
   (command! :application.command/reject
             {:application-id application-id
              :comment comment
              :attachments attachments}
             {:description [text :t.actions/reject]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn approve-reject-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/approve-reject)
                  :class "btn-primary"
                  :on-click #(rf/dispatch [::open-form])}])

(defn approve-reject-view
  [{:keys [application-id comment on-set-comment end on-set-entitlement-end on-approve on-reject]}]
  [action-form-view action-form-id
   (text :t.actions/approve-reject)
   [[button-wrapper {:id "reject"
                     :text (text :t.actions/reject)
                     :class "btn-danger"
                     :on-click on-reject}]
    [button-wrapper {:id "approve"
                     :text (text :t.actions/approve)
                     :class "btn-success"
                     :on-click on-approve}]]
   [:<>
    [action-comment {:id action-form-id
                     :label (text :t.form/add-comments-shown-to-applicant)
                     :comment comment
                     :on-comment on-set-comment}]
    [action-attachment {:application-id application-id
                        :key action-form-id}]
    [:div.form-group
     [:label {:for "approve-end"} (text :t.actions/approve-end-date)]
     [:div.input-group.w-50
      [:input.form-control {:type "date"
                            :id "approve-end"
                            :name "approve-end"
                            :value end
                            :required false
                            :on-change #(on-set-entitlement-end (.. % -target -value))}]
      (when end
        [:div.input-group-append
         [:button.btn.btn-outline-secondary
          {:on-click #(on-set-entitlement-end nil)
           :aria-label (text :t.actions/clear)}
          [close-symbol]]])]]]])

(defn approve-reject-form [application-id on-finished]
  (let [comment @(rf/subscribe [::comment])
        attachments @(rf/subscribe [:rems.actions.action/attachments action-form-id])
        end @(rf/subscribe [::entitlement-end])]
    [approve-reject-view {:application-id application-id
                          :comment comment
                          :on-set-comment #(rf/dispatch [::set-comment %])
                          :end end
                          :on-set-entitlement-end #(rf/dispatch [::set-entitlement-end %])
                          :on-approve #(rf/dispatch [::send-approve {:application-id application-id
                                                                     :comment comment
                                                                     :attachments attachments
                                                                     :end end
                                                                     :on-finished on-finished}])
                          :on-reject #(rf/dispatch [::send-reject {:application-id application-id
                                                                   :comment comment
                                                                   :attachments attachments
                                                                   :on-finished on-finished}])}]))
