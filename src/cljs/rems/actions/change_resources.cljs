(ns rems.actions.change-resources
  (:require [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text get-localized-title]]
            [rems.util :refer [post!]]))

(rf/reg-event-fx
 ::open-form
 (fn
   [{:keys [db]} [_ initial-resources]]
   (merge
    {:db (assoc db
                ::comment ""
                ::initial-resources (into #{} (map :catalogue-item/id initial-resources))
                ::selected-resources (into #{} (map :catalogue-item/id initial-resources)))}
    (when-not (:rems.catalogue/catalogue db)
      {:dispatch [:rems.catalogue/fetch-catalogue]}))))

(rf/reg-event-db ::set-sorting (fn [db [_ sorting]] (assoc db ::sorting sorting)))
(rf/reg-sub ::sorting (fn [db _] (::sorting db)))

(rf/reg-event-db ::set-filtering (fn [db [_ filtering]] (assoc db ::filtering filtering)))
(rf/reg-sub ::filtering (fn [db _] (::filtering db)))

(rf/reg-sub ::initial-resources (fn [db _] (::initial-resources db)))
(rf/reg-sub ::selected-resources (fn [db _] (::selected-resources db)))
(rf/reg-event-db ::set-selected-resources (fn [db [_ resources]] (assoc db ::selected-resources (map :id resources))))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(def ^:private action-form-id "change-resources")
(def ^:private dropdown-id "change-resources-dropdown")

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
  [{:keys [application initial-resources selected-resources full-catalogue catalogue comment can-comment? language on-set-comment on-set-resources on-send]}]
  (let [original-workflow-id (get-in application [:application/workflow :workflow/id])
        compatible-first-sort-fn #(if (compatible-item? % original-workflow-id) -1 1)
        sorted-selected-catalogue (->> catalogue
                                       (sort-by #(get-localized-title % language))
                                       (sort-by compatible-first-sort-fn))]
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
          [action-comment {:id action-form-id
                           :label (text :t.form/add-comments-shown-to-applicant)
                           :comment comment
                           :on-comment on-set-comment}])
        [:div.form-group
         [:label {:for dropdown-id} (text :t.actions/resources-selection)]
         [dropdown/dropdown
          {:id dropdown-id
           :items sorted-selected-catalogue
           :item-disabled? #(not (compatible-item? % original-workflow-id original-form-id))
           :item-key :id
           :item-label #(get-localized-title % language)
           :item-selected? #(contains? (set selected-resources) (% :id))
           :multi? true
           :on-change on-set-resources}]]
        (text :t.actions/bundling-intro)])]))

(defn change-resources-form [application can-comment? on-finished]
  (let [initial-resources @(rf/subscribe [::initial-resources])
        selected-resources @(rf/subscribe [::selected-resources])
        full-catalogue @(rf/subscribe [:rems.catalogue/full-catalogue])
        catalogue @(rf/subscribe [:rems.catalogue/catalogue])
        comment @(rf/subscribe [::comment])
        language @(rf/subscribe [:language])]
    [change-resources-view {:application application
                            :initial-resources initial-resources
                            :selected-resources selected-resources
                            :full-catalogue full-catalogue
                            :catalogue catalogue
                            :comment comment
                            :can-comment? can-comment?
                            :language language
                            :on-set-comment #(rf/dispatch [::set-comment %])
                            :on-set-resources #(rf/dispatch [::set-selected-resources %])
                            :on-send #(rf/dispatch [::send-change-resources {:application-id (:application/id application)
                                                                             :resources selected-resources
                                                                             :comment comment
                                                                             :on-finished on-finished}])}]))
