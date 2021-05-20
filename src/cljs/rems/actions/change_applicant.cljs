(ns rems.actions.change-applicant
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-button action-form-view comment-field button-wrapper collapse-action-form]]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::reset-form
 (fn [_ [_ element-id]]
   {:dispatch [:rems.actions.components/set-comment (str element-id "-comment") ""]}))

;; The API allows us to add attachments to these commands
;; but this is left out from the UI for simplicity
(rf/reg-event-fx
 ::change-applicant
 (fn [_ [_ {:keys [collapse-id application-id member comment on-finished]}]]
   (let [description [text :t.actions/change-applicant]]
     (post! "/api/applications/change-applicant"
            {:params {:application-id application-id
                      :member (select-keys member [:userid])
                      :comment comment}
             :handler (flash-message/default-success-handler
                       :change-members
                       description
                       (fn [_]
                         (collapse-action-form collapse-id)
                         (on-finished)))
             :error-handler (flash-message/default-error-handler :change-members description)}))
   {}))

(defn- qualify-parent-id [parent-id]
  (str parent-id "-change"))

(defn change-applicant-action-button [parent-id]
  (let [element-id (qualify-parent-id parent-id)]
    [action-button {:id element-id
                    :text (text :t.actions/change-applicant)
                    :on-click #(rf/dispatch [::reset-form element-id])}]))

(defn- change-applicant-view
  [{:keys [parent-id on-send]}]
  (let [element-id (qualify-parent-id parent-id)]
    [action-form-view element-id
     (text :t.actions/change-applicant)
     [[button-wrapper {:id (str element-id "-submit")
                       :text (text :t.actions/change-applicant)
                       :class "btn-primary"
                       :on-click on-send}]]
     [comment-field {:field-key (str element-id "-comment")
                     :label (text :t.form/add-comments-shown-to-applicant)}]
     {:collapse-id parent-id}]))

(defn change-applicant-form [parent-id member application-id on-finished]
  (let [element-id (qualify-parent-id parent-id)
        comment @(rf/subscribe [:rems.actions.components/comment (str element-id "-comment")])]
    [change-applicant-view {:parent-id parent-id
                            :on-send #(rf/dispatch [::change-applicant {:application-id application-id
                                                                        :collapse-id element-id
                                                                        :comment comment
                                                                        :member member
                                                                        :on-finished on-finished}])}]))
