(ns rems.fetcher
  "Helper functions for tracking the state of a data fetch request.

  The state map will contain the following keys:
    :data - the data which was fetched (nil if error or not fetched)
    :fetching? - whether a fetch is in progress
    :initialized? - whether at least one fetch has finished
    :loading? - whether the first fetch is in progress
    :reloading? - whether a subsequent fetch is in progress")

(defn- update-derived-state [state]
  (let [{:keys [initialized? fetching?]} state]
    (assoc state
           :loading? (and fetching? (not initialized?))
           :reloading? (and fetching? initialized?))))

(defn started [state]
  (-> state
      (assoc :fetching? true)
      update-derived-state))

(defn finished [state data]
  (-> state
      (assoc :data data
             :initialized? true
             :fetching? false)
      update-derived-state))
