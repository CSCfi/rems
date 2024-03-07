(ns rems.actions.redact-attachments
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button action-form-view command! select-attachments-field comment-field event-public-field perform-action-button]]
            [rems.text :refer [text]]))

(def ^:private action-form-id "redact-attachments")

(rf/reg-sub
 ::attachments
 (fn [db _] (::attachments db)))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} [_ attachments]]
   {:db (assoc db ::attachments attachments)
    :dispatch-n [[:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-event-public action-form-id false]
                 [:rems.actions.components/set-attachments action-form-id []]
                 [:rems.actions.components/set-selected-attachments action-form-id {}]]}))

(rf/reg-event-fx
 ::send-redact-attachments
 (fn [_ [_ cmd {:keys [on-finished]}]]
   (command! :application.command/redact-attachments
             cmd
             {:description [text :t.actions/redact-attachments]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(rf/reg-sub
 ::form-fields
 :<- [:rems.actions.components/selected-attachments action-form-id]
 :<- [:rems.actions.components/attachments-with-filenames action-form-id]
 :<- [:rems.actions.components/comment action-form-id]
 :<- [:rems.actions.components/event-public action-form-id]
 (fn [[select-attachments attachments-with-filenames comment event-public] _]
   {:redact-attachments select-attachments
    :attachments attachments-with-filenames
    :comment comment
    :public event-public}))

(defn redact-attachments-action-button [attachments]
  [action-button {:id action-form-id
                  :text (text :t.actions/redact-attachments)
                  :on-click #(rf/dispatch [::open-form attachments])}])

(defn redact-attachments-view [{:keys [application-id user redactable-attachments new-attachments on-submit]}]
  [action-form-view action-form-id
   (text :t.actions/redact-attachments)
   [[perform-action-button {:id action-form-id
                            :text (if (seq new-attachments)
                                    (text :t.actions/replace-attachments)
                                    (text :t.actions/remove-attachments))
                            :class :btn-danger
                            :disabled (nil? on-submit)
                            :on-click on-submit}]]
   [:<>
    [select-attachments-field {:field-key action-form-id
                               :attachments redactable-attachments
                               :label (text :t.form/attachments)
                               :user user}]
    [action-attachment {:field-key action-form-id
                        :application-id application-id
                        :label (text :t.form/upload-replacement-attachment)}]
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-comment)}]
    [event-public-field {:field-key action-form-id}]]])

(defn- build-command [application-id {:keys [redact-attachments attachments comment public]}]
  (when (and (some? application-id)
             (seq redact-attachments))
    (let [cmd {:application-id application-id
               :redacted-attachments (map #(select-keys % [:attachment/id]) redact-attachments)
               :public public
               :attachments (map #(select-keys % [:attachment/id]) attachments)
               :comment comment}]
      cmd)))

(defn redact-attachments-form [application-id on-finished]
  (let [form-fields @(rf/subscribe [::form-fields])]
    [redact-attachments-view
     {:application-id application-id
      :user (:userid @(rf/subscribe [:user]))
      :redactable-attachments @(rf/subscribe [::attachments])
      :new-attachments (:attachments form-fields)
      :on-submit (when-some [cmd (build-command application-id form-fields)]
                   #(rf/dispatch [::send-redact-attachments cmd {:on-finished on-finished}]))}]))
