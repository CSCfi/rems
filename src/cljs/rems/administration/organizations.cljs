(ns rems.administration.organizations
  "Admin page for organizations."
  (:require [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.atoms :as atoms]
            [rems.flash-message :as flash-message]
            [rems.text :refer [text]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [_db]}]
   {:dispatch [:rems.administration.administration/remember-current-page]}))

(defn organizations-page []
  [:div
   [administration/navigator]
   [atoms/document-title (text :t.administration/organizations)]
   [flash-message/component :top]])
