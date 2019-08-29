(ns rems.administration.workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.license :refer [licenses-view]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [attachment-link external-link info-field readonly-checkbox enrich-user document-title]]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [andstr]]
            [rems.spinner :as spinner]
            [rems.text :refer [get-localized-title localize-time text text-format]]
            [rems.util :refer [dispatch! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ workflow-id]]
   {:db (assoc db ::loading? true)
    ::fetch-workflow [workflow-id]}))

(defn- fetch-workflow [workflow-id]
  (fetch (str "/api/workflows/" workflow-id)
         {:handler #(rf/dispatch [::fetch-workflow-result %])}))

(rf/reg-fx ::fetch-workflow (fn [[workflow-id]] (fetch-workflow workflow-id)))

(rf/reg-event-db
 ::fetch-workflow-result
 (fn [db [_ workflow]]
   (-> db
       (assoc ::workflow workflow)
       (dissoc ::loading?))))

(rf/reg-sub ::workflow (fn [db _] (::workflow db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn- back-button []
  [atoms/link {:class "btn btn-secondary"}
   "/#/administration/workflows"
   (text :t.administration/back)])

(defn edit-button [id]
  [atoms/link {:class "btn btn-primary"}
   (str "/#/administration/edit-workflow/" id)
   (text :t.administration/edit)])

(defn workflow-view [workflow language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "workflow"
     :title [:span (andstr (:organization workflow) "/") (:title workflow)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (:organization workflow)]
              [inline-info-field (text :t.administration/title) (:title workflow)]
              [inline-info-field (text :t.administration/type)
               (if (:workflow workflow)
                 (text :t.create-workflow/dynamic-workflow)
                 ;; TODO: Not implemented.
                 (text :t.create-workflow/auto-approve-workflow))]
              [inline-info-field (text :t.create-workflow/handlers) (->> (get-in workflow [:workflow :handlers])
                                                                         (map enrich-user)
                                                                         (map :display)
                                                                         (str/join ", "))]
              [inline-info-field (text :t.administration/start) (localize-time (:start workflow))]
              [inline-info-field (text :t.administration/end) (localize-time (:end workflow))]
              [inline-info-field (text :t.administration/active) [readonly-checkbox (status-flags/active? workflow)]]]}]
   [licenses-view (:licenses workflow) language]
   (let [id (:id workflow)]
     [:div.col.commands
      [back-button]
      [edit-button id]
      [status-flags/enabled-toggle workflow #(rf/dispatch [:rems.administration.workflows/update-workflow %1 %2 [::enter-page id]])]
      [status-flags/archived-toggle workflow #(rf/dispatch [:rems.administration.workflows/update-workflow %1 %2 [::enter-page id]])]])])

(defn workflow-page []
  (let [workflow (rf/subscribe [::workflow])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration-navigator-container]
       [document-title (text :t.administration/workflow)]
       (if @loading?
         [spinner/big]
         [workflow-view @workflow @language])])))
