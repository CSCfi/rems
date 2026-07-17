(ns rems.new-application
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [document-title]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [post! replace-url!]]))

(defn remove-catalogue-items-from-cart! [catalogue-item-ids]
  (doseq [id catalogue-item-ids]
    (rf/dispatch [:rems.cart/remove-item id])))

(rf/reg-event-fx
 ::enter-new-application-page
 (fn [_ [_ catalogue-item-ids]]
   (post! "/api/applications/create"
          {:params {:catalogue-item-ids catalogue-item-ids}
           :handler (fn [{:keys [application-id
                                 success
                                 errors]
                          :as _response}]
                      (cond
                        success
                        (do
                          (remove-catalogue-items-from-cart! catalogue-item-ids)
                          (replace-url! (str "/application/" application-id)))

                        errors
                        (do
                          (replace-url! "/catalogue")
                          (js/console.log errors)
                          (flash-message/show-error! :top (->> errors
                                                               (mapv (flash-message/argumentize-some-key :catalogue-item-id :catalogue-item-ids))
                                                               flash-message/format-errors)))))
           :error-handler (fn [response]
                            ((flash-message/default-error-handler :top [text :t.applications/application]) response)
                            (replace-url! "/catalogue"))})
   {}))

(defn new-application-page []
  [:div
   [document-title (text :t.applications/application)]
   [flash-message/component :top]
   [spinner/big]])
