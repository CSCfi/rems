(ns rems.paging
  (:require [clojure.string :as str]
            [goog.functions :refer [debounce]]
            [reagent.core :as r]
            [rems.atoms :as atoms]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [fetch focus-when-collapse-opened]]))

(defn paging-field [{:keys [id on-change paging pages]}]
  [:div.d-flex.gap-1.justify-content-center.flex-wrap.mr-3.my-3 {:id id}
   (text :t.table.paging/page)
   (for [page (range pages)]
     (if (= (:current-page paging) page)
       ^{:key (str id "-page-" page)} [:span (str (inc page))]
       ^{:key (str id "-page-" page)} [atoms/link {:href ""
                                                   :label (str (inc page))
                                                   :on-click #(on-change (assoc paging :current-page page))}]))]
  #_(let [input-value (r/atom "")
          input-element (atom nil)
          collapse-id (str "application-paging-tips-collapse-" id)]
      (fn [{:keys [id on-change paginging? info]}]
        (let [on-change (debounce on-change 500)]
          [:<>
           [:div.paging-field.mt-3
            [:label.mr-3 {:for id}
             (text :t.paging/paging)]

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
                              (on-change value)))}]

             (when-not (= "" @input-value)
               [:div.input-group-append
                [:button.btn.btn-outline-secondary
                 {:id (str id "-clear")
                  :type :button
                  :aria-label (text :t.paging/clear-paging)
                  ;; override the custom font-size from .btn which breaks .input-group
                  :style {:font-size "inherit"}
                  :on-click (fn []
                              (reset! input-value "")
                              (on-change "")
                              (.focus @input-element))}
                 [close-symbol]]])]

            (when info
              [:a.application-paging-tips.btn.btn-link.collapsed
               {:data-toggle "collapse"
                :href (str "#" collapse-id)
                :aria-label (text :t.paging/example-paginges)
                :aria-expanded "false"
                :aria-controls collapse-id}
               [:i.fa.fa-question-circle]])
            (when paginging?
              [spinner/small])]
           (when info
             [:div.paging-tips.collapse.my-3 {:id collapse-id
                                              :ref focus-when-collapse-opened
                                              :tab-index "-1"}
              info])]))))
