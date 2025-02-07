(ns rems.subscriptions
  (:require [clj-time.core :as time]
            [clojure.set]
            [medley.core :refer [remove-vals]]
            [rems.db.users :as users]
            [rems.common.util :refer [getx getx-in]]))

(def subscribers-a (atom {}))

(defn- application-subscribers-timeout!
  "Check which subscriptions have timed out and remove them."
  []
  (let [too-old (time/minus (time/now) (time/minutes 1))]
    (swap! subscribers-a
           update
           :clients
           #(remove-vals (fn [{:keys [last-seen]}]
                           (or (not last-seen)
                               (time/before? last-seen too-old))) %))))

(defn notify-update
  "Notifies any possibly listening clients the application state has changed."
  [message]

  ;; notify specific subscribers
  (when-let [application-id (get-in message [:application-update :application :application/id])]
    (let [subscribers @subscribers-a]
      (doseq [[_client-id client-state] (get-in subscribers [:clients])
              :when (= (:application-id client-state) application-id)
              :let [q (:queue client-state)]
              :when q]

        ;; we only offer a value so that we don't block
        (.offer q message)))))

(defn get-application-subscribed-clients
  "Calculate visible `subscribers` for given `application-id`."
  [application-id]
  (vec (for [[client-id client-state] (:clients @subscribers-a)
             :when (= (:application-id client-state) application-id)]
         (remove-vals nil? {:client-id client-id
                            :user (:user client-state)
                            :form-id (:form-id client-state)
                            :field-id (:field-id client-state)}))))

(defn update-subscriber! [client]
  (swap! subscribers-a
         (fn [subscribers]
           (-> subscribers
               (update-in [:clients (:client-id client)]
                          (fn [old-client]
                            (-> old-client
                                (merge {:last-seen (time/now)})
                                (merge client)
                                (->> (remove-vals nil?)))))))))
(comment
  (application-subscribers-timeout!)
  (get-application-subscribed-clients 29))

;;(assert (get-application-for-user user-id application-id)) ; check the user has rights
;;(notify-update {:application {:application/id application-id}}) ; let everyone know of the new user

(defn long-poll [client-id user-id application-id]
  ;; fetch existing client data
  (let [existing-client-state (get-in @subscribers-a [:clients client-id])
        queue-capacity 10 ; allow some values to queue up but not much
        queue (if existing-client-state
                (do (assert (= user-id (getx-in existing-client-state [:user :userid]))) ; don't let other users in even with right client-id
                    (getx existing-client-state :queue))

                ;; no existing state, a new subscription
                ;; the first poll creates the queue
                (new java.util.concurrent.ArrayBlockingQueue queue-capacity))]

    ;; refresh server state about client
    (swap! subscribers-a
           (fn [subscribers]
             (-> subscribers
                 (update-in [:clients client-id]
                            merge
                            {:last-seen (time/now)
                             :user (users/get-user user-id)
                             :application-id application-id
                             :queue queue}))))

    ;; cleanup
    (application-subscribers-timeout!)

    ;; return results or wait
    (merge
     ;; constant data
     {:status :all-quiet
      :client-id client-id
      :user-id user-id}

     ;; command data
     (if existing-client-state ; NB: this means the first request returns immediately

       (when-some [message (.poll queue 15 java.util.concurrent.TimeUnit/SECONDS)]

         ;; subscribed application has been updated
         (when (:application-update message)
           {:status :updated
            :application-update (let [{:keys [application command]} (:application-update message)]
                                  (merge
                                   ;; always provide id and update clients
                                   {:application-id application-id
                                    :clients (get-application-subscribed-clients application-id)}

                                   ;; if command happened
                                   (when (and application command)
                                     (merge (if (= :application.command/save-draft  (:type command))
                                              (merge {:field-values (mapv #(select-keys % [:form :field :value]) (:field-values command))}

                                                     (when-let [attachments (:application/attachments application)]
                                                       {:application/attachments attachments}))

                                              {:full-reload false})))))}))

       ;; initial request to an application returns base info
       (when application-id
         {:status :updated
          :application-update {:application-id application-id
                               :clients (get-application-subscribed-clients application-id)}})))))
