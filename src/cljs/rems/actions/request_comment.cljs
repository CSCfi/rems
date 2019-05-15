(ns rems.actions.request-comment
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.atoms :refer [enrich-user]]
            [rems.autocomplete :as autocomplete]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

(rf/reg-fx
 ::fetch-potential-commenters
 (fn [on-success]
   (fetch "/api/applications/commenters" {:handler on-success})))

(rf/reg-event-fx
 ::open-form
 (fn
   [{:keys [db]} _]
   {:db (assoc db
               ::comment ""
               ::potential-commenters #{}
               ::selected-commenters #{})
    ::fetch-potential-commenters #(rf/dispatch [::set-potential-commenters %])}))

(rf/reg-sub ::potential-commenters (fn [db _] (::potential-commenters db)))
(rf/reg-event-db
 ::set-potential-commenters
 (fn [db [_ commenters]]
   (assoc db
          ::potential-commenters (set (map enrich-user commenters))
          ::selected-commenters #{})))

(rf/reg-sub ::selected-commenters (fn [db _] (::selected-commenters db)))
(rf/reg-event-db ::set-selected-commenters (fn [db [_ commenters]] (assoc db ::selected-commenters commenters)))
(rf/reg-event-db ::add-selected-commenter (fn [db [_ commenter]] (update db ::selected-commenters conj commenter)))
(rf/reg-event-db ::remove-selected-commenter (fn [db [_ commenter]] (update db ::selected-commenters disj commenter)))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(def ^:private action-form-id "request-comment")

(rf/reg-event-fx
 ::send-request-comment
 (fn [_ [_ {:keys [application-id commenters comment on-finished]}]]
   (status-modal/common-pending-handler! (text :t.actions/request-comment))
   (post! "/api/applications/request-comment"
          {:params {:application-id application-id
                    :comment comment
                    :commenters (map :userid commenters)}
           :handler (partial status-modal/common-success-handler! (fn [_]
                                                                    (collapse-action-form action-form-id)
                                                                    (on-finished)))
           :error-handler status-modal/common-error-handler!})
   {}))

(defn request-comment-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/request-comment)
                  :on-click #(rf/dispatch [::open-form])}])

(defn request-comment-view
  [{:keys [selected-commenters potential-commenters comment on-set-comment on-add-commenter on-remove-commenter on-send]}]
  [action-form-view action-form-id
   (text :t.actions/request-comment)
   [[button-wrapper {:id "request-comment"
                     :text (text :t.actions/request-comment)
                     :class "btn-primary"
                     :on-click on-send
                     :disabled (empty? selected-commenters)}]]
   [:div
    [action-comment {:id action-form-id
                     :label (text :t.form/add-comments-not-shown-to-applicant)
                     :comment comment
                     :on-comment on-set-comment}]
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
       :remove-fn on-remove-commenter}]]]])

(defn request-comment-form [application-id on-finished]
  (let [selected-commenters @(rf/subscribe [::selected-commenters])
        potential-commenters @(rf/subscribe [::potential-commenters])
        comment @(rf/subscribe [::comment])]
    [request-comment-view {:selected-commenters selected-commenters
                           :potential-commenters potential-commenters
                           :comment comment
                           :on-set-comment #(rf/dispatch [::set-comment %])
                           :on-add-commenter #(rf/dispatch [::add-selected-commenter %])
                           :on-remove-commenter #(rf/dispatch [::remove-selected-commenter %])
                           :on-send #(rf/dispatch [::send-request-comment {:application-id application-id
                                                                           :commenters selected-commenters
                                                                           :comment comment
                                                                           :on-finished on-finished}])}]))
