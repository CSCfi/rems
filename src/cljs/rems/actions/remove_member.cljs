(ns rems.actions.remove-member
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::reset-form
 (fn [{:keys [db]} [_ member-id]]
   {:db (assoc-in db [::comment member-id] "")}))

(rf/reg-sub
 ::comment
 (fn [db [_ member-id]]
   (get-in db [::comment member-id])))

(rf/reg-event-db
 ::set-comment
 (fn [db [_ member-id value]]
   (assoc-in db [::comment member-id] value)))

(rf/reg-event-fx
 ::remove-member
 (fn [_ [_ {:keys [collapse-id application-id member comment on-finished]}]]
   (status-modal/common-pending-handler! (text :t.actions/remove-member))
   (post! (if (:userid member)
            "/api/applications/remove-member"
            "/api/applications/uninvite-member")
          {:params {:application-id application-id
                    :member (if (:userid member)
                              (select-keys member [:userid])
                              (select-keys member [:name :email]))
                    :comment comment}
           :handler (partial status-modal/common-success-handler! (fn [_]
                                                                    (collapse-action-form collapse-id)
                                                                    (on-finished)))
           :error-handler status-modal/common-error-handler!})
   {}))

(defn remove-member-action-button [member-collapse-id member-id]
  [action-button {:id (str member-collapse-id "-remove-member")
                  :text (text :t.actions/remove-member)
                  :on-click #(rf/dispatch [::reset-form member-id])}])

(defn remove-member-view
  [{:keys [member-collapse-id comment on-set-comment on-send]}]
  [action-form-view (str member-collapse-id "-remove-member")
   (text :t.actions/remove-member)
   [[button-wrapper {:id (str member-collapse-id "-remove-member-submit")
                     :text (text :t.actions/remove-member)
                     :class "btn-primary"
                     :on-click on-send}]]
   [action-comment {:id (str member-collapse-id "-remove-member-comment")
                    :label (text :t.form/add-comments-shown-to-applicant)
                    :comment comment
                    :on-comment on-set-comment}]
   {:collapse-id member-collapse-id}])

(defn remove-member-form [application-id member-collapse-id member on-finished]
  (let [member-id (:userid member)
        comment @(rf/subscribe [::comment member-id])]
    [remove-member-view {:member-collapse-id member-collapse-id
                         :comment comment
                         :on-set-comment #(rf/dispatch [::set-comment member-id %])
                         :on-send #(rf/dispatch [::remove-member {:application-id application-id
                                                                  :collapse-id (str member-collapse-id "-remove-member")
                                                                  :comment comment
                                                                  :member member
                                                                  :on-finished on-finished}])}]))
