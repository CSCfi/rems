(ns rems.actions.change-resources
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.common-util :refer [index-by]]
            [rems.dropdown :as dropdown]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text get-localized-title]]
            [rems.util :refer [fetch post!]]))

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
   (status-modal/common-pending-handler! (text :t.actions/change-resources))
   (post! "/api/applications/change-resources"
          {:params (merge {:application-id application-id
                           :catalogue-item-ids (vec resources)}
                          (when comment
                            {:comment comment}))
           :handler (partial status-modal/common-success-handler! (fn [_]
                                                                    (collapse-action-form action-form-id)
                                                                    (on-finished)))
           :error-handler status-modal/common-error-handler!})
   {}))

(defn change-resources-action-button [initial-resources]
  [action-button {:id action-form-id
                  :text (text :t.actions/change-resources)
                  :on-click #(rf/dispatch [::open-form initial-resources])}])

(defn- show-bundling-warning? [resources]
  (let [workflows (set (map :wfid resources))
        forms (set (map :formid resources))]
    (and (seq resources)
         (not= 1 (count workflows) (count forms)))))

(defn- bundling-warning [resources can-bundle-all? language]
  [:div
   [:div.alert {:class (if can-bundle-all? :alert-warning :alert-danger)}
    [:p (if can-bundle-all?
          (text :t.actions/bundling-warning)
          (text :t.actions/bundling-error))]
    (into [:ul]
          (for [group (vals (group-by (juxt :wfid :formid) resources))]
            [:li (str/join ", " (map #(get-localized-title % language) group))]))]])

(defn- show-change-form-warning? [original-form-id resources]
  (and (seq resources)
       (apply not= original-form-id (map :formid resources))))

(defn- change-form-warning [resources can-bundle-all? language]
  [:div
   [:div.alert {:class (if can-bundle-all? :alert-warning :alert-danger)}
    [:p (if can-bundle-all?
          (text :t.actions/change-form-warning)
          (text :t.actions/change-form-error))]
    (into [:ul]
          (for [group (vals (group-by :formid resources))]
            [:li (str/join ", " (map #(get-localized-title % language) group))]))]])

(defn- show-change-workflow-warning? [original-workflow-id resources]
  (and (seq resources)
       (apply not= original-workflow-id (map :wfid resources))))

(defn- change-workflow-warning [resources can-bundle-all? language]
  [:div
   [:div.alert {:class (if can-bundle-all? :alert-warning :alert-danger)}
    [:p (if can-bundle-all?
          (text :t.actions/change-workflow-warning)
          (text :t.actions/change-workflow-error))]
    (into [:ul]
          (for [group (vals (group-by :wfid resources))]
            [:li (str/join ", " (map #(get-localized-title % language) group))]))]])

(defn compatible-item? [item resources original-workflow-id original-form-id]
  (not (or (show-bundling-warning? (conj resources item))
           (show-change-form-warning? original-form-id (conj resources item))
           (show-change-workflow-warning? original-workflow-id (conj resources item)))))

(defn change-resources-view
  [{:keys [application initial-resources selected-resources full-catalogue catalogue comment can-bundle-all? can-comment? language on-set-comment on-set-resources on-send]}]
  (let [indexed-resources (index-by [:id] full-catalogue)
        enriched-selected-resources (->> selected-resources
                                         (select-keys indexed-resources)
                                         vals
                                         (sort-by #(get-localized-title % language)))
        original-form-id (get-in application [:application/form :form/id])
        original-workflow-id (get-in application [:application/workflow :workflow/id])
        compatible-first-sort-fn #(if (compatible-item? % enriched-selected-resources original-workflow-id original-form-id) -1 1)
        sorted-selected-catalogue (->> catalogue
                                       (sort-by #(get-localized-title % language))
                                       (sort-by compatible-first-sort-fn))]
    [action-form-view action-form-id
     (text :t.actions/change-resources)
     [[button-wrapper {:id "change-resources"
                       :text (text :t.actions/change-resources)
                       :class "btn-primary"
                       :disabled (or (and (not can-bundle-all?)
                                          (or (show-bundling-warning? enriched-selected-resources))
                                          (show-change-form-warning? original-form-id enriched-selected-resources)
                                          (show-change-workflow-warning? original-workflow-id enriched-selected-resources))
                                     (empty? selected-resources)
                                     (= selected-resources initial-resources))
                       :on-click on-send}]]
     (if (empty? catalogue)
       [spinner/big]
       ;; TODO: Nowadays the user cannot select resources that have an
       ;;   incompatible form or workflow. Delete extra code here that
       ;;   previously showed a warning if the selected resources were
       ;;   incompatible.
       [:div
        (cond (show-bundling-warning? enriched-selected-resources)
              [bundling-warning enriched-selected-resources can-bundle-all? language]
              (show-change-form-warning? original-form-id enriched-selected-resources)
              [change-workflow-warning enriched-selected-resources can-bundle-all? language]
              (show-change-workflow-warning? original-workflow-id enriched-selected-resources)
              [change-form-warning enriched-selected-resources can-bundle-all? language])
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
           :item-disabled? #(not (compatible-item? % enriched-selected-resources original-workflow-id original-form-id))
           :item-label #(get-localized-title % language)
           :item-selected? #(contains? (set selected-resources) (% :id))
           :multi? true
           :on-change on-set-resources}]]])]))

(defn change-resources-form [application can-bundle-all? can-comment? on-finished]
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
                            :can-bundle-all? can-bundle-all?
                            :can-comment? can-comment?
                            :language language
                            :on-set-comment #(rf/dispatch [::set-comment %])
                            :on-set-resources #(rf/dispatch [::set-selected-resources %])
                            :on-send #(rf/dispatch [::send-change-resources {:application-id (:application/id application)
                                                                             :resources selected-resources
                                                                             :comment comment
                                                                             :on-finished on-finished}])}]))
