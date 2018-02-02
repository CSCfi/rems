(ns rems.application
   (:require [ajax.core :refer [GET]]
             [re-frame.core :as rf]))

(defn- fetch-application [user id]
  (GET (str "/api/application/" id) {:handler #(rf/dispatch [:application %])
                                     :response-format :json
                                     :headers {"x-rems-user-id" (:eppn user)}
                                     :keywords? true}))

(defn render-application [application]
  [:pre (with-out-str (cljs.pprint/pprint application))])

(defn show-application []
  (if-let [application @(rf/subscribe [:application])]
    (render-application application)
    [:p "No application loaded"]))

(defn application-page []
  (let [user @(rf/subscribe [:user])]
    (fetch-application user 1))
  (show-application))
