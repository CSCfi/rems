(ns rems.actions.change-resources
  (:require [re-frame.core :as rf]
            [rems.actions.components :refer [action-button action-form-view comment-field button-wrapper collapse-action-form]]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [medley.core :refer [distinct-by]]
            [rems.spinner :as spinner]
            [rems.text :refer [text get-localized-title]]
            [rems.util :refer [post!]]))

(def ^:private action-form-id "change-resources")

(rf/reg-event-fx
 ::open-form
 (fn
   [{:keys [db]} [_ initial-resources]]
   (merge
    {:db (assoc db
                ::initial-resources (into #{} (map :catalogue-item/id initial-resources))
                ::selected-resources (into #{} (map :catalogue-item/id initial-resources)))
     :dispatch-n (concat [[:rems.actions.components/set-comment action-form-id ""]]
                         (when-not (:rems.catalogue/catalogue db)
                           [[:rems.catalogue/full-catalogue]]))})))

(rf/reg-sub
 ::catalogue
 (fn [_ _]
   [(rf/subscribe [::selected-resources])
    (rf/subscribe [:rems.catalogue/catalogue])
    (rf/subscribe [:rems.catalogue/full-catalogue])])
 (fn [[selected-resources catalogue full-catalogue] _]
   (->> (filter (comp (set selected-resources) :id) full-catalogue) ; from full catalogue the items that are selected that can be disabled
        (concat catalogue)
        (distinct-by :id))))

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))
(rf/reg-sub ::sorting (fn [db _] (::sorting db)))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (::filtering db)))

(rf/reg-sub ::initial-resources (fn [db _] (::initial-resources db)))
(rf/reg-sub ::selected-resources (fn [db _] (::selected-resources db)))
(rf/reg-event-db ::set-selected-resources (fn [db [_ resources]] (assoc db ::selected-resources (set (map :id resources)))))

(def ^:private dropdown-id "change-resources-dropdown")

;; The API allows us to add attachments to this command
;; but this is left out from the UI for simplicity
(rf/reg-event-fx
 ::send-change-resources
 (fn [_ [_ {:keys [application-id resources comment on-finished]}]]
   (let [description [text :t.actions/change-resources]]
     (post! "/api/applications/change-resources"
            {:params (merge {:application-id application-id
                             :catalogue-item-ids (vec resources)}
                            (when comment
                              {:comment comment}))
             :handler (flash-message/default-success-handler
                       :change-resources
                       description
                       (fn [_]
                         (collapse-action-form action-form-id)
                         (on-finished)))
             :error-handler (flash-message/default-error-handler :change-resources description)}))
   {}))

(defn change-resources-action-button [initial-resources]
  [action-button {:id action-form-id
                  :text (text :t.actions/change-resources)
                  :on-click #(rf/dispatch [::open-form initial-resources])}])

(defn compatible-item? [item original-workflow-id]
  (= original-workflow-id (:wfid item)))

(defn change-resources-view
  [{:keys [application initial-resources selected-resources catalogue can-comment? language on-set-resources on-send]}]
  (let [original-workflow-id (get-in application [:application/workflow :workflow/id])
        compatible-first-sort-fn #(if (compatible-item? % original-workflow-id) -1 1)
        sorted-selected-catalogue (->> catalogue
                                       (sort-by #(get-localized-title % language))
                                       (sort-by compatible-first-sort-fn))
        config @(rf/subscribe [:rems.config/config])]
    [action-form-view action-form-id
     (text :t.actions/change-resources)
     [[button-wrapper {:id "change-resources"
                       :text (text :t.actions/change-resources)
                       :class "btn-primary"
                       :disabled (or (empty? selected-resources)
                                     (= selected-resources initial-resources))
                       :on-click on-send}]]
     (if (empty? catalogue)
       [spinner/big]
       ;; TODO: Nowadays the user cannot select resources that have an
       ;;   incompatible form or workflow. Delete extra code here that
       ;;   previously showed a warning if the selected resources were
       ;;   incompatible.
       [:div
        (when can-comment?
          [comment-field {:field-key action-form-id
                          :label (text :t.form/add-comments-shown-to-applicant)}])
        [:div.form-group
         [:label {:for dropdown-id} (text :t.actions/resources-selection)]
         [dropdown/dropdown
          {:id dropdown-id
           :items sorted-selected-catalogue
           :item-disabled? #(not (compatible-item? % original-workflow-id))
           :item-key :id
           :item-label #(get-localized-title % language)
           :item-selected? #(contains? (set selected-resources) (% :id))
           :multi? (:enable-cart config)
           :on-change #(on-set-resources (flatten (list %)))}]] ; single resource or list
        (when (:enable-cart config)
          (text :t.actions/bundling-intro))])]))

(defn change-resources-form [application can-comment? on-finished]
  (let [initial-resources @(rf/subscribe [::initial-resources])
        selected-resources @(rf/subscribe [::selected-resources])
        catalogue @(rf/subscribe [::catalogue])
        comment @(rf/subscribe [:rems.actions.components/comment action-form-id])
        language @(rf/subscribe [:language])]
    [change-resources-view {:application application
                            :initial-resources initial-resources
                            :selected-resources selected-resources
                            :catalogue catalogue
                            :can-comment? can-comment?
                            :language language
                            :on-set-resources #(rf/dispatch [::set-selected-resources %])
                            :on-send #(rf/dispatch [::send-change-resources {:application-id (:application/id application)
                                                                             :resources selected-resources
                                                                             :comment comment
                                                                             :on-finished on-finished}])}]))
