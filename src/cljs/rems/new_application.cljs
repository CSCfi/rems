(ns rems.new-application
  (:require [re-frame.core :as rf]
            [rems.application :refer [navigate-to]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text text-format]]
            [rems.util :refer [dispatch! fetch post!]]))

(defn remove-catalogue-items-from-cart! [catalogue-item-ids]
  (doseq [i catalogue-item-ids]
    (rf/dispatch [:rems.cart/remove-item i])))

(defn- save-application [[description catalogue-item-ids on-success]]
  (post! "/api/applications/save"
         {:handler (fn [response]
                     (if (:success response)
                       (on-success response)
                       (status-modal/set-error! {:result response})))
          :error-handler #(status-modal/set-error! {:result {:error %}})
          :params {:command "save"
                   :items {}
                   :licenses {}
                   :catalogue-items catalogue-item-ids}}))

(rf/reg-fx ::save-application save-application)

(rf/reg-event-fx
 ::fetch-draft-application-result
 (fn [{:keys [db]} [_ application]]
   ;; immediately dispatch save of draft on entry
   (status-modal/set-pending! {:open? false
                               :on-close #(dispatch! "#/catalogue")
                               :title (text :t.applications/application)})
   (let [catalogue-item-ids (mapv :id (:catalogue-items application))
         on-success (fn [response]
                      (remove-catalogue-items-from-cart! catalogue-item-ids)
                      (navigate-to (:id response) true))]
     {::save-application [(text :t.form/save)
                          catalogue-item-ids
                          on-success]})))

(rf/reg-event-fx
 ::enter-new-application-page
 (fn [{:keys [db]} [_ items]]
   {::fetch-draft-application items}))

(rf/reg-fx
 ::fetch-draft-application
 (fn [items]
   (fetch (str "/api/applications/draft")
          {:handler #(rf/dispatch [::fetch-draft-application-result %])
           :params {:catalogue-items items}})))

(defn new-application-page []
  [:div
   [:h2 (text :t.applications/application)]
   [spinner/big]])
