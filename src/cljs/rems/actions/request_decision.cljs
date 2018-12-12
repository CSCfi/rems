(ns rems.actions.request-decision
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.actions.action :refer [action-form-view button-wrapper]]
            [rems.atoms :refer [textarea]]
            [rems.autocomplete :as autocomplete]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(defn fetch-potential-deciders
  [[user on-success]]
  (fetch (str "/api/applications/deciders")
         {:handler on-success
          :headers {"x-rems-user-id" (:eppn user)}}))

(rf/reg-fx ::fetch-potential-deciders fetch-potential-deciders)

(comment
  (fetch-potential-deciders [{:eppn "developer"} prn]))

(defn open-form
  [{:keys [db]} _]
  (merge {:db (assoc db
                     ::comment ""
                     ::potential-deciders #{}
                     ::selected-deciders #{})}
         {::fetch-potential-deciders [(get-in db [:identity :user])
                                      #(rf/dispatch [::set-potential-deciders %])]}))

(rf/reg-event-fx ::open-form open-form)

(comment
  (open-form {:db {:identity {:roles #{:approver} :user {:eppn "developer"}}}}
             [::open-form])
  (rf/dispatch [::open-form]))

;; TODO together with application.cljs extract a user selection component
(defn enrich-user [user]
  (assoc user :display (str (:name user) " (" (:email user) ")")))

(rf/reg-event-db
 ::set-potential-deciders
 (fn [db [_ deciders]]
   (assoc db
          ::potential-deciders (set (map enrich-user deciders))
          ::selected-deciders #{})))

(rf/reg-sub ::potential-deciders (fn [db _] (::potential-deciders db)))

(rf/reg-event-db
 ::set-selected-deciders
 (fn [db [_ deciders]]
   (assoc db ::selected-deciders deciders)))

(rf/reg-event-db
 ::add-selected-decider
 (fn [db [_ decider]]
   (assoc db ::selected-deciders [decider]) ; TODO decide if there can be one or more deciders
   #_(update db ::selected-deciders conj decider)))

(rf/reg-event-db
 ::remove-selected-decider
 (fn [db [_ decider]]
   (update db ::selected-deciders disj decider)))

(rf/reg-sub ::selected-deciders (fn [db _] (::selected-deciders db)))
(rf/reg-sub ::comment (fn [db _] (::comment db)))

(rf/reg-event-db
 ::set-comment
 (fn [db [_ value]] (assoc db ::comment value)))

(defn- send-request-decision! [{:keys [deciders application-id comment on-success on-error]}]
  (post! "/api/applications/command"
         {:params {:application-id application-id
                   :type :rems.workflow.dynamic/request-decision
                   :comment comment
                   :decider (first (map :userid deciders))} ; TODO decide if there can be one or more deciders
          :handler on-success ; TODO interpret :errors as failure
          :error-handler on-error}))

(rf/reg-event-fx
 ::send-request-decision
 (fn [{:keys [db]} [_ {:keys [application-id deciders comment on-pending on-success on-error]}]]
   (send-request-decision! {:deciders deciders
                            :application-id application-id
                            :comment comment
                            :on-success on-success
                            :on-error on-error})
   (on-pending)
   {}))

(defn request-decision-view
  [{:keys [selected-deciders potential-deciders comment on-set-comment on-add-decider on-remove-decider on-send]}]
  [action-form-view "request-decision"
   (text :t.actions/request-decision)
   nil
   [[button-wrapper {:id "do-request-decision"
                     :text (text :t.actions/request-decision)
                     :on-click on-send}]]
   [:div [:div.form-group
          [:label {:for "comment"} (text :t.form/add-comments-not-shown-to-applicant)]
          [textarea {:id "comment"
                     :name "comment"
                     :placeholder (text :t.form/comment)
                     :value comment
                     :on-change #(on-set-comment (.. % -target -value))}]]
    [:div.form-group
     [:label (text :t.actions/request-selection)]
     [autocomplete/component
      {:value (sort-by :display selected-deciders)
       :items potential-deciders
       :value->text #(:display %2)
       :item->key :userid
       :item->text :display
       :item->value identity
       :search-fields [:name :email]
       :add-fn on-add-decider
       :remove-fn on-remove-decider}]]]
   nil
   nil])

(defn request-decision-form [application-id on-finished]
  (let [selected-deciders (rf/subscribe [::selected-deciders])
        potential-deciders (rf/subscribe [::potential-deciders])
        comment (rf/subscribe [::comment])
        description (text :t.actions/request-decision)
        state (r/atom nil)
        on-pending #(reset! state {:status :pending})
        on-success #(reset! state {:status :saved})
        on-error #(reset! state {:status :failed :error %})
        on-modal-close #(do (reset! state nil)
                            (on-finished))]
    (fn [application-id]
      [:div
       (when (:status @state)
         [status-modal (assoc @state
                              :description (text :t.actions/request-decision)
                              :on-close on-modal-close)])
       [request-decision-view {:selected-deciders @selected-deciders
                               :potential-deciders @potential-deciders
                               :comment @comment
                               :on-set-comment #(rf/dispatch [::set-comment %])
                               :on-add-decider #(rf/dispatch [::add-selected-decider %])
                               :on-remove-decider #(rf/dispatch [::remove-selected-decider %])
                               :on-send #(rf/dispatch [::send-request-decision {:application-id application-id
                                                                                :deciders @selected-deciders
                                                                                :comment @comment
                                                                                :on-pending on-pending
                                                                                :on-success on-success
                                                                                :on-error on-error}])}]])))
