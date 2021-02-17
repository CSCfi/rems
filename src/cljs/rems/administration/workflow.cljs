(ns rems.administration.workflow
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [inline-info-field]]
            [rems.administration.license :refer [licenses-view]]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :as atoms :refer [document-title enrich-user readonly-checkbox]]
            [rems.collapsible :as collapsible]
            [rems.common.util :refer [andstr]]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
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

(defn edit-button [id]
  [atoms/link {:class "btn btn-primary edit-workflow"}
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
               (into [:ul.list-group]
                     (for [form (get-in workflow [:workflow :forms])]
                       [:li.list-group-item [atoms/link nil (str "/administration/forms/" (:form/id form)) (:form/internal-name form)]]))
               {:box? false}]]}]
   [licenses-view (:licenses workflow) language]
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
