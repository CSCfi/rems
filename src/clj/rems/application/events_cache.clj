(ns rems.application.events-cache
  (:require [rems.db.applications :as applications]))

(defn new []
  (atom {:last-processed-event-id 0
         :state nil}))

(defn refresh!
  "Refreshes the cache with new events and returns the latest state.
   `update-fn` should be a function which takes as parameters the previously
   cached state and a list of new events, and returns the updated state."
  [cache update-fn]
  (let [cached @cache
        new-events (applications/get-dynamic-application-events-since (:last-processed-event-id cached))]
    (if (empty? new-events)
      (:state cached)
      (let [updated {:state (update-fn (:state cached) new-events)
                     :last-processed-event-id (:event/id (last new-events))}]
        ;; with concurrent requests, only one of them will update the cache
        (compare-and-set! cache cached updated)
        (:state updated)))))
