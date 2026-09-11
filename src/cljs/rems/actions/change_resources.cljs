(ns rems.actions.change-resources
  (:require [clojure.set :as set]
            [medley.core :refer [distinct-by]]
            [re-frame.core :as rf]
            [rems.actions.components :refer [action-button action-form-view
                                             collapse-action-form
                                             comment-field
                                             perform-action-button]]
            [rems.dropdown :as dropdown]
            [rems.flash-message :as flash-message]
            [rems.globals]
            [rems.spinner :as spinner]
            [rems.text :refer [get-localized-title text]]
            [rems.util :refer [post!]]))

(def ^:private action-form-id "change-resources")

(rf/reg-event-fx
 ::open-form
 (fn
   [{:keys [db]} [_ initial-resources]]
   {:db (assoc db
               ::initial-resources (into #{} (map :catalogue-item/id initial-resources))
               ::selected-resources (into #{} (map :catalogue-item/id initial-resources))
               ::error nil)
    :fx [[:dispatch [:rems.actions.components/set-comment action-form-id ""]]
         (when-not (:rems.catalogue/catalogue db)
           [:dispatch [:rems.catalogue/full-catalogue]])
         (when (:enable-catalogue-hierarchy @rems.globals/config)
             [:dispatch [:rems.catalogue/entitlements]])]}))

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

(rf/reg-sub ::command-error (fn [db _] some? (::command-error db)))
(rf/reg-event-db ::set-command-error (fn [db [_ error]] (assoc db ::command-error error)))

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
             :handler (fn [{:keys [success errors] :as response}]
                        (cond success
                              (do
                                ((flash-message/default-success-handler
                                  :change-resources
                                  description
                                  (fn [_]
                                    (collapse-action-form action-form-id)
                                    (on-finished)))
                                 response)
                                (rf/dispatch [::set-command-error nil]))
                              errors
                              (do
                                (flash-message/show-error!
                                 :change-resources
                                 (->> errors
                                      (mapv (flash-message/argumentize-some-key :catalogue-item-id :catalogue-item-ids))
                                      flash-message/format-errors))
                                (rf/dispatch [::set-command-error errors]))))
             :error-handler (flash-message/default-error-handler :change-resources description)}))
   {}))

(defn change-resources-action-button [initial-resources]
  [action-button {:id action-form-id
                  :text (text :t.actions/change-resources)
                  :on-click #(rf/dispatch [::open-form initial-resources])}])

(defn compatible-item? [item original-workflow-id]
  (= original-workflow-id (:wfid item)))

(defn compatible-hierarchy? [catalogue-item selected-resources entitlements]
  (if-let [top-level-id (-> catalogue-item :part-of :catalogue-item/id)]
    (contains? (set/union entitlements selected-resources) top-level-id)
    true))

(defn change-resources-view
  [{:keys [application initial-resources selected-resources catalogue entitlements can-comment? on-set-resources on-send]}]
  (let [original-workflow-id (get-in application [:application/workflow :workflow/id])
        compatible-first-sort-fn #(if (compatible-item? % original-workflow-id) -1 1)
        sorted-selected-catalogue (->> catalogue
                                       (sort-by #(get-localized-title %))
                                       (sort-by compatible-first-sort-fn))
        enable-cart? (:enable-cart @rems.globals/config)
        enable-hierarchy? (:enable-catalogue-hierarchy @rems.globals/config)
        item-disabled? #(or (not (compatible-item? % original-workflow-id))
                            (and enable-hierarchy?
                                 (not (compatible-hierarchy? % selected-resources entitlements))))]
    [action-form-view action-form-id
     (text :t.actions/change-resources)
     [[perform-action-button {:id "change-resources"
                              :text (text :t.actions/change-resources)
                              :class "btn-primary"
                              :disabled (or (empty? selected-resources)
                                            (and (not @(rf/subscribe [::command-error]))
                                                 (= selected-resources initial-resources)))
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
           :items (->> sorted-selected-catalogue
                       (mapv #(assoc % ::label (get-localized-title %))))
           :item-disabled? item-disabled?
           :item-key :id
           :item-label ::label
           :item-selected? #(contains? (set selected-resources) (% :id))
           :multi? enable-cart?
           :on-change (if enable-cart?
                        (fn [items] (on-set-resources items))
                        (fn [item] (on-set-resources [item])))}]]
        (when enable-cart?
          (text :t.actions/bundling-intro))])]))

(defn change-resources-form [application can-comment? on-finished]
  (let [initial-resources @(rf/subscribe [::initial-resources])
        selected-resources @(rf/subscribe [::selected-resources])
        catalogue @(rf/subscribe [::catalogue])
        entitlements @(rf/subscribe [:rems.catalogue/entitlements->catalogue-item-ids])
        comment @(rf/subscribe [:rems.actions.components/comment action-form-id])]
    [change-resources-view {:application application
                            :initial-resources initial-resources
                            :selected-resources selected-resources
                            :catalogue catalogue
                            :entitlements entitlements
                            :can-comment? can-comment?
                            :on-set-resources #(rf/dispatch [::set-selected-resources %])
                            :on-send #(rf/dispatch [::send-change-resources {:application-id (:application/id application)
                                                                             :resources selected-resources
                                                                             :comment comment
                                                                             :on-finished on-finished}])}]))
