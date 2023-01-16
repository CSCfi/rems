(ns rems.atoms
  (:require [clojure.string :as str]
            [komponentit.autosize :as autosize]
            [reagent.core :as reagent]
            [rems.common.util :refer [escape-element-id]]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text localized localize-attachment]]
            [rems.util :refer [focus-when-collapse-opened]]))

(defn external-link []
  [:i {:class "fa fa-external-link-alt"
       :aria-label (text :t.link/opens-in-new-window)}])

(defn file-download []
  [:i {:class "fa fa-file-download"
       :aria-label (text :t.link/download-file)}])

(defn link [opts uri title]
  [:a (merge opts {:href uri})
   title])

(defn image [opts src]
  [:img (merge opts {:src src})])

(defn sort-symbol [sort-order]
  (let [[class label] (case sort-order
                        :asc ["fa-arrow-up" :t.table/ascending-order]
                        :desc ["fa-arrow-down" :t.table/descending-order])]
    [:i.fa {:class class
            :aria-label (text label)}]))

(defn search-symbol []
  [:i.fa {:class "fa-search"}])

(defn close-symbol []
  [:i.fa {:class "fa-times"}])

(defn success-symbol []
  [:i {:class "fas fa-check-circle text-success"}])

(defn failure-symbol []
  [:i {:class "fas fa-times-circle text-danger"}])

(defn add-symbol []
  [:i.fa.fa-plus])

(defn collapse-symbol []
  [:i {:class "fas fa-compress-alt icon-link"
       :aria-label (text :t.collapse/hide)}])

(defn expand-symbol []
  [:i {:class "fas fa-expand-alt icon-link"
       :aria-label (text :t.collapse/show)}])

(defn make-empty-symbol [& [symbol]]
  (let [symbol (or symbol (success-symbol))]
    [:span {:style {:opacity 0}} symbol]))

(defn textarea [attrs]
  [autosize/textarea (merge {:min-rows 5}
                            (update attrs :class #(str/trim (str "form-control " %))))])

(defn flash-message
  "Displays a notification (aka flash) message.

   :id      - HTML element ID
   :status  - one of the alert types from Bootstrap i.e. :success, :info, :warning or :danger
   :content - content to show inside the notification"
  [{:keys [id status content]}]
  (when status
    [:div.flash-message.alert {:class (str "alert-" (name status))
                               :id id}
     content]))

(defn checkbox
  "Displays a checkbox."
  [{:keys [id class value on-change]}]
  (let [wrapped-on-change (fn [e]
                            (.preventDefault e)
                            (.stopPropagation e)
                            (on-change value))]
    [:i.far.fa-lg
     (cond-> {:id id
              :class [:checkbox class (if value :fa-check-square :fa-square)]
              :tabIndex 0
              :role :checkbox
              :aria-checked value
              :aria-label (if value
                            (text :t.form/checkbox-checked)
                            (text :t.form/checkbox-unchecked))
              :aria-readonly (nil? on-change)}
       on-change (assoc :on-click wrapped-on-change
                        :on-key-press #(when (= (.-key %) " ")
                                         (wrapped-on-change %))))]))

(defn readonly-checkbox
  "Displays a readonly checkbox."
  [opts]
  [:span.readonly-checkbox
   [checkbox (dissoc opts :on-change)]])

(defn- format-field-values
  "Formats field `value` for display.

  A simple and crude version until something better is needed."
  [value multiline?]
  (cond (string? value)
        value

        (keyword? value)
        (name value)

        (boolean? value)
        [readonly-checkbox {:value value}]

        (map? value)
        (->> value
             (mapv (fn [[k v]] [(format-field-values k multiline?) ": " (format-field-values v multiline?)])) ; first level
             (interpose (if multiline? "\n" ", "))
             (mapcat identity) ; flatten only first level, preserve any values as is
             (into [:<>]))

        (and (vector? value)
             (or (keyword? (first value))
                 (fn? (first value))))
        value ; hiccup

        (sequential? value)
        (->> value
             (mapv #(format-field-values % multiline?))
             (interpose (if multiline? "\n" ", "))
             (into [:<>]))

        :else (str value)))

(defn info-field
  "A component that shows a readonly field with title and value.

  Used for e.g. displaying applicant attributes.

  The `value` can be a simple primitive or a collection. Uses a simple approach that can
  be extended if needed. Deeply nested data structures would be difficult to fit,
  so they are not supported.

  Additional options:
  `:inline?` - puts the label and value on the same row
  `:box?`    - wrap the value into a field value box (default true)"
  [title value & [{:keys [inline? box? multiline?] :or {box? true} :as _opts}]]
  (let [formatted-value (format-field-values value multiline?)
        style (cond box? {:class "form-control"}
                    :else {:style {:padding-left 0}})]
    (if inline?
      [:div.container-fluid
       [:div.form-group.row
        [:label.col-sm-3.col-form-label title]
        [:div.col-sm-9 style formatted-value]]]
      [:div.form-group
       [:label title]
       [:div style formatted-value]])))

(defn download-button [{:keys [disabled? title url]}]
  [:a (cond-> {:class [:attachment-link :btn :btn-outline-secondary :mr-2 :text-truncate]
               :href url
               :target :_blank
               :title title
               :style {:max-width "25em"}}
        disabled? (-> (dissoc :href :target)
                      (update :class conj :disabled)))
   [file-download] " " title])

(defn license-attachment-link
  "Renders link to the attachment with `id` and name `title`."
  [id title]
  [download-button {:title title
                    :url (str "/api/licenses/attachments/" id)}])

(defn attachment-link
  "Renders a link to attachment (should have keys :attachment/id and :attachment/filename)"
  [attachment]
  (when attachment
    [:div.field
     [download-button {:disabled? (= :filename/redacted (:attachment/filename attachment))
                       :title (localize-attachment attachment)
                       :url (str "/applications/attachment/" (:attachment/id attachment))}]]))

(defn enrich-user [user]
  (assoc user :display (str (or (:name user)
                                (:userid user))
                            (when (:email user)
                              (str " (" (:email user) ")")))))

(defn enrich-email [email]
  (assoc email :display [:a {:href (str "mailto:" (:email email))}
                         (str (localized (:name email))
                              " <" (:email email) ">")]))

(defn set-document-title! [title]
  (set! (.-title js/document)
        (str title
             (when-not (str/blank? title)
               " - ")
             (text :t.header/title))))

(defn document-title [_title & [{:keys [heading?] :or {heading? true}}]]
  (let [on-update (fn [this]
                    (let [[_ title] (reagent/argv this)]
                      (set-document-title! title)))]
    (reagent/create-class
     {:component-did-mount on-update
      :component-did-update on-update
      :display-name "document-title"
      :reagent-render (fn [title]
                        (when heading?
                          [:h1 title]))})))

(defn logo []
  [:div {:class "logo"}
   [:div.img]])

(defn logo-navigation []
  [:div {:class "navbar-brand logo-menu"}
   [:div.img]])

(defn expander
  "Displays an expandable block of content with animated chevron.

   Pass a map of options with the following keys:
   * `id` unique id for expanded content
   * `content` content which is displayed in expanded state
   * `expanded?` initial expanded state
   * `title` content which is always displayed together with animated chevron"
  [{:keys [expanded?] :or {expanded? false}}]
  (let [expanded (reagent/atom expanded?)]
    (fn [{:keys [id content title]}]
      (let [id (escape-element-id id)]
        [:<>
         [:button.info-button.btn.d-flex.align-items-center.px-0 ; .btn adds unnecessary horizontal padding
          {:data-toggle "collapse"
           :href (str "#" id)
           :aria-expanded (if @expanded "true" "false")
           :aria-controls id
           :on-click #(swap! expanded not)
           :style {:white-space "normal"}} ; .btn uses "nowrap" which overflows the page with long input
          [:span.mr-2.fa.fa-chevron-down.animate-transform
           {:class (when @expanded "rotate-180")}]
          title]
         [:div.collapse {:id id
                         :ref focus-when-collapse-opened
                         :tab-index "-1"}
          content]]))))

(defn guide []
  (let [state (reagent/atom false)
        on-change #(swap! state not)]
    (fn []
      [:div
       (component-info success-symbol)
       (example "success symbol"
                [success-symbol])
       (component-info failure-symbol)
       (example "failure symbol"
                [failure-symbol])

       (component-info flash-message)
       (example "flash-message with info"
                [flash-message {:status :info
                                :content "Hello world"}])

       (example "flash-message with error"
                [flash-message {:status :danger
                                :content "You fail"}])

       (component-info readonly-checkbox)
       (example "readonly-checkbox unchecked"
                [readonly-checkbox {:value false}])
       (example "readonly-checkbox checked"
                [readonly-checkbox {:value true}])
       (example (str "checkbox interactive " (if @state "checked" "unchecked"))
                [checkbox {:value @state :on-change on-change}])
       (example "checkbox with id and class"
                [checkbox {:id :special :class :text-danger :value @state :on-change on-change}])

       (component-info info-field)
       (example "info-field with text"
                [info-field "Users" "Bob Tester"])
       (example "info-field with array"
                [info-field "Users" ["Bob Tester" "Jane Coder"]])
       (example "info-field with array, multline"
                [info-field "Users" ["Bob Tester" "Jane Coder"] {:multiline? true}])
       (example "info-field with boolean"
                [info-field "Users" false])
       (example "info-field with map"
                [info-field "Users" {"Bob Tester" false "Jane Coder" true}])
       (example "info-field with nested data is unsupported but shows somehow"
                [info-field "Users" [{"Bob Tester" false "Jane Coder" {:type :coder :missing nil}}]])
       (example "info-field without box around value"
                [info-field "Users" "Bob Tester" {:box? false}])
       (example "info-field inline"
                [info-field "Users" ["Bob Tester" "Jane Coder"] {:inline? true}])

       (component-info attachment-link)
       (example "attachment-link"
                [attachment-link {:attachment/id 1
                                  :attachment/filename "my-attachment.pdf"}])
       (example "attachment-link, long filename"
                [attachment-link {:attachment/id 123
                                  :attachment/filename "this_is_the_very_very_very_long_filename_of_a_test_file_the_file_itself_is_quite_short_though_abcdefghijklmnopqrstuvwxyz0123456789_overflow_overflow_overflow.txt"}])

       (component-info expander)
       (example "expander"
                [expander {:id "guide-expander-id"
                           :title "Expander block with animated chevron"
                           :expanded? false
                           :content [:p "Expanded content"]}])])))
