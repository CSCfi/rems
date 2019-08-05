(ns rems.actions.request-review
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.atoms :refer [enrich-user]]
            [rems.dropdown :as dropdown]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [fetch post!]]))

;; TODO: the api should probably be reviewers now
(rf/reg-fx
 ::fetch-potential-reviewers
 (fn [on-success]
   (fetch "/api/applications/commenters" {:handler on-success})))

(rf/reg-event-fx
 ::open-form
 (fn
   [{:keys [db]} _]
   {:db (assoc db
               ::comment ""
               ::potential-reviewers #{}
               ::selected-reviewers #{})
    ::fetch-potential-reviewers #(rf/dispatch [::set-potential-reviewers %])}))

(rf/reg-sub ::potential-reviewers (fn [db _] (::potential-reviewers db)))
(rf/reg-event-db
 ::set-potential-reviewers
 (fn [db [_ reviewers]]
   (assoc db
          ::potential-reviewers (set (map enrich-user reviewers))
          ::selected-reviewers #{})))

(rf/reg-sub ::selected-reviewers (fn [db _] (::selected-reviewers db)))
(rf/reg-event-db ::set-selected-reviewers (fn [db [_ reviewers]] (assoc db ::selected-reviewers reviewers)))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(def ^:private action-form-id "request-review")
(def ^:private dropdown-id "request-review-dropdown")

(rf/reg-event-fx
 ::send-request-review
 (fn [_ [_ {:keys [application-id reviewers comment on-finished]}]]
   (status-modal/common-pending-handler! (text :t.actions/request-comment))
   (post! "/api/applications/request-comment"
          {:params {:application-id application-id
                    :comment comment
                    :commenters (map :userid reviewers)}
           :handler (partial status-modal/common-success-handler! (fn [_]
                                                                    (collapse-action-form action-form-id)
                                                                    (on-finished)))
           :error-handler status-modal/common-error-handler!})
   {}))

(defn request-review-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/request-comment)
                  :on-click #(rf/dispatch [::open-form])}])

(defn request-review-view
  [{:keys [selected-reviewers potential-reviewers comment on-set-comment on-set-reviewers on-send]}]
  [action-form-view action-form-id
   (text :t.actions/request-comment)
   [[button-wrapper {:id "request-review-button"
                     :text (text :t.actions/request-comment)
                     :class "btn-primary"
                     :on-click on-send
                     :disabled (empty? selected-reviewers)}]]
   [:div
    [action-comment {:id action-form-id
                     :label (text :t.form/add-comments-not-shown-to-applicant)
                     :comment comment
                     :on-comment on-set-comment}]
    [:div.form-group
     [:label {:for dropdown-id} (text :t.actions/request-selection)]
     [dropdown/dropdown
      {:id dropdown-id
       :items potential-reviewers
       :item-label :display
       :item-selected? #(contains? (set selected-reviewers) %)
       :multi? true
       :on-change on-set-reviewers}]]]])

(defn request-review-form [application-id on-finished]
  (let [selected-reviewers @(rf/subscribe [::selected-reviewers])
        potential-reviewers @(rf/subscribe [::potential-reviewers])
        comment @(rf/subscribe [::comment])]
    [request-review-view {:selected-reviewers selected-reviewers
                          :potential-reviewers potential-reviewers
                          :comment comment
                          :on-set-comment #(rf/dispatch [::set-comment %])
                          :on-set-reviewers #(rf/dispatch [::set-selected-reviewers %])
                          :on-send #(rf/dispatch [::send-request-review {:application-id application-id
                                                                         :reviewers selected-reviewers
                                                                         :comment comment
                                                                         :on-finished on-finished}])}]))
