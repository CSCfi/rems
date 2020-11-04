(ns rems.actions.add-member
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-button action-form-view button-wrapper collapse-action-form]]
            [rems.atoms :as atoms]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))



(rf/reg-fx
 ::fetch-potential-members
 (fn [on-success]
   (fetch "/api/applications/members"
          {:handler on-success
           :error-handler (flash-message/default-error-handler :top "Fetch potential members")})))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} _]
   {:db (assoc db
               ::potential-members #{}
               ::selected-member nil)
    ::fetch-potential-members #(rf/dispatch [::set-potential-members %])}))

(rf/reg-sub ::potential-members (fn [db _] (::potential-members db)))
(rf/reg-event-db
 ::set-potential-members
 (fn [db [_ members]]
   (assoc db
          ::potential-members (set (map atoms/enrich-user members))
          ::selected-member nil)))

(rf/reg-event-db ::set-selected-member (fn [db [_ member]] (assoc db ::selected-member member)))
(rf/reg-sub ::selected-member (fn [db _] (::selected-member db)))

(def ^:private action-form-id "add-member")
(def ^:private dropdown-id "add-member-dropdown")

(rf/reg-event-fx
 ::send-add-member
 (fn [_ [_ {:keys [member application-id on-finished]}]]
   (let [description [text :t.actions/add-member]]
     (post! "/api/applications/add-member"
            {:params {:application-id application-id
                      :member (select-keys member [:userid])}
             :handler (flash-message/default-success-handler
                       :change-members
                       description
                       (fn [_]
                         (collapse-action-form action-form-id)
                         (on-finished)))
             :error-handler (flash-message/default-error-handler :change-members description)}))
   {}))

(defn add-member-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/add-member)
                  :on-click #(rf/dispatch [::open-form])}])

(defn add-member-view
  [{:keys [selected-member potential-members on-set-member on-send]}]
  [action-form-view action-form-id
   (text :t.actions/add-member)
   [[button-wrapper {:id "add-member-submit"
                     :text (text :t.actions/add-member)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:div
    [:div.form-group
     [:label {:for dropdown-id} (text :t.actions/member)]
     [dropdown/dropdown
      {:id dropdown-id
       :items potential-members
       :item-key :userid
       :item-label :display
       :item-selected? #(= selected-member %)
       :on-change on-set-member}]]]
   {:collapse-id "member-action-forms"}])

(defn add-member-form [application-id on-finished]
  (let [selected-member @(rf/subscribe [::selected-member])
        potential-members @(rf/subscribe [::potential-members])]
    [add-member-view {:selected-member selected-member
                      :potential-members potential-members
                      :on-set-member #(rf/dispatch [::set-selected-member %])
                      :on-send #(rf/dispatch [::send-add-member {:application-id application-id
                                                                 :member selected-member
                                                                 :on-finished on-finished}])}]))
