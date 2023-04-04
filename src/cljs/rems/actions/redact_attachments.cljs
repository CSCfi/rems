(ns rems.actions.redact-attachments
  (:require [medley.core :refer [assoc-some]]
            [re-frame.core :as rf]
            [rems.actions.components :refer [action-attachment action-button action-form-view button-wrapper command! select-attachments-field comment-field comment-public-field]]
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
                 [:rems.actions.components/set-comment-public action-form-id false]
                 [:rems.actions.components/set-attachments action-form-id []]
                 [:rems.actions.components/init-select-attachments action-form-id]]}))

(rf/reg-event-fx
 ::send-redact-attachments
 (fn [_ [_ cmd {:keys [on-finished]}]]
   (command! :application.command/redact-attachments
             cmd
             {:description [text :t.actions/redact-attachments]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(rf/reg-sub ::form-fields
            :<- [:rems.actions.components/select-attachments action-form-id]
            :<- [:rems.actions.components/attachments-with-filenames action-form-id]
            :<- [:rems.actions.components/comment action-form-id]
            :<- [:rems.actions.components/comment-public action-form-id]
            (fn [[select-attachments attachments comment comment-public] _]
              {:redacted-attachments select-attachments
               :attachments attachments
               :comment comment
               :public comment-public}))

(defn redact-attachments-action-button [attachments]
  [action-button {:id action-form-id
                  :text (text :t.actions/redact-attachments)
                  :on-click #(rf/dispatch [::open-form attachments])}])

(defn redact-attachments-view [{:keys [application-id redactable-attachments new-attachments on-submit]}]
  [action-form-view action-form-id
   (text :t.actions/redact-attachments)
   [[button-wrapper (-> {:id action-form-id
                         :text (if (seq new-attachments)
                                 (text :t.actions/replace-attachments)
                                 (text :t.actions/remove-attachments))
                         :class :btn-danger}
                        (assoc-some :on-click on-submit
                                    :disabled (nil? on-submit)))]]
   [:<>
    [select-attachments-field {:field-key action-form-id
                               :attachments redactable-attachments
                               :label (text :t.form/attachments)}]
    [action-attachment {:field-key action-form-id
                        :application-id application-id
                        :label (text :t.form/upload-replacement-attachment)}]
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-comment)}]
    [comment-public-field {:field-key action-form-id
                           :label (text :t.form/comment-public)}]]])

(defn- validate [{:keys [application-id redacted-attachments attachments comment public]}]
  (and (int? application-id)
       (some->> (seq redacted-attachments) (every? some?))
       (boolean? public)
       (every? some? attachments)
       (string? comment)))

(defn- build-command [application-id {:keys [redacted-attachments attachments comment public]}]
  (let [cmd {:application-id application-id
             :redacted-attachments (map #(select-keys % [:attachment/id]) redacted-attachments)
             :public public
             :attachments (map #(select-keys % [:attachment/id]) attachments)
             :comment (or comment "")}]
    (when (validate cmd)
      cmd)))

(defn redact-attachments-form [application-id on-finished]
  (let [form-fields @(rf/subscribe [::form-fields])]
    [redact-attachments-view
     {:application-id application-id
      :redactable-attachments @(rf/subscribe [::attachments])
      :new-attachments (:attachments form-fields)
      :on-submit (when-some [cmd (build-command application-id form-fields)]
                   #(rf/dispatch [::send-redact-attachments cmd {:on-finished on-finished}]))}]))
