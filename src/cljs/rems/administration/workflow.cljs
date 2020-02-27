(ns rems.administration.workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.license :refer [licenses-view]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [attachment-link external-link info-field readonly-checkbox enrich-user document-title]]
            [rems.collapsible :as collapsible]
            [rems.common.util :refer [andstr]]
            [rems.flash-message :as flash-message]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [get-localized-title text text-format]]
            [rems.util :refer [navigate! fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ workflow-id]]
   {:db (assoc db ::loading? true)
    ::fetch-workflow [workflow-id]}))

(defn- fetch-workflow [workflow-id]
  (fetch (str "/api/workflows/" workflow-id)
         {:handler #(rf/dispatch [::fetch-workflow-result %])
          :error-handler (flash-message/default-error-handler :top "Fetch workflow")}))

(rf/reg-fx ::fetch-workflow (fn [[workflow-id]] (fetch-workflow workflow-id)))

(rf/reg-event-db
 ::fetch-workflow-result
 (fn [db [_ workflow]]
   (-> db
       (assoc ::workflow workflow)
       (dissoc ::loading?))))

(rf/reg-sub ::workflow (fn [db _] (::workflow db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn edit-button [id]
  [atoms/link {:class "btn btn-primary"}
   (str "/administration/workflows/edit/" id)
   (text :t.administration/edit)])

(def workflow-types
  {:workflow/default :t.create-workflow/default-workflow
   :workflow/decider :t.create-workflow/decider-workflow
   :workflow/master :t.create-workflow/master-workflow})

(defn workflow-view [workflow language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "workflow"
     :title [:span (andstr (:organization workflow) "/") (:title workflow)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (:organization workflow)]
              [inline-info-field (text :t.administration/title) (:title workflow)]
              [inline-info-field (text :t.administration/type) (text (get workflow-types
                                                                          (get-in workflow [:workflow :type])
                                                                          :t/missing))]
              [inline-info-field (text :t.create-workflow/handlers) (->> (get-in workflow [:workflow :handlers])
                                                                         (map enrich-user)
                                                                         (map :display)
                                                                         (str/join ", "))]
              [inline-info-field (text :t.administration/active) [readonly-checkbox {:value (status-flags/active? workflow)}]]]}]
   [licenses-view (:licenses workflow) language]
   (let [id (:id workflow)]
     [:div.col.commands
      [administration/back-button "/administration/workflows"]
      [roles/when roles/show-admin-edit-buttons?
       [edit-button id]
       [status-flags/enabled-toggle workflow #(rf/dispatch [:rems.administration.workflows/set-workflow-enabled %1 %2 [::enter-page id]])]
       [status-flags/archived-toggle workflow #(rf/dispatch [:rems.administration.workflows/set-workflow-archived %1 %2 [::enter-page id]])]]])])

(defn workflow-page []
  (let [workflow (rf/subscribe [::workflow])
        language (rf/subscribe [:language])
        loading? (rf/subscribe [::loading?])]
    (fn []
      [:div
       [administration/navigator]
       [document-title (text :t.administration/workflow)]
       [flash-message/component :top]
       (if @loading?
         [spinner/big]
         [workflow-view @workflow @language])])))
