(ns rems.settings
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [document-title]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [fetch put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::form)
    :dispatch [::fetch-user-settings]}))

(rf/reg-event-fx
 ::fetch-user-settings
 (fn [{:keys [db]} _]
   (fetch "/api/user-settings"
          {:handler #(rf/dispatch-sync [::set-form (select-keys % [:email])]) ; only the keys that can be edited on this page
           :error-handler (flash-message/default-error-handler :top "Fetch user settings")})
   {:db (assoc db ::form ::loading)}))

(rf/reg-sub
 ::form
 (fn [db] (::form db)))

(rf/reg-event-db
 ::set-form
 (fn [db [_ form]]
   (assoc db ::form form)))

(rf/reg-event-fx
 ::save
 (fn [{:keys [db]} _]
   (let [description [text :t.settings/save]]
     (put! "/api/user-settings"
           {:params (::form db)
            :handler (flash-message/default-success-handler :top description
                                                            #(rf/dispatch [::fetch-user-settings]))
            :error-handler (flash-message/default-error-handler :top description)}))
   {}))

;;;; UI

(defn settings-page []
  (let [identity @(rf/subscribe [:identity])
        form @(rf/subscribe [::form])]
    [:<>
     [document-title (text :t.navigation/settings)]
     [flash-message/component :top]
     (if (= ::loading form)
       [spinner/big]
       [:form
        {:on-submit (fn [event]
                      (.preventDefault event)
                      (rf/dispatch [::save]))}

        (let [id "email"]
          [:div.form-group
           [:label {:for id} (text :t.settings/email)]
           [:input.form-control
            {:type "email"
             :id id
             :value (:email form)
             :placeholder (:email (:user identity))
             :on-change (fn [event]
                          (let [value (.. event -target -value)]
                            (rf/dispatch [::set-form (assoc form :email value)])))}]])

        [:button.btn.btn-primary
         {:type "submit"}
         (text :t.settings/save)]])]))
