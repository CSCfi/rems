(ns rems.search
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [rems.atoms :refer [close-symbol]]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [fetch focus-when-collapse-opened]]))

(defn reg-fetcher
  "Registers a set of event handlers and subscriptions for fetching data, with
  support for search. Throttles automatically so that only one fetch is running
  at a time.

  Given `(reg-fetcher ::foo \"/url\")`, registers the following:

  - Handler `[::foo]` fetches data from `/url`, i.e. get all items
  - Handler `[::foo \"bar\"]` fetches data from `/url?query=bar`, i.e. do a search
  - Subscription `[::foo]` returns the data that was last fetched
  - Subscription `[::foo :error]` returns the error message if the fetch failed
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
       (-> db
           (assoc-in [id :data] (:data result))
           (assoc-in [id :error] (:error result))
           (assoc-in [id :initialized?] true)
           (assoc-in [id :fetching?] false))))

    (rf/reg-sub
     id
     (fn [db [_ k]]
       (case k
         :searching? (and (get-in db [id :fetching?])
                          (get-in db [id :initialized?]))
         (get-in db [id (or k :data)]))))))

(defn search-field [{:keys [id on-search searching? info]}]
  (let [input-value (r/atom "")
        input-element (atom nil)
        collapse-id "application-search-tips-collapse"]
    (fn [{:keys [id on-search searching? info]}]
      [:<>
       [:div.search-field
        [:label.mr-1 {:for id}
         (text :t.search/search)]

        [:div.input-group.mr-2.w-50
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
        (when info
          [:a.application-search-tips.btn.btn-link.collapsed
           {:data-toggle "collapse"
            :href (str "#" collapse-id)
            :aria-label (text :t.search/example-searches)
            :aria-expanded "false"
            :aria-controls collapse-id}
           [:i.fa.fa-question-circle]])
        (when searching?
          [spinner/small])]
       (when info
         [:div.search-tips.collapse {:id collapse-id
                                     :ref focus-when-collapse-opened
                                     :tab-index "-1"}
          info])])))

(defn- application-search-info []
  [:span
   (text :t.search/example-searches)
   ": "
   (->> ["supercalifra*" "+egg +bacon -spam" "id:\"2019/12\"" "applicant:\"alice@example.com\""
         "resource:\"urn:fi:abcd\""]
        (map (fn [example] [:tt.example-search example]))
        (interpose ", ")
        (into [:<>]))
   " "
   [:a {:href "https://github.com/CSCfi/rems/blob/master/docs/search.md"}
    (text :t.search/learn-more)]])

(defn application-search-field [opts]
  [search-field (assoc opts :info [application-search-info])])
