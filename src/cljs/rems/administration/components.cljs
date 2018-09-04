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
            [re-frame.core :as rf]))

(defn- key-to-id [key]
  (if (number? key)
    (str key)
    (name key)))

(defn- keys-to-id [keys]
  (->> keys
       (map key-to-id)
       (str/join "-")))

(defn text-field
  "A basic text field, full page width."
  [context {:keys [keys label placeholder]}]
  (let [form @(rf/subscribe [(:get-form context)])
        id (keys-to-id keys)]
    [:div.form-group.field
     [:label {:for id} label]
     [:input.form-control {:type "text"
                           :id id
                           :placeholder placeholder
                           :value (get-in form keys)
                           :on-change #(rf/dispatch [(:update-form context)
                                                     keys
                                                     (.. % -target -value)])}]]))

(defn- localized-text-field-lang [context {:keys [keys-prefix lang]}]
  (let [form @(rf/subscribe [(:get-form context)])
        keys (conj keys-prefix lang)
        id (keys-to-id keys)]
    [:div.form-group.row
     [:label.col-sm-1.col-form-label {:for id}
      (str/upper-case (name lang))]
     [:div.col-sm-11
      [:input.form-control {:type "text"
                            :id id
                            :value (get-in form keys)
                            :on-change #(rf/dispatch [(:update-form context)
                                                      keys
                                                      (.. % -target -value)])}]]]))

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
                                                :lang lang}]))))

(defn checkbox
  "A single checkbox, on its own line."
  [context {:keys [keys label]}]
  (let [form @(rf/subscribe [(:get-form context)])
        id (keys-to-id keys)]
    [:div.form-group.field
     [:div.form-check
      [:input.form-check-input {:id id
                                :type "checkbox"
                                :checked (boolean (get-in form keys))
                                :on-change #(rf/dispatch [(:update-form context)
                                                          keys
                                                          (.. % -target -checked)])}]
      [:label.form-check-label {:for id}
       label]]]))

(defn radio-button
  "A single radio button. Needs to be wrapped in a form group.
  See also `vertical-radio-button-group`."
  [context {:keys [keys value label]}]
  (let [form @(rf/subscribe [(:get-form context)])
        name (keys-to-id keys)
        id (keys-to-id (conj keys value))]
    [:div.form-check
     [:input.form-check-input {:id id
                               :type "radio"
                               :name name
                               :value value
                               :checked (= value (get-in form keys))
                               :on-change #(when (.. % -target -checked)
                                             (rf/dispatch [(:update-form context) keys value]))}]
     [:label.form-check-label {:for id}
      label]]))

(defn vertical-radio-button-group
  "A group of radio buttons, laid out vertically. The radio buttons
   must be listed in `options`, which is a list of maps of the shape
   `{:value \"...\", :label \"...\"}`."
  [context {:keys [keys options]}]
  (into [:div.form-group.field]
        (map (fn [{:keys [value label]}]
               [radio-button context {:keys keys
                                      :value value
                                      :label label}])
             options)))
