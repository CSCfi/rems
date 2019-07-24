(ns rems.user-settings
  (:require [re-frame.core :as rf :refer [reg-event-fx reg-event-db]]
            [rems.language :as language]
            [rems.status-modal :as status-modal]
            [rems.util :refer [fetch put!]]))

(reg-event-fx
 :loaded-user-settings
 (fn [{:keys [db]} [_ user-settings]]
   (language/update-language (:language user-settings))
   {:db (assoc db :user-settings user-settings)}))

(defn fetch-user-settings! []
  (fetch "/api/user-settings" {:handler #(rf/dispatch [:loaded-user-settings %])}))

(reg-event-fx
 ::update-user-settings
 (fn [{:keys [db]} [_ user-id settings]]
   (put! "/api/user-settings/update"
         {:params (merge settings {:user-id user-id})
          :handler (fn [_] (fetch-user-settings!))
          :error-handler status-modal/common-error-handler!})))
