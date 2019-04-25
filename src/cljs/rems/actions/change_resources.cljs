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

(defn change-resources-view
  [{:keys [initial-resources selected-resources catalogue comment can-comment? language on-set-comment on-add-resources on-remove-resource on-send]}]
  [action-form-view action-form-id
   (text :t.actions/change-resources)
   [[button-wrapper {:id "change-resources"
                     :text (text :t.actions/change-resources)
                     :class "btn-primary"
                     :disabled (= selected-resources initial-resources)
                     :on-click on-send}]]
   (let [indexed-resources (index-by [:id] catalogue)]
     (if (empty? catalogue)
       [spinner/big]
       [:div
        (when can-comment?
          [action-comment {:id action-form-id
                           :label (text :t.form/add-comments-shown-to-applicant)
                           :comment comment
                           :on-comment on-set-comment}])
        [:div.form-group
         [:label (text :t.actions/resources-selection)]
         [autocomplete/component
          {:value (sort-by #(get-catalogue-item-title % language)
                           (vals (select-keys indexed-resources
                                              selected-resources)))
           :items (vals (apply dissoc indexed-resources selected-resources))
           :value->text #(get-catalogue-item-title %2 language)
           :item->key :id
           :item->text #(get-catalogue-item-title % language)
           :item->value identity
           :term-match-fn (partial resource-matches? language)
           :add-fn on-add-resources
           :remove-fn on-remove-resource}]]]))])

(defn change-resources-form [application-id can-comment? on-finished]
  (let [initial-resources @(rf/subscribe [::initial-resources])
        selected-resources @(rf/subscribe [::selected-resources])
        catalogue @(rf/subscribe [:rems.catalogue/full-catalogue])
        comment @(rf/subscribe [::comment])
        language @(rf/subscribe [:language])]
    [change-resources-view {:initial-resources initial-resources
                            :selected-resources selected-resources
                            :catalogue catalogue
                            :comment comment
                            :can-comment? can-comment?
                            :language language
                            :on-set-comment #(rf/dispatch [::set-comment %])
                            :on-add-resources #(rf/dispatch [::add-selected-resources %])
                            :on-remove-resource #(rf/dispatch [::remove-selected-resource %])
                            :on-send #(rf/dispatch [::send-change-resources {:application-id application-id
                                                                             :resources selected-resources
                                                                             :comment comment
                                                                             :on-finished on-finished}])}]))
