(ns rems.profile
  (:require [re-frame.core :as rf]
            [rems.atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.flash-message :as flash-message]
            [rems.fetcher :as fetcher]
            [rems.guide-util :refer [component-info example]]
            [rems.globals]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.user :as user]
            [rems.util :refer [put!]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::form)
    :dispatch [::user-settings]}))

(defn- fetch-user-settings-success [result]
  ;; select only the keys that can be edited on this page
  (rf/dispatch [::set-form (select-keys result [:notification-email])])
  ;; update user settings in SPA state after user has changed them
  (rf/dispatch [:loaded-user-settings result]))

(fetcher/reg-fetcher ::user-settings "/api/user-settings" {:on-success fetch-user-settings-success})

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
   (let [description [text :t.profile/save]]
     (put! "/api/user-settings/edit"
           {:params (::form db)
            :handler (flash-message/default-success-handler :top description
                                                            #(rf/dispatch [::user-settings]))
            :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(rf/reg-sub
 ::missing-email?
 (fn [db _]
   (let [user @rems.globals/user]
     (and user
          (not (:email user))
          (not (:notification-email (:user-settings db)))))))

;;;; UI

(defn- missing-email-warning-dialog []
  [:div.alert.alert-warning
   (text :t.profile/warning-about-missing-email)])

(defn missing-email-warning []
  (when @(rf/subscribe [::missing-email?])
    [missing-email-warning-dialog]))

(defn- user-settings []
  (let [form @(rf/subscribe [::form])]
    [collapsible/component
     {:title (text :t.profile/settings)
      :always (if @(rf/subscribe [::user-settings :fetching?])
                [spinner/big]
                [:form
                 {:on-submit (fn [event]
                               (.preventDefault event)
                               (rf/dispatch [::save]))}

                 [:div.form-group
                  (text :t.profile/idp-email) ": " (or (:email @rems.globals/user)
                                                       [:span.text-muted (text :t.profile/no-email)])]

                 (let [id "notification-email"]
                   [:div.form-group
                    [:label {:for id} (text :t.profile/notification-email) ":"]
                    [:input.form-control
                     {:type "email"
                      :id id
                      :value (:notification-email form)
                      :on-change (fn [event]
                                   (let [value (.. event -target -value)]
                                     (rf/dispatch [::set-form (assoc form :notification-email value)])))}]])

                 [:button.btn.btn-primary
                  {:type "submit"}
                  (text :t.profile/save)]])}]))

(defn- user-details []
  [collapsible/component
   {:title (text :t.profile/your-details)
    :always [:<>
             [user/username @rems.globals/user]
             [user/attributes @rems.globals/user false]]}])

(defn profile-page []
  [:<>
   [document-title (text :t.navigation/profile)]
   [flash-message/component :top]
   [:div.spaced-vertically-3
    [user-settings]
    [user-details]]])

(defn guide []
  [:div
   (component-info missing-email-warning-dialog)

   (example "warning message"
            [missing-email-warning-dialog])])
