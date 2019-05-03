(ns rems.application.events-cache
  (:require [conman.core :as conman]
            [rems.db.events :as events]
            [rems.db.core :refer [*db*]]
            [rems.util :refer [atom?]]))

(def ^:private empty-cache
  ;; TODO: consider refactoring opportunities with pollers
  {:last-processed-event-id 0
   :state nil})

(defn new []
  (atom empty-cache))

(defn- update-with-new-events [cached update-fn]
  (conman/with-transaction [*db* {:isolation :serializable
                                  :read-only? true}]
    (let [new-events (events/get-all-events-since (:last-processed-event-id cached))]
      (if (empty? new-events)
        cached
        {:state (update-fn (:state cached) new-events)
         :last-processed-event-id (:event/id (last new-events))}))))

(defn refresh!
  "Refreshes the cache with new events and returns the latest state.
   `update-fn` should be a function which takes as parameters the previously
   cached state and a list of new events, and returns the updated state."
  [cache update-fn]
  (let [cache-enabled? (atom? cache)
        cached (if cache-enabled? @cache empty-cache)
        updated (update-with-new-events cached update-fn)]
    (when (and cache-enabled?
               (not (identical? cached updated)))
      ;; with concurrent requests, only one of them will update the cache
      (compare-and-set! cache cached updated))
    (:state updated)))

(defn empty! [cache]
  (when (atom? cache)
    (reset! cache empty-cache)))
