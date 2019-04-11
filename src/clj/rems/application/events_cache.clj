(ns rems.application.events-cache
  (:require [rems.db.applications :as applications]))

(defn new []
  (atom {:last-processed-event-id 0
         :state nil}))

(defn refresh! [cache f]
  (let [cached @cache
        new-events (applications/get-dynamic-application-events-since (:last-processed-event-id cached))]
    (if (empty? new-events)
      (:state cached)
      (let [updated {:state (f (:state cached) new-events)
                     :last-processed-event-id (:event/id (last new-events))}]
        ;; with concurrent requests, only one of them will update the cache
        (compare-and-set! cache cached updated)
        (:state updated)))))
