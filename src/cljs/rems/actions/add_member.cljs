(ns rems.actions.add-member
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.actions.action :refer [action-button action-form-view button-wrapper]]
            [rems.atoms :refer [textarea]]
            [rems.autocomplete :as autocomplete]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(defn fetch-potential-members
  [[user on-success]]
  (fetch (str "/api/applications/members")
         {:handler on-success
          :headers {"x-rems-user-id" (:eppn user)}}))

(rf/reg-fx ::fetch-potential-members fetch-potential-members)

(defn open-form
  [{:keys [db]} _]
  {:db (assoc db
              ::potential-members #{}
              ::selected-members #{})
   ::fetch-potential-members [(get-in db [:identity :user])
                              #(rf/dispatch [::set-potential-members %])]})

(rf/reg-event-fx ::open-form open-form)

;; TODO together with application.cljs extract a user selection component
(defn enrich-user [user]
  (assoc user :display (str (:name user) " (" (:email user) ")")))

(rf/reg-event-db
 ::set-potential-members
 (fn [db [_ members]]
   (assoc db
          ::potential-members (set (map enrich-user members))
          ::selected-members #{})))

(rf/reg-sub ::potential-members (fn [db _] (::potential-members db)))

(rf/reg-event-db
 ::set-selected-member
 (fn [db [_ member]]
   (assoc db ::selected-member member)))

(rf/reg-event-db
 ::remove-selected-member
 (fn [db [_ member]]
   (dissoc db ::selected-member)))

(rf/reg-sub ::selected-member (fn [db _] (::selected-member db)))

(defn- send-add-member! [{:keys [member application-id on-success on-error]}]
  (post! "/api/applications/command"
         {:params {:application-id application-id
                   :type :rems.workflow.dynamic/add-member
                   :member (select-keys member [:userid])}
          :handler (fn [response]
                     (if (:success response)
                       (on-success response)
                       (on-error (first (:errors response)))))
          :error-handler on-error}))

(rf/reg-event-fx
 ::send-add-member
 (fn [{:keys [db]} [_ {:keys [application-id member on-pending on-success on-error]}]]
   (send-add-member! {:member member
                      :application-id application-id
                      :on-success on-success
                      :on-error on-error})
   (on-pending)
   {}))

(def ^:private action-form-id "add-member")

(defn add-member-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/add-member)
                  :on-click #(rf/dispatch [::open-form])}])

(defn add-member-view
  [{:keys [selected-member potential-members on-add-member on-remove-member on-send]}]
  [action-form-view action-form-id
   (text :t.actions/add-member)
   [[button-wrapper {:id "add-member"
                     :text (text :t.actions/add-member)
                     :class "btn-primary"
                     :on-click on-send}]]
   [:div
    [:div.form-group
     [:label (text :t.actions/member)]
     [autocomplete/component
      {:value (when selected-member [selected-member])
       :items potential-members
       :value->text #(:display %2)
       :item->key :userid
       :item->text :display
       :item->value identity
       :search-fields [:name :email]
       :add-fn on-add-member
       :remove-fn on-remove-member}]]]
   {:collapse-id "member-action-forms"}])

(defn add-member-form [application-id on-finished]
  (let [selected-member (rf/subscribe [::selected-member])
        potential-members (rf/subscribe [::potential-members])
        description (text :t.actions/add-member)
        state (r/atom nil)
        on-pending #(reset! state {:status :pending})
        on-success #(reset! state {:status :saved})
        on-error #(reset! state {:status :failed :error %})
        on-modal-close #(do (reset! state nil)
                            (on-finished))]
    (fn [application-id on-finished]
      [:div
       (when (:status @state)
         [status-modal (assoc @state
                              :description (text :t.actions/add-member)
                              :on-close on-modal-close)])
       [add-member-view {:selected-member @selected-member
                         :potential-members @potential-members
                         :on-add-member #(rf/dispatch [::set-selected-member %])
                         :on-remove-member #(rf/dispatch [::remove-selected-member %])
                         :on-send #(rf/dispatch [::send-add-member {:application-id application-id
                                                                    :member @selected-member
                                                                    :on-pending on-pending
                                                                    :on-success on-success
                                                                    :on-error on-error}])}]])))
