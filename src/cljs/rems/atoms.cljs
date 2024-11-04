(ns rems.atoms
  (:require [clojure.string :as str]
            [komponentit.autosize :as autosize]
            [medley.core :refer [assoc-some remove-vals update-existing]]
            [reagent.core :as r]
            [reagent.impl.util]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text text-format localized localize-attachment]]
            [rems.util :refer [class-names]]))

(defn external-link []
  [:i {:class "fa fa-external-link-alt"}
   [:span.sr-only (text :t.link/opens-in-new-window)]])

(defn file-download []
  [:i {:class "fa fa-file-download"}
   [:span.sr-only (text :t.link/download-file)]])

(defn link
  ([opts]
   (link (dissoc opts :label :href)
         (:href opts)
         (:label opts)))
  ([opts href label]
   (when-not (str/blank? label)
     (let [button? (not (str/includes? (class-names (:class opts))
                                       "btn-link"))]
       [:a (->> {:href href}
                (merge (when button? {:role :button}))
                (merge opts)
                (remove-vals nil?))
        label]))))

(defn image [opts src]
  [:img (merge opts {:src src})])

(defn sort-symbol [sort-order]
  (let [[class label] (case sort-order
                        :asc ["fa-arrow-up" :t.table/ascending-order]
                        :desc ["fa-arrow-down" :t.table/descending-order])]
    [:i.fa {:class class}]))

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

(defn not-shown-to-applying-users-symbol []
  [:i.fas.fa-eye-slash {:title (text :t.applications/not-shown-to-applying-users)}
   [:span.sr-only (text :t.applications/not-shown-to-applying-users)]])

(defn shown-to-applying-users-symbol []
  [:i.fas.fa-eye {:title (text :t.applications/shown-to-applying-users)}
   [:span.sr-only (text :t.applications/shown-to-applying-users)]])

(defn info-symbol [] [:i.fa.fa-info-circle])

(defn textarea [attrs]
  [autosize/textarea (merge {:min-rows 5}
                            (update attrs :class #(str/trim (str "form-control " %))))])

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
  `:inline?`     - puts the label and value on the same row
  `:box?`        - wrap the value into a field value box (default true)
  `:multiline?`  - puts the value in multiple rows, if possible. see `rems.atoms/format-field-values`"
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
  (if disabled?
    [:a.download.btn.btn-outline-secondary.text-truncate.disabled
     {:disabled true
      :role :button
      :tabIndex "-1"}
     [:span title]]

    [:a.download.btn.btn-outline-secondary.text-truncate
     {:href url
      :role :button
      :target "_blank"}
     [file-download]
     [:span title]]))

(defn license-attachment-link
  "Renders link to the attachment with `id` and name `title`."
  [id title]
  [:div.attachment-link
   [download-button {:title title
                     :url (str "/api/licenses/attachments/" id)}]])

(defn attachment-link [attachment]
  (let [filename (:attachment/filename attachment)]
    (cond
      (= :filename/redacted filename)
      [:div.attachment-link
       (text :t.applications/attachment-filename-redacted)]

      (:attachment/redacted attachment)
      [:div.attachment-link
       [download-button {:disabled? true
                         :title filename}]
       [:div.col-auto (text :t.applications/attachment-filename-redacted)]]

      :else
      [:div.attachment-link
       [download-button {:title (localize-attachment attachment)
                         :url (str "/applications/attachment/" (:attachment/id attachment))}]])))

(defn enrich-user [user]
  (assoc user :display (str (or (:name user)
                                (:userid user))
                            (when (:email user)
                              (str " (" (:email user) ")")))))

(defn enrich-email [email]
  (assoc email :display [:a {:href (str "mailto:" (:email email))}
                         (str (localized (:name email))
                              " <" (:email email) ">")]))

(defn- set-document-title! [s]
  (r/with-let [title (r/reaction ; changes when text and text-format change
                      (if-not (str/blank? s)
                        (text-format :t.label/dash s (text :t.header/title))
                        (text :t.header/title)))]
    (set! (.-title js/document) @title)))

(defn document-title [title & [{:keys [heading?] :or {heading? true}}]]
  (let [the-title @(r/track set-document-title! title)]
    (when heading?
      [:h1 the-title])))

(defn button
  "Plain button with defaults. `props` are passed to button, except for following keys:

   * `:text` text to display in button"
  [props]
  (let [button-defaults {:type :button
                         :class ["btn-secondary"]}]
    [:button.btn (merge button-defaults
                        (dissoc props :text))
     (:text props)]))

(defn- wrap-rate-limit [callback ms]
  (r/with-let [wait (r/atom nil)]
    (fn rate-limited-callback [& args]
      (when-not @wait
        (reset! wait (js/setTimeout #(reset! wait nil) ms))
        (apply callback args)))
    (finally
      (some-> @wait js/clearTimeout))))

(defn rate-limited-button
  "atoms/button variant that allows click handler to be called, at most,
   once per interval `:wait`. Only the 1st call will go through if called
   multiple times.

   * `:wait` interval in milliseconds"
  [{:keys [disabled wait] :or {wait 2000} :as props}]
  [button (-> props
              (dissoc :wait
                      :loading?
                      (when disabled :on-click))
              (update-existing :on-click wrap-rate-limit wait))])

(defn action-button
  "Takes an `action` description and creates a button that triggers it."
  [{:keys [class id label on-click url] :as action}]
  [link (-> action
            (dissoc :on-click :text :url)
            (assoc-some :on-click on-click)
            (assoc :id id
                   :label (:text action label) ; XXX: consider refactoring :text -> :label
                   :href (or url "#")
                   :class (class-names :btn :btn-secondary class)))])

(defn action-link
  "Takes an `action` description and creates a link that triggers it."
  [{:keys [class id label on-click url] :as action}]
  [link (-> action
            (dissoc :on-click :text :url)
            (assoc-some :on-click on-click)
            (assoc :id id
                   :label (:text action label) ; XXX: consider refactoring :text -> :label
                   :href (or url "#")
                   :class (class-names :btn :btn-link class)
                   :role :button))])

(defn rate-limited-action-button
  "Takes an `action` description and creates a button that triggers it.
   Click events are rate limited."
  [{:keys [class disabled id label on-click]}]
  [rate-limited-button {:class (str/trim (str "btn-secondary " class))
                        :disabled disabled
                        :id id
                        :on-click on-click
                        :text label ; XXX: button and rate-limited-button could use label instead
                        }])

(defn new-action
  "Standard new action helper, to use with e.g. `action-button` or `action-link`."
  [{:keys [label] :as action}]
  (assoc action
         :label [:<> [add-symbol] " " (or label (text :t.administration/add))]))

(defn cancel-action
  "Standard cancel action helper, to use with e.g. `action-button` or `action-link`."
  [action]
  (assoc action
         :label [text :t.administration/cancel]))

(defn edit-action
  "Standard edit action helper, to use with e.g. `action-button` or `action-link`."
  [action]
  (assoc action
         :label [text :t.administration/edit]))

(defn save-action
  "Standard save action helper, to use with e.g. `action-button` or `action-link`."
  [action]
  (assoc action
         :label [text :t.administration/save]))

(defn commands
  "Creates a standard commands group with left alignment."
  [& commands]
  (into [:div.commands.justify-content-start.mb-3] commands))

(defn commands-group-button
  "Displays a group of commands in a dropdown button.

  If there is just one, replaces the dropdown button with
  the actual command.

  The individual commands must follow the `action` format,
  and can be used in both link or button variant depending
  on the number of commands."
  [{:keys [label]} & actions]
  (let [actions (for [action-or-list actions ; flatten first level
                      action (if (list? action-or-list) action-or-list [action-or-list])
                      :when (not (nil? action))]
                  action)]
    (case (count actions)
      ;; nothing
      0 nil

      ;; if only one, display it as a button directly
      1 [action-button (first actions)]

      ;; group actions as links in a popup of a button
      [:div.btn-group
       [:button.modify-dropdown.btn.btn-secondary.dropdown-toggle
        {:data-toggle :dropdown}
        label]
       (into [:div.dropdown-menu.dropdown-menu-right]
             (for [action actions]
               [action-link (update action :class #(str "dropdown-item py-2 " %))]))])))

(defn guide []
  (let [state (r/atom false)
        on-change #(swap! state not)]
    (fn []
      [:div
       (component-info success-symbol)
       (example "success symbol"
                [success-symbol])
       (component-info failure-symbol)
       (example "failure symbol"
                [failure-symbol])

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

       (component-info action-link)
       (example "example command as link"

                (def example-command {:id "example-command"
                                      :class "example-command"
                                      :label "Example"
                                      :url "http://example.com/command"
                                      :on-click #(js/alert "click example")})

                [action-link example-command])
       (component-info action-button)
       (example "example command as button" [action-button example-command])
       (component-info rate-limited-action-button)
       (example "example command as rate-limited button"

                (defn- stateful-context [[c cmd]]
                  (r/with-let [n (r/atom 0)
                               on-click #(r/rswap! n inc)]
                    [:<>
                     [c (assoc cmd :on-click on-click)]
                     [:span.ml-2 "Count: " @n]]))

                [stateful-context
                 [rate-limited-action-button example-command]])

       (component-info commands)
       (example "empty commands" [commands])
       (example "with commands"

                (def another-command {:id "another-command"
                                      :class "another-command"
                                      :label "Another"
                                      :url "http://example.com/another"
                                      :on-click #(js/alert "click another")})

                [commands
                 [action-button example-command]
                 [action-button another-command]])

       (component-info commands-group-button)
       (example "empty group" [commands-group-button])
       (example "one command is directly shown"
                [commands-group-button {:label "Group"}
                 example-command])
       (example "with more commands"
                [commands-group-button {:label "Group"}
                 example-command
                 another-command])

       (component-info rate-limited-button)
       (example "rate limited button"
                (r/with-let [n (r/atom 0)]
                  [rate-limited-button {:on-click #(r/rswap! n inc)
                                        :text (str "Count: " @n)}]))])))
