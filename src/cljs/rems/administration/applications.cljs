(ns rems.administration.applications
  "Admin page for applications."
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.atoms :as atoms]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch [:rems.administration.administration/remember-current-page]}))

(defn- export-applications-button []
  [atoms/link {:class "btn btn-primary"}
   "/administration/applications/export"
   (text :t.administration/export-applications)])

(defn admin-applications []
  [export-applications-button])

(defn admin-applications-page []
  [:div
   [administration/navigator]
   [atoms/document-title (text :t.administration/applications)]
   [flash-message/component :top]
   [admin-applications]])
