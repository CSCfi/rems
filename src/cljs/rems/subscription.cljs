(ns rems.subscription
  (:require [re-frame.core :as rf]
            [rems.application]
            [rems.globals]
            [rems.util :refer [fetch]]))

;; A unique identity for each tab/window that is a subscription
(rf/reg-sub ::client-id (fn [db _] (::client-id db)))
(rf/reg-event-db ::set-client-id (fn [db [_ x]] (assoc db ::client-id x)))

;; The current state/result of the connection to the back-end
(rf/reg-sub ::server-connection (fn [db _] (::server-connection db)))
(rf/reg-event-db ::set-server-connection (fn [db [_ x]] (assoc db ::server-connection x)))

(defn handle-long-poll-result [result]
  (rf/dispatch [::set-server-connection result])

  ;; application (page) refresh
  (when-let [application-update (:application-update result)]
    (rems.application/handle-application-update application-update))

  ;; setup a new poll
  (.setTimeout js/window
               #(rf/dispatch [::long-poll])
               ;; NB: Let's retry with a random delay, because
               ;; all clients will have received some results
               ;; at the same time, so they might end up hitting
               ;; the server again at the same time in the future.
               (+ (rand-int 200) 50)))

(rf/reg-event-fx
 ::long-poll
 (fn [{:keys [db]} _]
   (let [client-id (::client-id db)
         user-id (:userid @rems.globals/user)]

     (if (and client-id user-id)
       ;; for logged-in users only
       (fetch "/api/subscriptions/long-poll"
              {:params (merge {:client-id client-id}
                              (when-let [application-id (:application-id (:page-params db))]
                                {:application-id application-id}))
               :handler handle-long-poll-result
               :error-handler (fn [error]
                                ;; error so let's do a slow retry
                                (rf/dispatch [::set-server-connection {:status :error
                                                                       :error error}])
                                (.setTimeout js/window #(rf/dispatch [::long-poll]) 15000))})

       ;; not logged-in so let's do a slowish retry
       (.setTimeout js/window #(rf/dispatch [::long-poll]) 5000))

     {:db (assoc-in db [::server-connection :status] :fetching)})))

(defn open-server-connection! []
  (let [client-id (random-uuid)]
    (rf/dispatch [::set-client-id client-id])
    (rf/dispatch [::long-poll])))
