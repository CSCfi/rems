(ns rems.search
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.atoms :refer [close-symbol]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]))

(defn reg-fetcher
  "Registers a set of event handlers and subscriptions for fetching data, with
  support for search. Throttles automatically so that only one fetch is running
  at a time.

  Given `(reg-fetcher ::foo \"/url\")`, registers the following:

  - Handler `[::foo]` fetches data from `/url`, i.e. get all items
  - Handler `[::foo \"bar\"]` fetches data from `/url?query=bar`, i.e. do a search
  - Subscription `[::foo]` returns the data that was last fetched
  - Subscription `[::foo :initialized?]` returns `true` if the data has been
    fetched at least once
  - Subscription `[::foo :fetching?]` returns `true` if a fetch is in progress
  - Subscription `[::foo :searching?]` returns `true` if a search is in progress,
    i.e. a fetch after the initial fetch

  All state is stored in `db` under `::foo`, which can be removed to reset state."
  [id url]
  (let [result-id (keyword (namespace id)
                           (str (name id) "-result"))]
    (rf/reg-event-fx
     id
     (fn [{:keys [db]} [_ query]]
       ;; do only one fetch at a time - will retry after the pending fetch is finished
       (when-not (get-in db [id :fetching?])
         (fetch url
                {:url-params (when query
                               {:query query})
                 :handler #(rf/dispatch [result-id % query])
                 ;; TODO: no title for the error modal. That's fine since this will only fail very exceptionally.
                 :error-handler #(do
                                   (status-modal/common-error-handler! %)
                                   (rf/dispatch [result-id nil query]))}))
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
       (-> db
           (assoc-in [id :data] result)
           (assoc-in [id :initialized?] true)
           (assoc-in [id :fetching?] false))))

    (rf/reg-sub
     id
     (fn [db [_ k]]
       (case k
         :searching? (and (get-in db [id :fetching?])
                          (get-in db [id :initialized?]))
         (get-in db [id (or k :data)]))))))

(defn search-field [{:keys [id on-search searching?]}]
  (let [input-value (r/atom "")
        input-element (atom nil)]
    (fn [{:keys [id on-search searching?]}]
      [:div.search-field.mb-3
       [:label.mr-1 {:for id}
        (text :t.search/search)]

       [:div.input-group.mr-2
        [:input.form-control
         {:id id
          :type :text
          :value @input-value
          :ref (fn [element]
                 (reset! input-element element))
          :on-change (fn [event]
                       (let [value (-> event .-target .-value)]
                         (reset! input-value value)
                         (on-search value)))}]

        (when-not (= "" @input-value)
          [:div.input-group-append
           [:button.btn.btn-outline-secondary
            {:id (str id "-clear")
             :type :button
             :aria-label (text :t.search/clear-search)
             ;; override the custom font-size from .btn which breaks .input-group
             :style {:font-size "inherit"}
             :on-click (fn []
                         (reset! input-value "")
                         (on-search "")
                         (.focus @input-element))}
            [close-symbol]]])]

       (when searching?
         [spinner/small])])))
