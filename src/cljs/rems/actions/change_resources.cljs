(ns rems.actions.change-resources
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment button-wrapper collapse-action-form]]
            [rems.autocomplete :as autocomplete]
            [rems.catalogue-util :refer [get-catalogue-item-title]]
            [rems.common-util :refer [index-by]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
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

(defn resource-matches? [language resource query]
  (-> (get-catalogue-item-title resource language)
      .toLowerCase
      (.indexOf query)
      (not= -1)))

(rf/reg-sub ::initial-resources (fn [db _] (::initial-resources db)))
(rf/reg-sub ::selected-resources (fn [db _] (::selected-resources db)))
(rf/reg-event-db ::set-selected-resources (fn [db [_ resources]] (assoc db ::selected-resources resources)))
(rf/reg-event-db ::add-selected-resources (fn [db [_ resource]] (update db ::selected-resources conj (:id resource))))
(rf/reg-event-db ::remove-selected-resource (fn [db [_ resource]] (update db ::selected-resources disj (:id resource))))

(rf/reg-sub ::comment (fn [db _] (::comment db)))
(rf/reg-event-db ::set-comment (fn [db [_ value]] (assoc db ::comment value)))

(def ^:private action-form-id "change-resources")

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
            [:li (str/join ", " (map #(get-catalogue-item-title % language) group))]))]])

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
            [:li (str/join ", " (map #(get-catalogue-item-title % language) group))]))]])

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
            [:li (str/join ", " (map #(get-catalogue-item-title % language) group))]))]])

(defn compatible-item? [item resources original-workflow-id original-form-id]
  (not (or (show-bundling-warning? (conj resources item))
           (show-change-form-warning? original-form-id (conj resources item))
           (show-change-workflow-warning? original-workflow-id (conj resources item)))))

(defn change-resources-view
  [{:keys [application initial-resources selected-resources full-catalogue catalogue comment can-bundle-all? can-comment? language on-set-comment on-add-resources on-remove-resource on-send]}]
  (let [indexed-resources (index-by [:id] full-catalogue)
        enriched-selected-resources (->> selected-resources
                                         (select-keys indexed-resources)
                                         vals
                                         (sort-by #(get-catalogue-item-title % language)))
        original-form-id (get-in application [:application/form :form/id])
        original-workflow-id (get-in application [:application/workflow :workflow/id])]
    [action-form-view action-form-id
     (text :t.actions/change-resources)
     [[button-wrapper {:id "change-resources"
                       :text (text :t.actions/change-resources)
                       :class "btn-primary"
                       :disabled (or (and (not can-bundle-all?)
                                          (or (show-bundling-warning? enriched-selected-resources) )
                                          (show-change-form-warning? original-form-id enriched-selected-resources)
                                          (show-change-workflow-warning? original-workflow-id enriched-selected-resources))
                                     (empty? selected-resources)
                                     (= selected-resources initial-resources))
                       :on-click on-send}]]
     (if (empty? catalogue)
       [spinner/big]
       [:div
        (cond (show-bundling-warning? enriched-selected-resources)
              [bundling-warning enriched-selected-resources can-bundle-all? language]
              (show-change-form-warning? original-form-id  enriched-selected-resources)
              [change-workflow-warning enriched-selected-resources can-bundle-all? language]
              (show-change-workflow-warning? original-workflow-id enriched-selected-resources)
              [change-form-warning enriched-selected-resources can-bundle-all? language])
        (when can-comment?
          [action-comment {:id action-form-id
                           :label (text :t.form/add-comments-shown-to-applicant)
                           :comment comment
                           :on-comment on-set-comment}])
        [:div.form-group
         [:label (text :t.actions/resources-selection)]
         [autocomplete/component
          {:value enriched-selected-resources
           :items catalogue
           :value->text #(get-catalogue-item-title %2 language)
           :item->key :id
           :item->text (fn [item]
                         [:span (when-not (or can-bundle-all?
                                              (compatible-item? item enriched-selected-resources original-workflow-id original-form-id))
                                  {:class :text-danger})
                          (get-catalogue-item-title item language)])
           :item->value identity
           :term-match-fn (partial resource-matches? language)
           :add-fn on-add-resources
           :remove-fn on-remove-resource}]]])]))

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
                            :on-add-resources #(rf/dispatch [::add-selected-resources %])
                            :on-remove-resource #(rf/dispatch [::remove-selected-resource %])
                            :on-send #(rf/dispatch [::send-change-resources {:application-id (:application/id application)
                                                                             :resources selected-resources
                                                                             :comment comment
                                                                             :on-finished on-finished}])}]))
