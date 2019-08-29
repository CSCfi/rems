(ns rems.actions.remove-member
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::reset-form
 (fn [{:keys [db]} [_ element-id]]
   {:db (assoc-in db [::comment element-id] "")}))

(rf/reg-sub
 ::comment
 (fn [db [_ element-id]]
   (get-in db [::comment element-id])))

(rf/reg-event-db
 ::set-comment
 (fn [db [_ element-id value]]
   (assoc-in db [::comment element-id] value)))

(rf/reg-event-fx
 ::remove-member
 (fn [_ [_ {:keys [collapse-id application-id member comment on-finished]}]]
   (let [description (text :t.actions/remove-member)]
     (post! (if (:userid member)
              "/api/applications/remove-member"
              "/api/applications/uninvite-member")
            {:params {:application-id application-id
                      :member (if (:userid member)
                                (select-keys member [:userid])
                                (select-keys member [:name :email]))
                      :comment comment}
             :handler (flash-message/default-success-handler
                       description
                       (fn [_]
                         (collapse-action-form collapse-id)
                         (on-finished)))
             :error-handler (flash-message/default-error-handler description)}))
   {}))

(defn remove-member-action-button [element-id]
  [action-button {:id (str element-id "-form")
                  :text (text :t.actions/remove-member)
                  :on-click #(rf/dispatch [::reset-form element-id])}])

(defn- remove-member-view
  [{:keys [element-id comment on-set-comment on-send]}]
  [action-form-view (str element-id "-form")
   (text :t.actions/remove-member)
   [[button-wrapper {:id (str element-id "-submit")
                     :text (text :t.actions/remove-member)
                     :class "btn-primary"
                     :on-click on-send}]]
   [action-comment {:id (str element-id "-comment")
                    :label (text :t.form/add-comments-shown-to-applicant)
                    :comment comment
                    :on-comment on-set-comment}]
   {:collapse-id element-id}])

(defn remove-member-form [element-id member application-id on-finished]
  (let [comment @(rf/subscribe [::comment element-id])]
    [remove-member-view {:element-id element-id
                         :comment comment
                         :on-set-comment #(rf/dispatch [::set-comment element-id %])
                         :on-send #(rf/dispatch [::remove-member {:application-id application-id
                                                                  :collapse-id (str element-id "-form")
                                                                  :comment comment
                                                                  :member member
                                                                  :on-finished on-finished}])}]))
