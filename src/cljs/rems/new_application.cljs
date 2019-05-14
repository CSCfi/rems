(ns rems.new-application
  (:require [re-frame.core :as rf]
            [rems.application :as application]
            [rems.atoms :refer [document-title]]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [post!]]))

(defn remove-catalogue-items-from-cart! [catalogue-item-ids]
  (doseq [id catalogue-item-ids]
    (rf/dispatch [:rems.cart/remove-item id])))

(rf/reg-event-fx
 ::enter-new-application-page
 (fn [{:keys [db]} [_ catalogue-item-ids]]
   (post! "/api/applications/create"
          {:params {:catalogue-item-ids catalogue-item-ids}
           :handler (fn [response]
                      (remove-catalogue-items-from-cart! catalogue-item-ids)
                      (application/navigate-to (:application-id response) true))})
   {}))

(defn new-application-page []
  [:div
   [:h1 [document-title (text :t.applications/application)]]
   [spinner/big]])
