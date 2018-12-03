(ns rems.actions.request-comment
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.actions.action :refer [action-form-view button-wrapper]]
            [rems.atoms :refer [textarea]]
            [rems.autocomplete :as autocomplete]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(defn fetch-potential-commenters
  [[user on-success]]
  (fetch (str "/api/applications/commenters")
         {:handler on-success
          :headers {"x-rems-user-id" (:eppn user)}}))

(rf/reg-fx ::fetch-potential-commenters fetch-potential-commenters)

(comment
  (fetch-potential-commenters [{:eppn "developer"} prn]))

(defn open-form
  [{:keys [db]} _]
  (merge {:db (assoc db
                     ::comment ""
                     ::potential-commenters #{}
                     ::selected-commenters #{})}
         {::fetch-potential-commenters [(get-in db [:identity :user])
                                        #(rf/dispatch [::set-potential-commenters %])]}))

(rf/reg-event-fx ::open-form open-form)

(comment
  (open-form {:db {:identity {:roles #{:approver} :user {:eppn "developer"}}}}
             [::open-form prn])
  (rf/dispatch [::open-form]))

;; TODO together with application.cljs extract a user selection component
(defn enrich-user [user]
  (assoc user :display (str (:name user) " (" (:email user) ")")))

(rf/reg-event-db
 ::set-potential-commenters
 (fn [db [_ commenters]]
   (assoc db
          ::potential-commenters (set (map enrich-user commenters))
          ::selected-commenters #{})))

(rf/reg-sub ::potential-commenters (fn [db _] (::potential-commenters db)))

(rf/reg-event-db
 ::set-selected-commenters
 (fn [db [_ commenters]]
   (assoc db ::selected-commenters commenters)))

(rf/reg-event-db
 ::add-selected-commenter
 (fn [db [_ commenter]]
   (update db ::selected-commenters conj commenter)))

(rf/reg-event-db
 ::remove-selected-commenter
 (fn [db [_ commenter]]
   (update db ::selected-commenters disj commenter)))

(rf/reg-sub ::selected-commenters (fn [db _] (::selected-commenters db)))
(rf/reg-sub ::comment (fn [db _] (::comment db)))

(rf/reg-event-db
 ::set-comment
 (fn [db [_ value]] (assoc db ::comment value)))

(defn- send-request-comment! [{:keys [commenters application-id comment on-success on-error]}]
  (post! "/api/applications/command"
         {:params {:application-id application-id
                   :type :rems.workflow.dynamic/request-comment
                   :comment comment
                   :commenters (map :userid commenters)}
          :handler on-success ; TODO interpret :errors as failure
          :error-handler on-error}))

(rf/reg-event-fx
 ::send-request-comment
 (fn [{:keys [db]} [_ {:keys [application-id commenters comment on-pending on-success on-error]}]]
   (let [user (get-in db [:identity :user])]
     (send-request-comment! {:commenters commenters
                             :application-id application-id
                             :comment comment
                             :on-success on-success
                             :on-error on-error})
     (on-pending)
     {})))

(rf/reg-event-db
 ::send-comment-request-success
 (fn [db [_ value]]
   ;; TODO where to set message?
   (assoc db ::send-comment-request-message value)))

(defn request-comment-view
  [{:keys [selected-commenters potential-commenters comment on-set-comment on-add-commenter on-remove-commenter on-send]}]
  [action-form-view "request-comment"
   (text :t.actions/request-comment)
   nil
   [button-wrapper {:id "request-comment"
                    :text (text :t.actions/request-comment)
                    :on-click on-send}]
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
      {:value (sort-by :display selected-commenters)
       :items potential-commenters
       :value->text #(:display %2)
       :item->key :userid
       :item->text :display
       :item->value identity
       :search-fields [:name :email]
       :add-fn on-add-commenter
       :remove-fn on-remove-commenter}]]]
   nil
   nil])

(defn request-comment-form [application-id on-finished]
  (let [selected-commenters (rf/subscribe [::selected-commenters])
        potential-commenters (rf/subscribe [::potential-commenters])
        comment (rf/subscribe [::comment])
        description (text :t.actions/request-comment)
        state (r/atom nil)
        on-pending #(reset! state {:status :pending})
        on-success #(reset! state {:status :saved })
        on-error #(reset! state {:status :failed :error %})
        on-modal-close #(do (reset! state nil)
                            (on-finished))]
    (fn [application-id]
      [:div
       (when (:status @state)
         [status-modal (assoc @state
                              :description (text :t.actions/request-comment)
                              :on-close on-modal-close)])
       [request-comment-view {:selected-commenters @selected-commenters
                              :potential-commenters @potential-commenters
                              :comment @comment
                              :on-set-comment #(rf/dispatch [::set-comment %])
                              :on-add-commenter #(rf/dispatch [::add-selected-commenter %])
                              :on-remove-commenter #(rf/dispatch [::remove-selected-commenter %])
                              :on-send #(rf/dispatch [::send-request-comment {:application-id application-id
                                                                              :commenters @selected-commenters
                                                                              :comment @comment
                                                                              :on-pending on-pending
                                                                              :on-success on-success
                                                                              :on-error on-error}])}]])))
