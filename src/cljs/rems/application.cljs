(ns rems.application
   (:require [ajax.core :refer [GET]]
             [re-frame.core :as rf]))

(rf/reg-event-fx
 ::start-fetch-application
 (fn [coeff [_ id]]
   {::fetch-application [(get-in coeff [:db :user]) id]}))

(defn- fetch-application [user id]
  (GET (str "/api/application/" id) {:handler #(rf/dispatch [::fetch-application-result %])
                                     :response-format :json
                                     :headers {"x-rems-user-id" (:eppn user)}
                                     :keywords? true}))

(rf/reg-fx
 ::fetch-application
 (fn [[user id]]
   (fetch-application user id)))

(rf/reg-event-db
 ::fetch-application-result
 (fn [db [_ application]]
   (assoc db :application application)))

(defn- render-application [application]
  [:pre (with-out-str (cljs.pprint/pprint application))])

(defn- show-application []
  (if-let [application @(rf/subscribe [:application])]
    (render-application application)
    [:p "No application loaded"]))

(defn application-page []
  (show-application))
