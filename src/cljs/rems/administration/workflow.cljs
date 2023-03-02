(ns rems.administration.workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [document-title enrich-user readonly-checkbox]]
            [rems.collapsible :as collapsible]
            [rems.common.util :refer [andstr]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [localized text]]
            [rems.util :refer [fetch]]))

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

(defn edit-action [workflow-id]
  (atoms/edit-action
   {:class "edit-workflow"
    :url (str "/administration/workflows/edit/" workflow-id)}))

(defn edit-button [workflow-id]
  [atoms/action-button (edit-action workflow-id)])

(def workflow-types
  {:workflow/default :t.create-workflow/default-workflow
   :workflow/decider :t.create-workflow/decider-workflow
   :workflow/master :t.create-workflow/master-workflow})

(defn workflow-view [workflow language]
  [:div.spaced-vertically-3
   [collapsible/component
    {:id "workflow"
     :title [:span (andstr (get-in workflow [:organization :organization/short-name language]) "/") (:title workflow)]
     :always [:div
              [inline-info-field (text :t.administration/organization) (get-in workflow [:organization :organization/name language])]
              [inline-info-field (text :t.administration/title) (:title workflow)]
              [inline-info-field (text :t.administration/type) (text (get workflow-types
                                                                          (get-in workflow [:workflow :type])
                                                                          :t/missing))]
              [inline-info-field (text :t.create-workflow/handlers) (->> (get-in workflow [:workflow :handlers])
                                                                         (map enrich-user)
                                                                         (map :display)
                                                                         (str/join ", "))]
              [inline-info-field (text :t.administration/active) [readonly-checkbox {:value (status-flags/active? workflow)}]]
              [inline-info-field (text :t.administration/forms)
               (->> (for [form (get-in workflow [:workflow :forms])
                          :let [uri (str "/administration/forms/" (:form/id form))
                                title (:form/internal-name form)]]
                      [atoms/link nil uri title])
                    (interpose ", ")
                    (into [:<>]))]
              [inline-info-field (text :t.administration/licenses)
               (->> (for [license (get-in workflow [:workflow :licenses])
                          :let [uri (str "/administration/licenses/" (:license/id license))
                                title (:title (localized (:localizations license)))]]
                      [atoms/link nil uri title])
                    (interpose ", ")
                    (into [:<>]))]]}]
   (let [id (:id workflow)]
     [:div.col.commands
      [administration/back-button "/administration/workflows"]
      [roles/show-when roles/+admin-write-roles+
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
