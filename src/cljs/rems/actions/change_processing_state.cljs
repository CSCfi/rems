(ns rems.actions.change-processing-state
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button action-form-view command! comment-field event-public-field perform-action-button]]
            [rems.dropdown :as dropdown]
            [rems.text :refer [localized text]]))

(def ^:private action-form-id "change-processing-state")

(rf/reg-sub ::processing-state (fn [db] (::processing-state db)))
(rf/reg-event-db ::set-processing-state (fn [db [_ value]] (assoc db ::processing-state value)))

(rf/reg-event-fx
 ::open-form
 (fn [_ _]
   {:dispatch-n [[:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-event-public action-form-id false]
                 [:rems.actions.components/set-attachments action-form-id []]
                 [::set-processing-state nil]]}))

(rf/reg-sub
 ::command
 :<- [:rems.actions.components/attachments-with-filenames action-form-id]
 :<- [:rems.actions.components/comment action-form-id]
 :<- [:rems.actions.components/event-public action-form-id]
 :<- [::processing-state]
 (fn [[form-attachments
       form-comment
       form-event-public
       form-processing-state]
      [_ application-id]]
   (when (and application-id
              form-processing-state)
     {:application-id application-id
      :attachments (for [att form-attachments]
                     (select-keys att [:attachment/id]))
      :comment form-comment
      :processing-state form-processing-state
      :public form-event-public})))

(rf/reg-event-fx
 ::send-command
 (fn [_ [_ cmd on-finished]]
   (command! :application.command/change-processing-state
             cmd
             {:description [text :t.actions/change-processing-state]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn processing-state-field [{:keys [field-key label processing-states]}]
  (let [id (str field-key "-dropdown")
        current-value @(rf/subscribe [::processing-state])]
    [:div.processing-state.mb-3
     [:label.sr-only {:for id} label]
     [dropdown/dropdown
      {:id id
       :items (->> processing-states
                   (mapv #(assoc % ::label (localized (:processing-state/title %)))))
       :item-label ::label
       :item-selected? #(= current-value (:processing-state/value %))
       :on-change #(rf/dispatch [::set-processing-state (:processing-state/value %)])}]]))

(defn change-processing-state-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/change-processing-state)
                  :on-click #(rf/dispatch [::open-form])}])

(defn- change-processing-state-view [{:keys [application-id on-submit workflow]}]
  [action-form-view action-form-id
   (text :t.actions/change-processing-state)
   [[perform-action-button {:id action-form-id
                            :text (text :t.actions/change-processing-state)
                            :class "btn-primary"
                            :disabled (not on-submit)
                            :on-click on-submit}]]
   [:<>
    [processing-state-field {:field-key action-form-id
                             :label (text :t.actions/change-processing-state)
                             :processing-states (:workflow/processing-states workflow)}]
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-comment)}]
    [action-attachment {:field-key action-form-id
                        :application-id application-id}]
    [event-public-field {:field-key action-form-id}]]])

(defn change-processing-state-form [application-id application on-finished]
  (let [cmd @(rf/subscribe [::command application-id])]
    [change-processing-state-view
     {:application-id application-id
      :on-submit (when cmd
                   #(rf/dispatch [::send-command cmd on-finished]))
      :workflow (:application/workflow application)}]))
