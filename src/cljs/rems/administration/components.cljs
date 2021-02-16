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
            [rems.text :refer [text-format]]))

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

(defn input-field [{:keys [keys label placeholder context type normalizer readonly]}]
  (let [form @(rf/subscribe [(:get-form context)])
        form-errors (when (:get-form-errors context)
                      @(rf/subscribe [(:get-form-errors context)]))
        id (keys-to-id keys)
        normalizer (or normalizer identity)
        error (get-in form-errors keys)]
    [:div.form-group.field
     [:label {:for id} label]
     [:input.form-control {:type type
                           :id id
                           :disabled readonly
                           :placeholder placeholder
                           :class (when error "is-invalid")
                           :value (get-in form keys)
                           :on-change #(rf/dispatch [(:update-form context)
                                                     keys
                                                     (normalizer (.. % -target -value))])}]
     [field-validation-message error label]]))

(defn text-field
  "A basic text field, full page width."
  [context keys]
  (input-field (merge keys {:context context :type "text"})))

(defn textarea-autosize
  "A basic textarea, full page width."
  [context {:keys [keys label placeholder]}]
  (let [form @(rf/subscribe [(:get-form context)])
        form-errors (when (:get-form-errors context)
                      @(rf/subscribe [(:get-form-errors context)]))
        id (keys-to-id keys)
        error (get-in form-errors keys)]
    [:div.form-group.field
     [:label {:for id} label]
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
    [:div.form-group.row
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
  [context {:keys [keys label]}]
  (let [languages @(rf/subscribe [:languages])]
    (into [:div.form-group.field
           [:label label]]
          (for [lang languages]
            [localized-text-field-lang context {:keys-prefix keys
                                                :label label
                                                :lang lang}]))))

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
   (when label [:label {:for id} label])
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
