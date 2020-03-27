(ns rems.fetcher
  "Helper functions for data fetch requests.

  See `rems.fetcher/reg-fetcher` for more details.

  The state map will contain the following keys:
    :data - the data which was fetched (nil if error or not fetched)
    :fetching? - whether a fetch is in progress
    :initialized? - whether at least one fetch has finished
    :loading? - whether the first fetch is in progress
    :reloading? - whether a subsequent fetch is in progress"
  (:require [re-frame.core :as rf]
            [rems.flash-message :as flash-message]
            [rems.util :refer [fetch]]))

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


(defn reg-fetcher
  "Registers a set of event handlers and subscriptions for fetching data, with
  support for search. Throttles automatically so that only one fetch is running
  at a time.

  Given `(reg-fetcher ::foo \"/url\")`, registers the following:

  - Handler `[::foo]` fetches data from `/url`, i.e. get all items
  - Handler `[::foo {:query \"bar\"]}` fetches data from `/url?query=bar`, i.e. do a search
  - Subscription `[::foo]` returns the data that was last fetched
  - Subscription `[::foo :error]` returns the error message if the fetch failed
  - Subscription `[::foo :initialized?]` returns `true` if the data has been
    fetched at least once
  - Subscription `[::foo :fetching?]` returns `true` if a fetch is in progress
  - Subscription `[::foo :searching?]` returns `true` if a search is in progress,
    i.e. a fetch after the initial fetch

  All state is stored in `db` under `::foo`, which can be removed to reset state.

  Further options can be given as a map `opts`:
  - `:result`     - a transformation function that is applied to each result before storing it
  - `:on-success` - called after successful fetch"
  [id url & [opts]]
  (let [result-id (keyword (namespace id)
                           (str (name id) "-result"))
        result-fn (or (:result opts) identity)
        on-success (or (:on-success opts) (fn [_result]))]
    (rf/reg-event-fx
     id
     (fn [{:keys [db]} [_ query]]
       ;; do only one fetch at a time - will retry after the pending fetch is finished
       (when-not (get-in db [id :fetching?])
         (fetch url
                {:url-params query
                 :handler #(rf/dispatch [result-id {:data %} query])
                 :error-handler (fn [response]
                                  ;; We use a custom error reporting mechanism instead of flash-message
                                  ;; because grabbing the focus is problematic for continuous search.
                                  (rf/dispatch [result-id
                                                {:error (flash-message/format-response-error response)}
                                                query]))}))
       {:db (-> db
                (assoc-in [id :query] query)
                (assoc-in [id :fetching?] true))}))

    (rf/reg-event-db
     result-id
     (fn [db [_ result query]]
       ;; fetch again if the query that just finished was not the latest
       (let [latest-query (get-in db [id :query])]
         (when-not (= query latest-query)
           (rf/dispatch [id latest-query])))
       (let [result (result-fn (:data result))]
         (on-success result)
         (-> db
             (assoc-in [id :data] result)
             (assoc-in [id :error] (:error result))
             (assoc-in [id :initialized?] true)
             (assoc-in [id :fetching?] false)))))

    (rf/reg-sub
     id
     (fn [db [_ k]]
       (case k
         :searching? (and (get-in db [id :fetching?])
                          (get-in db [id :initialized?]))
         (get-in db [id (or k :data)]))))))
