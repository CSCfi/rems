(ns rems.actions.request-review
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-attachment action-button action-comment action-form-view button-wrapper command!]]
            [rems.atoms :refer [enrich-user]]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]))

(def ^:private action-form-id "request-review")
(def ^:private dropdown-id "request-review-dropdown")

(rf/reg-fx
 ::fetch-potential-reviewers
 (fn [on-success]
   (fetch "/api/applications/reviewers"
          {:handler on-success
           :error-handler (flash-message/default-error-handler :top "Fetch potential reviewers")})))

(rf/reg-event-fx
 ::open-form
 (fn
   [{:keys [db]} _]
   {:db (assoc db
               ::comment ""
               ::potential-reviewers #{}
               ::selected-reviewers #{})
    :dispatch [:rems.actions.action/set-attachment-id action-form-id nil]
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

(rf/reg-event-fx
 ::send-request-review
 (fn [_ [_ {:keys [application-id reviewers comment attachments on-finished]}]]
   (command! :application.command/request-review
             {:application-id application-id
              :comment comment
              :reviewers (map :userid reviewers)
              :attachments attachments}
             {:description [text :t.actions/request-review]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn request-review-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/request-review)
                  :on-click #(rf/dispatch [::open-form])}])

(defn request-review-view
  [{:keys [application-id selected-reviewers potential-reviewers comment on-set-comment on-set-reviewers on-send]}]
  [action-form-view action-form-id
   (text :t.actions/request-review)
   [[button-wrapper {:id "request-review-button"
                     :text (text :t.actions/request-review)
                     :class "btn-primary"
                     :on-click on-send
                     :disabled (empty? selected-reviewers)}]]
   [:div
    [action-comment {:id action-form-id
                     :label (text :t.form/add-comments-not-shown-to-applicant)
                     :comment comment
                     :on-comment on-set-comment}]
    [action-attachment {:key action-form-id
                        :application-id application-id}]
    [:div.form-group
     [:label {:for dropdown-id} (text :t.actions/request-selection)]
     [dropdown/dropdown
      {:id dropdown-id
       :items potential-reviewers
       :item-key :userid
       :item-label :display
       :item-selected? #(contains? (set selected-reviewers) %)
       :multi? true
       :on-change on-set-reviewers}]]]])

(defn request-review-form [application-id on-finished]
  (let [selected-reviewers @(rf/subscribe [::selected-reviewers])
        potential-reviewers @(rf/subscribe [::potential-reviewers])
        comment @(rf/subscribe [::comment])
        attachments @(rf/subscribe [:rems.actions.action/attachments action-form-id])]
    [request-review-view {:application-id application-id
                          :selected-reviewers selected-reviewers
                          :potential-reviewers potential-reviewers
                          :comment comment
                          :on-set-comment #(rf/dispatch [::set-comment %])
                          :on-set-reviewers #(rf/dispatch [::set-selected-reviewers %])
                          :on-send #(rf/dispatch [::send-request-review {:application-id application-id
                                                                         :reviewers selected-reviewers
                                                                         :comment comment
                                                                         :attachments attachments
                                                                         :on-finished on-finished}])}]))
