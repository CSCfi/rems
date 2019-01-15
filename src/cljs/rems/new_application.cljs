(ns rems.new-application
  (:require [re-frame.core :as rf]
            [rems.application :refer [navigate-to]]
            [rems.spinner :as spinner]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [text text-format]]
            [rems.util :refer [fetch post!]]))

(rf/reg-event-db
 ::set-status
 (fn [db [_ {:keys [status description error] :as state}]]
   (assoc db ::status state)))

(rf/reg-sub ::status (fn [db _] (::status db)))

(defn remove-catalogue-items-from-cart! [catalogue-item-ids]
  (doseq [i catalogue-item-ids]
    (rf/dispatch [:rems.cart/remove-item i])))

(defn- save-application [description catalogue-item-ids on-success]
  (post! "/api/applications/save"
         {:handler (fn [response]
                     (if (:success response)
                       (on-success response)
                       (rf/dispatch [::set-status {:status :failed
                                                   :description description}])))
          :error-handler (fn [error]
                           (rf/dispatch [::set-status {:status :failed
                                                       :description description
                                                       :error error}]))
          :params {:command "save"
                   :items {}
                   :licenses {}
                   :catalogue-items catalogue-item-ids}}))

(rf/reg-fx ::save-application (fn [params] (apply save-application params)))

(rf/reg-event-fx
 ::fetch-draft-application-result
 (fn [{:keys [db]} [_ application]]
   ;; immediately dispatch save of draft on entry
   (let [catalogue-item-ids (mapv :id (:catalogue-items application))]
     {::save-application [(text :t.form/save)
                          catalogue-item-ids
                          (fn [response]
                            (remove-catalogue-items-from-cart! catalogue-item-ids)
                            (navigate-to (:id response) true))]})))

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
  (let [status @(rf/subscribe [::status])]
    [:div
     [:h2 (text :t.applications/application)]
     (if status
       [status-modal (select-keys status [:status :description :error])]
       [spinner/big])]))
