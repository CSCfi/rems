(ns rems.administration.duo
  (:require [goog.functions :refer [debounce]]
            [re-frame.core :as rf]
            [rems.administration.components :refer [date-field inline-info-field input-field textarea-autosize text-field]]
            [rems.atoms :as atoms]
            [rems.common.duo :refer [duo-restriction-label duo-validation-summary]]
            [rems.common.util :refer [escape-element-id]]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.fields :as fields]
            [rems.text :refer [localized text text-format]]
            [rems.util :refer [linkify]]))

(defn duo-restriction-info-field [& [opts]]
  (let [restriction-type (:type opts)
        values (:values opts)]
    (case restriction-type
      :mondo [:div.container-fluid.pt-2.px-0
              [:label (text (get duo-restriction-label restriction-type))]
              [:div.solid-group
               (for [mondo values]
                 ^{:key mondo}
                 [inline-info-field
                  [:pre.mb-0 (:id mondo)] (:label mondo)])]]

      [inline-info-field
       (text (get duo-restriction-label restriction-type)) (:value (first values))])))

(defn duo-valid-icon [valid]
  (case valid
    :duo/compatible [:span.mr-3.fa.fa-check-circle.text-success]
    :duo/not-compatible [:span.mr-3.fa.fa-times-circle.text-danger]
    :duo/needs-manual-validation [:span.mr-3.fa.fa-exclamation-circle.text-warning]
    nil))

(defn- duo-error [error]
  (case (:type error)
    :t.duo.validation/mondo-not-valid
    (let [label (text (get duo-restriction-label :mondo))]
      [:div.alert.alert-danger
       [:p (text (:type error))]
       [:ul
        [:li (text-format :t.label/default (text :t.applications/resource) (localized (:catalogue-item/title error)))]
        (doall
         (for [mondo (:duo/restrictions error)]
           ^{:key (:id mondo)}
           [:li (text-format :t.label/long label (:id mondo) (:label mondo))]))]])

    :t.duo.validation/needs-validation
    [:div.alert.alert-warning
     [:p (text (:type error))]
     [:ul
      [:li (text-format :t.label/default (text :t.applications/resource) (localized (:catalogue-item/title error)))]
      (into [:<>] (for [restriction (:duo/restrictions error)
                        :let [label (text (get duo-restriction-label (:type restriction)))]
                        value (:values restriction)]
                    [:li (text-format :t.label/default label (:value value))]))]]

    nil))

(defn- duo-more-info [info]
  [:div.solid-group
   (when-some [title (:catalogue-item/title info)]
     [:p (text-format :t.label/default (text :t.applications/resource) (localized title))])
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
        statuses (map (comp :validity :duo/validation) matches)
        collapsible-id (escape-element-id (:id opts))]
    [:div.form-item {:class (if (:compact? opts) "mb-2" "my-2")}
     [atoms/expander
      {:id collapsible-id
       :title [(if (:compact? opts) :p :h3) {:class "mb-0"}
               [duo-valid-icon (duo-validation-summary statuses)]
               (text-format :t.label/dash (:shorthand duo) (localized (:label duo)))]
       :content [:div.mt-2.solid-group
                 [:pre (:id duo)]
                 (if (empty? (:duo/more-infos opts))
                   [:p (localized (:description duo))]
                   [:div.mb-2
                    [:span (localized (:description duo))]
                    (when-let [more-infos (seq (:duo/more-infos opts))]
                      [fields/info-collapse
                       {:info-id (str collapsible-id "-more-infos")
                        :aria-label-text (localized (:description duo))
                        :content (into [:<>] (for [info more-infos]
                                               [duo-more-info info]))}])])
                 (for [restriction (:restrictions duo)
                       :when (seq (:values restriction))]
                   ^{:key (:type restriction)}
                   [:<>
                    [duo-restriction-info-field restriction]
                    (into [:<>] (for [error (mapcat (comp :errors :duo/validation) matches)]
                                  [duo-error error]))])]}]]))

(fetcher/reg-fetcher ::mondo-codes "/api/resources/search-mondo-codes")

(defn duo-restriction-field [{:keys [on-change] :as opts}]
  (let [duo-id (:duo/id opts)
        context (:context opts)
        restriction (:duo/restriction opts)
        restriction-label (text (get duo-restriction-label (:type restriction)))]
    (case (:type restriction)
      :mondo
      (let [update-path [duo-id :restrictions :mondo]
            mondos (:values restriction)]
        [:div.mb-4
         [:label.administration-field-label {:for "mondos-dropdown"} restriction-label]
         [dropdown/async-dropdown
          {:id "mondos-dropdown"
           :item-key :id
           :item-label #(text-format :t.label/dash (:id %) (:label %))
           :multi? true
           :items mondos
           :on-change #(let [new-value %]
                         (rf/dispatch [(:update-form context) update-path new-value])
                         (when on-change
                           (on-change new-value)))
           :on-load-options (-> (fn [{:keys [query-string on-data]}]
                                  (rf/dispatch [::mondo-codes {:search-text query-string} {:on-data on-data}]))
                                (debounce 500))
           :placeholder (text :t.search/search)}]])

      :date
      (let [update-path [duo-id :restrictions :date]]
        [date-field context
         {:label restriction-label
          :on-change on-change
          :keys update-path}])

      :months
      (let [update-path [duo-id :restrictions :months]]
        [input-field {:type :number
                      :context context
                      :keys update-path
                      :label restriction-label
                      :on-change on-change
                      :input-style {:max-width 200}}])

      (:topic :location :institute :collaboration :project :users)
      (let [update-path [duo-id :restrictions (:type restriction)]]
        [text-field context
         {:keys update-path
          :on-change on-change
          :label restriction-label}])

      nil)))

(defn duo-field [duo & [opts]]
  [:<>
   [:h3
    [duo-valid-icon (duo-validation-summary (:duo/statuses opts))]
    (text-format :t.label/dash (:shorthand duo) (localized (:label duo)))]
   [:pre (:id duo)]
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
                             :on-change (:on-change opts)
                             :duo/restriction {:type (key restriction)
                                               :values (val restriction)}}])
   (into [:<>] (for [error (:duo/errors opts)]
                 [duo-error error]))])

