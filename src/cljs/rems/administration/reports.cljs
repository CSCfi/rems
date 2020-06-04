(ns rems.administration.reports
  "Admin page for reports."
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
  [atoms/link {:id :export-applications-button
               :class "btn btn-primary"}
   "/administration/reports/export-applications"
   (text :t.administration/export-applications)])

(defn reports []
  [export-applications-button])

(defn reports-page []
  [:div
   [administration/navigator]
   [atoms/document-title (text :t.administration/reports)]
   [flash-message/component :top]
   [reports]])
