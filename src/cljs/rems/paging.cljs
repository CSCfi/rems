(ns rems.paging
  (:require [reagent.core :as r]
            [rems.atoms :as atoms]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text]]))

(defn- page-number [{:keys [paging page on-change]}]
  (if (= (:current-page paging) page)
    [atoms/link {:label [:u (str (inc page))]
                 :class "btn btn-link current-page"
                 :disabled true
                 :on-click #(on-change (assoc paging :current-page page))}]

    [atoms/link {:label (str (inc page))
                 :class "btn btn-link"
                 :on-click #(on-change (assoc paging :current-page page))}]))

(defn- page-numbers [{:keys [id] :as opts} pages]
  [:<>
   (for [page pages]
     ^{:key (str id "-page-" page)}
     [page-number (assoc opts :page page)])])

(defn paging-field
  "Component for showing page numbers.

  Intended to be used together with a `rems.table/table` through `rems.table/paging`.

  `:id`                      - identity of the component (derived from table)
  `:on-change`               - callback
  `:paging`                  - paging state (with table)
    `:current-page`          - the current page (0-indexed)
    `:show-all-page-numbers` - state of whether to show all page numbers or `...`
  `:pages`                   - how many pages exist"
  [{:keys [id on-change paging pages]}]
  (r/with-let [show-all-page-numbers (r/atom (:show-all-page-numbers paging))]
    (let [opts {:id id :paging paging :on-change on-change}]
      (when (> pages 1)
        [:div.d-flex.gap-1.align-items-center.justify-content-center.flex-wrap
        [:div (text :t.table.paging/page)]
        [:div.my-3.paging-numbers
         {:class (if @show-all-page-numbers
                   "paging-numbers-grid"
                   "paging-numbers-flex")
          :id (str id "-pages")}

          (if (or @show-all-page-numbers
                  (< pages 10))
            ;; few pages, just show them all
            [page-numbers opts (range pages)]

            ;; show 1 2 3 ... 7 8 9
            (let [first-pages (take 3 (range pages))
                  last-pages (take-last 3 (drop 3 (range pages)))]
              [:<>
               [page-numbers opts first-pages]

               ^{:key (str id "-page-...")}
               [atoms/link {:label "..."
                            :class "btn btn-link"
                            :on-click #(reset! show-all-page-numbers true)}]

               [page-numbers opts last-pages]]))]]))))


(defn guide []
  [:div
   (component-info paging-field)
   (example "no pages"
            [paging-field {:id "paging1"
                           :pages 0}])

   (example "1 page"
            [paging-field {:id "paging2"
                           :pages 1}])

   (example "3 pages, current page 2"
            [paging-field {:id "paging3"
                           :paging {:current-page 1}
                           :pages 3}])

   (example "9 pages, current page 2"
            [paging-field {:id "paging4"
                           :paging {:current-page 1}
                           :pages 9}])

   (example "100 pages, current page 2, not opened"
            [paging-field {:id "paging5"
                           :paging {:current-page 1}
                           :pages 100}])

   (example "100 pages, current page 2, opened all page numbers"
            [paging-field {:id "paging5"
                           :paging {:current-page 1
                                    :show-all-page-numbers true}
                           :pages 100}])])

