(ns rems.actions.request-comment
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-form-view button-wrapper]]
            [rems.atoms :refer [textarea]]
            [rems.autocomplete :as autocomplete]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(rf/reg-fx
 ::fetch-potential-commenters
 (fn [user]
   (fetch (str "/api/applications/reviewers") ; TODO separate API for commenters
          {:handler #(do (rf/dispatch [::set-potential-commenters %])
                         (rf/dispatch [::set-selected-commenters #{}]))
           :headers {"x-rems-user-id" (:eppn user)}})))

(rf/reg-event-fx
 ::open-form
 (fn [{:keys [db]} [_]]
   (prn (get-in db [:identity :roles]))
   (merge {:db (assoc db
                      ::comment ""
                      ::potential-commenters #{}
                      ::selected-commenters #{})}
          (when (contains? (get-in db [:identity :roles]) :approver) ; TODO handler role?
            {::fetch-potential-commenters (get-in db [:identity :user])}))))



;; TODO together with application.cljs
(defn enrich-user [user]
  (assoc user :display (str (:name user) " (" (:email user) ")")))

(rf/reg-event-db
 ::set-potential-commenters
 (fn [db [_ users]]
   (assoc db ::potential-commenters (map enrich-user users))))

(rf/reg-sub ::potential-commenters (fn [db _] (::potential-commenters db)))

(rf/reg-event-db
 ::set-selected-commenters
 (fn [db [_ users]]
   (assoc db ::selected-commenters users)))

(rf/reg-event-db
 ::add-selected-commenter
 (fn [db [_ user]]
   (if (contains? (::selected-commenters db) user)
     db
     (update db ::selected-commenters conj user))))

(rf/reg-event-db
 ::remove-selected-commenter
 (fn [db [_ user]]
   (update db ::selected-commenters disj user)))

(rf/reg-sub ::selected-commenters (fn [db _] (::selected-commenters db)))
(rf/reg-sub ::comment (fn [db _] (::comment db)))

(rf/reg-event-db
 ::set-comment
 (fn [db [_ value]] (assoc db ::comment value)))

(defn- send-request-comment! [commenters user application-id comment description]
  (post! "/api/applications/request_comment"
         {:params {:application-id application-id
                   :comment comment
                   :recipients (map :userid commenters)}
          :handler (fn [resp]
                     ;; TODO use callbacks so no dependency?
                     (rf/dispatch [:rems.application/set-status {:status :saved
                                                                 :description description}])
                     (rf/dispatch [::send-request-comment-success true])
                     (rf/dispatch [:rems.application/enter-application-page application-id])
                     #_(scroll-to-top!))
          :error-handler (fn [error]
                           (rf/dispatch [:rems.application/set-status {:status :failed
                                                                        :description description
                                                                        :error error}]))}))

(rf/reg-event-fx
 ::send-request-comment
 (fn [{:keys [db]} [_ commenters comment description]]
   (let [application-id (get-in db [:rems.application/application :application :id]) ; TODO circular dependency
         user (get-in db [:identity :user])]
     (send-request-comment! commenters user application-id comment description)
     ;; TODO where to set status?
     {:dispatch [:rems.application/set-status {:status :pending
                                               :description description}]})))

(rf/reg-event-db
 ::send-comment-request-success
 (fn [db [_ value]]
   ;; TODO where to set message?
   (assoc db ::send-comment-request-message value)))

;; TODO potentially use local ratom
;; TODO potentially use callbacks and dispatch in container
(defn request-comment-view [selected-commenters potential-commenters comment on-comment]
  (prn comment)
  [action-form-view "request-comment"
   (text :t.actions/review-request) ; TODO change localization keys
   nil
   [button-wrapper {:id "request-comment"
                    :text (text :t.actions/review-request)
                    :on-click #(rf/dispatch [::send-request-comment selected-commenters comment (text :t.actions/review-request)])}]
   [:div [:div.form-group
          [:label {:for "comment"} (text :t.form/add-comments-not-shown-to-applicant)]
          [textarea {:id "comment"
                     :name "comment"
                     :placeholder (text :t.form/comment)
                     :value comment
                     :on-change #(rf/dispatch [::set-comment (.. % -target -value)])}]]
    [:div.form-group
     [:label (text :t.actions/review-request-selection)]
     [autocomplete/component
      {:value (sort-by :display selected-commenters)
       :items potential-commenters
       :value->text #(:display %2)
       :item->key :userid
       :item->text :display
       :item->value identity
       :search-fields [:name :email]
       :add-fn #(rf/dispatch [::add-selected-commenter %])
       :remove-fn #(rf/dispatch [::remove-selected-commenter %])}]]]
   nil
   nil])

(defn request-comment-form []
  (let [selected-commenters @(rf/subscribe [::selected-commenters])
        potential-commenters @(rf/subscribe [::potential-commenters])
        comment @(rf/subscribe [::comment])]
    [request-comment-view selected-commenters potential-commenters comment #(rf/dispatch [::set-comment %])]))
