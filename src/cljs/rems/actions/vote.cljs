(ns rems.actions.vote
  (:require [clojure.string :as str]
            [goog.string]
            [goog.string.format]
            [re-frame.core :as rf]
            [reagent.core :as rc]
            [rems.actions.components :refer [action-attachment action-button comment-field action-form-view command!]]
            [rems.atoms :as atoms]
            [rems.common.application-util :as application-util]
            [rems.common.util :refer [build-index]]
            [rems.dropdown :as dropdown]
            [rems.text :refer [text text-format]]))

(def ^:private action-form-id "vote")

(rf/reg-event-fx
 ::open-form
 (fn [_ _]
   {:dispatch-n [[::set-vote nil]
                 [:rems.actions.components/set-comment action-form-id ""]
                 [:rems.actions.components/set-attachments action-form-id []]]}))

(rf/reg-event-fx
 ::send-vote
 (fn [_ [_ {:keys [application-id vote comment attachments on-finished]}]]
   (command! :application.command/vote
             {:application-id application-id
              :vote vote
              :comment comment
              :attachments attachments}
             {:description [text :t.actions/vote]
              :collapse action-form-id
              :on-finished on-finished})
   {}))

(defn vote-action-button []
  [action-button {:id action-form-id
                  :text (text :t.actions/vote)
                  :on-click #(rf/dispatch [::open-form])}])

(rf/reg-sub ::vote (fn [db [_]] (::vote db)))
(rf/reg-event-db ::set-vote (fn [db [_ value]] (assoc db ::vote value)))

(defn vote-field [{:keys [previous-vote vote on-vote]}]
  (let [id (str action-form-id "-vote")]
    [:div.vote.mb-3
     [:label.sr-only {:for id} (text :t.actions/vote)]
     [dropdown/dropdown
      {:id id
       ;; XXX: consider making choices dynamic
       :items [{:value "accept" :label (text :t.applications.voting.votes/accept)}
               {:value "reject" :label (text :t.applications.voting.votes/reject)}]
       :item-label (comp rc/as-element :label)
       :item-selected? #(= (or vote previous-vote) (:value %))
       :on-change #(on-vote (:value %))}]]))

;; XXX: many actions have the view-container component separation, we may want to consider just simplifying our code and removing it
(defn vote-view
  [{:keys [application-id previous-vote disabled vote on-vote on-send]}]
  [action-form-view action-form-id
   (text :t.actions/vote)
   [[atoms/rate-limited-button {:id "vote-button"
                                :text (text :t.actions/vote)
                                :class "btn-primary"
                                :disabled (or disabled @(rf/subscribe [:rems.spa/pending-request :application.command/vote]))
                                :on-click on-send}]]
   [:<>
    (when previous-vote
      (text-format :t.applications.voting/previously-voted
                   [:strong (text (keyword (str "t" ".applications.voting.votes") previous-vote))]))

    [vote-field {:previous-vote previous-vote
                 :vote vote
                 :on-vote on-vote}]
    [comment-field {:field-key action-form-id
                    :label (text :t.form/add-comments-not-shown-to-applicant)}]
    [action-attachment {:field-key action-form-id
                        :application-id application-id}]]])

(defn vote-form [application-id application on-finished]
  (let [vote @(rf/subscribe [::vote])
        userid (:userid @(rf/subscribe [:user]))
        comment @(rf/subscribe [:rems.actions.components/comment action-form-id])
        attachments @(rf/subscribe [:rems.actions.components/attachments action-form-id])]
    [vote-view {:application-id application-id
                :previous-vote (get-in application [:application/votes userid])
                :vote vote
                :on-vote #(rf/dispatch [::set-vote %])
                :disabled (not vote)
                :on-send #(rf/dispatch [::send-vote {:application-id application-id
                                                     :vote vote
                                                     :comment comment
                                                     :attachments attachments
                                                     :on-finished on-finished}])}]))

(defn votes-summary
  [application]
  (let [votes (get-in application [:application/votes])
        summary (frequencies (vals votes))
        voters (get-in application [:application/workflow :workflow.dynamic/handlers])
        voter-by-userid (build-index {:keys [:userid]} voters)
        voters-by-vote (build-index {:keys [val] :value-fn (comp voter-by-userid key) :collect-fn conj} votes)]
    [:div.my-3
     [:h3 (text :t.applications/votes)]

     [:div.container-fluid
      (for [[vote n] (sort-by val summary)
            :let [n-pct (* 100 (/ n (count voters)))
                  vote-voters (get voters-by-vote vote)]]
        ^{:key vote}
        [:div.form-group.row
         [:label.col-sm-3.col-form-label (text (keyword (str "t" ".applications.voting.votes") vote))]
         [:div.col-sm-9.form-control (goog.string/format "%.2f%% (%s)"
                                                         n-pct
                                                         (->> vote-voters
                                                              (mapv application-util/get-member-name)
                                                              (str/join ", ")))]])

      (let [n (- (count voters) (count votes))
            n-pct (* 100 (/ n (count voters)))
            missing-voters (->> voters
                                (remove (comp (set (keys votes)) :userid))
                                (mapv application-util/get-member-name)
                                (str/join ", "))]
        [:div.form-group.row
         [:label.col-sm-3.col-form-label (text :t.applications.voting.votes/empty)]
         [:div.col-sm-9.form-control (goog.string/format "%.2f%% (%s)"
                                                         n-pct
                                                         missing-voters)]])]]))
