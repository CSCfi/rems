(ns rems.administration.duo
  (:require [goog.functions :refer [debounce]]
            [re-frame.core :as rf]
            [rems.administration.components :refer [date-field inline-info-field input-field textarea-autosize text-field]]
            [rems.collapsible :as collapsible]
            [rems.common.duo :refer [duo-restriction-label duo-validation-summary]]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.fields :as fields]
            [rems.text :refer [localized text]]
            [rems.util :refer [escape-element-id linkify]]))

(defn duo-restriction-info-field [& [opts]]
  (let [restriction-type (:type opts)
        values (:values opts)]
    (case restriction-type
      :mondo [:div.container-fluid.pt-2.px-0
              [:label (text (duo-restriction-label restriction-type))]
              (into [:div.solid-group]
                    (for [mondo values]
                      ^{:key mondo}
                      [inline-info-field
                       [:pre.mb-0 (:id mondo)] (:label mondo)]))]

      [inline-info-field
       (text (duo-restriction-label restriction-type)) (:value (first values))])))

(defn duo-valid-icon [valid]
  (case valid
    :duo/compatible [:span.mr-3.fa.fa-check-circle.text-success]
    :duo/not-compatible [:span.mr-3.fa.fa-times-circle.text-danger]
    :duo/needs-manual-validation [:span.mr-3.fa.fa-exclamation-circle.text-warning]
    nil))

(defn- duo-error [error]
  (case (:type error)
    :t.duo.validation/mondo-not-valid
    (let [label (text (duo-restriction-label :mondo))]
      [:div.alert.alert-danger
       [:p (text (:type error))]
       [:ul
        [:li (str (text :t.applications/resource) ": " (localized (:catalogue-item/title error)))]
        (for [mondo (:duo/restrictions error)]
          ^{:key (:id mondo)}
          [:li (str label ": " (:id mondo) " - " (:label mondo))])]])

    :t.duo.validation/needs-validation
    [:div.alert.alert-warning
     [:p (text (:type error))]
     [:ul
      [:li (str (text :t.applications/resource) ": " (localized (:catalogue-item/title error)))]
      (doall
       (for [restriction (:duo/restrictions error)
             :let [label (text (duo-restriction-label (:type restriction)))]]
         (for [{:keys [value]} (:values restriction)]
           ^{:key (random-uuid)}
           [:li (str label ": " value)])))]]

    nil))

(defn- duo-more-info [info]
  [:div.solid-group
   (when-some [title (:catalogue-item/title info)]
     [:p
      (str (text :t.applications/resource) ": ")
      [:span.font-weight-bold (localized title)]])
   [:p (linkify (localized (:more-info info)))]])

(defn duo-info-field
  "Read-only field for displaying DUO code.
   
   Options:
   * `:id` Unique id for expandable content
   * `:compact?` Display more compact header
   * `:duo/matches` List of duo matches for displaying DUO code validity status in header, and displaying possible validation errors
   * `:duo/more-infos` List of extra info to display in expandable content block"
  [& [opts]]
  (let [duo (:duo opts)
        matches (:duo/matches opts)
        statuses (map (comp :valid :duo/validation) matches)
        collapsible-id (escape-element-id (:id opts))]
    [:div.form-item {:class (if (:compcat? opts) "mb-2" "my-2")}
     [collapsible/expander
      {:id collapsible-id
       :title [(if (:compact? opts) :p :h3) {:class "mb-0"}
               [duo-valid-icon (duo-validation-summary statuses)]
               (str (:shorthand duo) " – " (localized (:label duo)))]
       :content [:div.mt-2.solid-group
                 [:pre (get duo :id "unknown code")]
                 (if (empty? (:duo/more-infos opts))
                   [:p (localized (:description duo))]
                   [:div.mb-2
                    [:span (localized (:description duo))]
                    (when-let [more-infos (seq (:duo/more-infos opts))]
                      [fields/info-collapse
                       {:info-id (str collapsible-id "-more-infos")
                        :aria-label-text (localized (:description duo))
                        :content (into [:div]
                                       (for [info more-infos]
                                         ^{:key (:resource/id info)}
                                         [duo-more-info info]))}])])
                 (for [restriction (:restrictions duo)
                       :when (seq (:values restriction))]
                   ^{:key (:type restriction)}
                   [:<>
                    [duo-restriction-info-field restriction]
                    (for [error (mapcat (comp :errors :duo/validation) matches)]
                      ^{:key (random-uuid)}
                      [duo-error error])])]}]]))

(fetcher/reg-fetcher ::mondo-codes "/api/resources/search-mondo-codes")

(defn duo-restriction-field [opts]
  (let [duo-id (:duo/id opts)
        context (:context opts)
        restriction (:duo/restriction opts)
        restriction-label (text (duo-restriction-label (:type restriction)))]
    (case (:type restriction)
      :mondo
      (let [update-path [duo-id :restrictions :mondo]
            mondos (:values restriction)]
        [:div.mb-4
         [:label.administration-field-label {:for "mondos-dropdown"} restriction-label]
         [dropdown/async-dropdown
          {:id "mondos-dropdown"
           :item-key :id
           :item-label #(str (:id %) " – " (:label %))
           :multi? true
           :items mondos
           :on-change #(rf/dispatch [(:update-form context) update-path %])
           :on-load-options (-> (fn [{:keys [query-string on-data]}]
                                  (rf/dispatch [::mondo-codes {:search-text query-string} {:on-data on-data}]))
                                (debounce 500))
           :loading? @(rf/subscribe [::mondo-codes :fetching?])
           :placeholder (text :t.search/search)}]])

      :date
      (let [update-path [duo-id :restrictions :date]]
        [date-field context
         {:label restriction-label
          :keys update-path}])

      :months
      (let [update-path [duo-id :restrictions :months]]
        [input-field {:type :number
                      :context context
                      :keys update-path
                      :label restriction-label
                      :input-style {:max-width 200}}])

      (:topic :location :institute :collaboration :project :users)
      (let [update-path [duo-id :restrictions (:type restriction)]]
        [text-field context
         {:keys update-path
          :label restriction-label}])

      nil)))

(defn duo-field [duo & [opts]]
  [:<>
   [:h3
    [duo-valid-icon (duo-validation-summary (:duo/statuses opts))]
    (str (:shorthand duo) " – " (localized (:label duo)))]
   [:pre (get duo :id "unknown code")]
   [:p (localized (:description duo))]
   (if (:create-field? opts)
     [textarea-autosize (:context opts)
      {:keys [(:id duo) :more-info]
       :label [:div (text :t.duo.fields/more-info)]}]
     (for [info (:duo/more-infos opts)]
       ^{:key (str (:id duo) (:resource/id info))}
       [duo-more-info info]))
   (for [restriction (:restrictions duo)]
     ^{:key (key restriction)}
     [duo-restriction-field {:duo/id (:id duo)
                             :context (:context opts)
                             :duo/restriction {:type (key restriction)
                                               :values (val restriction)}}])
   (for [error (:duo/errors opts)]
     ^{:key (random-uuid)}
     [duo-error error])])

