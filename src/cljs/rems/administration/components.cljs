(ns rems.administration.components
  "Reusable form components to use on the administration pages.

  Each component takes a `context` parameter to refer to the form state
  in the re-frame store. The context must be a map with these keys:
    :get-form     - Query ID for subscribing to the form data.
    :update-form  - Event handler ID for updating the form data.
                    The event will have two parameters `keys` and `value`,
                    analogous to the `assoc-in` parameters.

  The second parameter to each component is a map with all component specific variables.
  Typically this includes at least `keys` and `label`.
    :keys   - List of keys, a path to the component's data in the form state,
              analogous to the `get-in` and `assoc-in` parameters.
    :label  - String, shown to the user as-is."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :refer [info-field textarea]]
            [rems.collapsible :as collapsible]
            [rems.dropdown :as dropdown]
            [rems.fields :as fields]
            [rems.common.roles :as roles]
            [rems.text :refer [text text-format]]))

(defn- key-to-id [key]
  (if (number? key)
    (str key)
    (name key)))

(defn- keys-to-id [keys]
  (->> keys
       (map key-to-id)
       (str/join "-")))

(defn- field-validation-message [error label]
  [:div {:class "invalid-feedback"}
   (when error (text-format error label))])

(defn input-field [{:keys [keys label placeholder context type normalizer readonly inline? input-style]}]
  (let [form @(rf/subscribe [(:get-form context)])
        form-errors (when (:get-form-errors context)
                      @(rf/subscribe [(:get-form-errors context)]))
        id (keys-to-id keys)
        normalizer (or normalizer identity)
        error (get-in form-errors keys)]
    [:div.form-group.field {:class (when inline? "row")}
     [:label {:for id
              :class (if inline?
                       "col-sm-auto col-form-label"
                       "administration-field-label")}
      label]
     [:div {:class (when inline? "col")}
      [:input.form-control {:type type
                            :id id
                            :style input-style
                            :disabled readonly
                            :placeholder placeholder
                            :class (when error "is-invalid")
                            :value (get-in form keys)
                            :on-change #(rf/dispatch [(:update-form context)
                                                      keys
                                                      (normalizer (.. % -target -value))])}]
      [field-validation-message error label]]]))

(defn text-field
  "A basic text field, full page width."
  [context keys]
  (input-field (merge keys {:context context :type "text"})))

(defn text-field-inline
  "A basic text field, label next to field"
  [context keys]
  (input-field (merge keys {:context context :type "text" :inline? true})))

(defn number-field
  "A basic number field, full page width."
  [context keys]
  (input-field (merge keys {:context context :type "number"})))

(defn textarea-autosize
  "A basic textarea, full page width."
  [context {:keys [keys label placeholder]}]
  (let [form @(rf/subscribe [(:get-form context)])
        form-errors (when (:get-form-errors context)
                      @(rf/subscribe [(:get-form-errors context)]))
        id (keys-to-id keys)
        error (get-in form-errors keys)]
    [:div.form-group.field
     [:label.administration-field-label {:for id} label]
     [textarea {:id id
                :placeholder placeholder
                :value (get-in form keys)
                :class (when error "is-invalid")
                :on-change #(rf/dispatch [(:update-form context)
                                          keys
                                          (.. % -target -value)])}]
     [field-validation-message error label]]))

(defn- localized-text-field-lang [context {:keys [keys-prefix label lang]}]
  (let [form @(rf/subscribe [(:get-form context)])
        form-errors (when (:get-form-errors context)
                      @(rf/subscribe [(:get-form-errors context)]))
        keys (conj keys-prefix lang)
        id (keys-to-id keys)
        error (get-in form-errors keys)]
    [:div.form-group.row.mb-0
     [:label.col-sm-1.col-form-label {:for id}
      (str/upper-case (name lang))]
     [:div.col-sm-11
      [textarea {:id id
                 :min-rows 1
                 :value (get-in form keys)
                 :class (when error "is-invalid")
                 :on-change #(rf/dispatch [(:update-form context)
                                           keys
                                           (.. % -target -value)])}]
      [field-validation-message error label]]]))

(defn localized-text-field
  "A text field for inputting text in all supported languages.
  Has a separate text fields for each language. The data is stored
  in the form as a map of language to text."
  [context {:keys [keys label collapse?]}]
  (let [languages @(rf/subscribe [:languages])
        id (keys-to-id keys)
        fields (into [:<>]
                     (for [lang languages]
                       [localized-text-field-lang context {:keys-prefix keys
                                                           :label label
                                                           :lang lang}]))]
    (if collapse?
      [:div.form-group.field.mb-1
       [:label.administration-field-label
        label
        " "
        [collapsible/controls id (text :t.collapse/show) (text :t.collapse/hide)]]
       [:div.collapse {:id id}
        fields]]
      [:div.form-group.field
       [:label.administration-field-label label]
       fields])))

(defn checkbox
  "A single checkbox, on its own line."
  [context {:keys [keys label negate?]}]
  (let [form @(rf/subscribe [(:get-form context)])
        val-fn (if negate?
                 (comp not boolean)
                 boolean)
        id (keys-to-id keys)]
    [:div.form-group.field
     [:div.form-check
      [:input.form-check-input {:id id
                                :type "checkbox"
                                :checked (val-fn (get-in form keys))
                                :on-change #(rf/dispatch [(:update-form context)
                                                          keys
                                                          (val-fn (.. % -target -checked))])}]
      [:label.form-check-label {:for id}
       label]]]))

(defn- radio-button [context {:keys [keys value label orientation readonly]}]
  (let [form @(rf/subscribe [(:get-form context)])
        form-errors (when (:get-form-errors context)
                      @(rf/subscribe [(:get-form-errors context)]))
        name (keys-to-id keys)
        id (keys-to-id (conj keys value))
        error (get-in form-errors keys)]
    [(case orientation
       :vertical :div.form-check
       :horizontal :div.form-check.form-check-inline)
     [:input.form-check-input {:id id
                               :type "radio"
                               :disabled readonly
                               :class (when error "is-invalid")
                               :name name
                               :value value
                               :checked (= value (get-in form keys))
                               :on-change #(when (.. % -target -checked)
                                             (rf/dispatch [(:update-form context) keys value]))}]
     [:label.form-check-label {:for id}
      label]]))

(defn radio-button-group
  "A list of radio buttons.
  `id`           - the id of the group
  `orientation`  - `:horizontal` or `:vertical`
  `keys`         - keys for options
  `label`        - optional label text for group
  `options`      - list of `{:value \"...\", :label \"...\"}`
  `readonly`     - boolean"
  [context {:keys [id keys label orientation options readonly]}]
  [:div.form-group.field {:id id}
   (when label [:label.administration-field-label {:for id} label])
   (into [:div.form-control]
         (map (fn [{:keys [value label]}]
                [radio-button context {:keys keys
                                       :value value
                                       :label label
                                       :readonly readonly
                                       :orientation orientation}])
              options))])

(defn inline-info-field [text value & [opts]]
  [info-field text value (merge {:inline? true} opts)])

(defn localized-info-field
  "An info field for displaying text in all supported languages.
  The data is passed in as a map of language to text."
  [m {:keys [label]}]
  (let [languages @(rf/subscribe [:languages])
        to-label #(str label " (" (str/upper-case (name %)) ")")]
    (into [:<>]
          (for [lang languages]
            [inline-info-field (to-label lang) (get m lang)]))))

(defn organization-field [context {:keys [keys readonly]}]
  (let [label (text :t.administration/organization)
        organizations @(rf/subscribe [:owned-organizations])
        language @(rf/subscribe [:language])
        form @(rf/subscribe [(:get-form context)])
        value (get-in form keys)
        form-errors (when (:get-form-errors context)
                      @(rf/subscribe [(:get-form-errors context)]))
        id (keys-to-id keys)
        item-selected? #(= (:organization/id %) (:organization/id value))
        disallowed (roles/disallow-setting-organization? @(rf/subscribe [:roles]))]
    [:div.form-group
     [:label.administration-field-label {:for id} label]
     (if (or readonly disallowed)
       [fields/readonly-field {:id id
                               :value (get-in value [:organization/name language])}]
       [dropdown/dropdown
        {:id id
         :items (->> organizations (filter :enabled) (remove :archived))
         :item-key :organization/id
         :item-label (comp language :organization/name)
         :item-selected? item-selected?
         :on-change #(rf/dispatch [(:update-form context) keys %])}])
     [field-validation-message (get-in form-errors keys) label]]))

(defn date-field
  [context {:keys [label keys min max validation optional]}]
  (let [form @(rf/subscribe [(:get-form context)])
        value (get-in form keys)
        form-errors (when (:get-form-errors context)
                      @(rf/subscribe [(:get-form-errors context)]))
        id (keys-to-id keys)]
    ;; TODO: format readonly value in user locale (give field-wrapper a formatted :value and :previous-value in opts)
    [:div.form-group
     [:label.administration-field-label {:for id} label]
     [:input.form-control {:type "date"
                           :id id
                           :name id
                           :class (when validation "is-invalid")
                           :value value
                           :required (not optional)
                           :aria-required (not optional)
                           :aria-invalid (when validation true)
                           :aria-describedby (when validation
                                               (str id "-error"))
                           :min min
                           :max max
                           :on-change #(rf/dispatch [(:update-form context)
                                                     keys
                                                     (.. % -target -value)])}]
     [field-validation-message (get-in form-errors keys) label]]))
