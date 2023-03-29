(ns rems.search
  (:require [clojure.string :as str]
            [goog.functions :refer [debounce]]
            [reagent.core :as r]
            [rems.atoms :refer [close-symbol]]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [fetch focus-when-collapse-opened]]))

(defn search-field [{:keys [id on-search searching? info]}]
  (let [input-value (r/atom "")
        input-element (atom nil)
        collapse-id (str "application-search-tips-collapse-" id)]
    (fn [{:keys [id on-search searching? info]}]
      (let [on-search (debounce on-search 500)]
        [:<>
         [:div.search-field.mt-3
          [:label.mr-3 {:for id}
           (text :t.search/search)]

          [:div.input-group.mr-2.w-50
           [:input.form-control
            {:id id
             :type :text
             :value @input-value
             :ref (fn [element]
                    (reset! input-element element))
             :on-change (fn [event]
                          (let [value (-> event .-target .-value)
                                value (if (string? value)
                                        (str/triml value)
                                        value)]
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
           [:div.search-tips.collapse.my-3 {:id collapse-id
                                            :ref focus-when-collapse-opened
                                            :tab-index "-1"}
            info])]))))

(defn- application-search-info [] ; TODO: this should probably be almost completely in localized text
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
