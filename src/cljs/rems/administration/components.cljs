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
            [medley.core :refer [assoc-some]]
            [re-frame.core :as rf]
            [rems.atoms :as atoms :refer [info-field textarea]]
            [rems.collapsible :as collapsible]
            [rems.config]
            [rems.dropdown :as dropdown]
            [rems.fields :as fields]
            [rems.globals]
            [rems.common.roles :as roles]
            [rems.common.util :refer [clamp parse-int]]
            [rems.text :refer [localized text text-format]]
            [rems.util :refer [event-checked event-value]]))

(defn- key-to-id [key]
  (if (number? key)
    (str key)
    (name key)))

(defn keys-to-id [key-path]
  (->> key-path
       (map key-to-id)
       (str/join "-")))

(defn field-validation-message [error label]
  (when error
    [:div.invalid-feedback (text-format error label)]))

(defn update-form [context key-path value]
  (rf/dispatch-sync [(:update-form context) key-path value])
  value)

(defn get-form-value [context key-path]
  (if (contains? context :get-in-form)
    @(rf/subscribe [(:get-in-form context) key-path])
    (get-in @(rf/subscribe [(:get-form context)]) key-path)))

(defn get-form-error [context key-path]
  (when (contains? context :get-form-error)
    @(rf/subscribe [(:get-form-error context) key-path])))

(defn input-field [{:keys [context inline? input-style label normalizer on-change placeholder readonly field-type]
                    key-path :keys
                    min-value :min
                    max-value :max}]
  (let [id (keys-to-id key-path)
        value (get-form-value context key-path)
        error (get-form-error context key-path)
        normalizer (or normalizer identity)
        on-change (or on-change identity)]
    [:div.form-group.field {:class (when inline? "row")}
     [:label {:for id
              :class (if inline?
                       "col-sm-auto col-form-label"
                       "administration-field-label")}
      label]
     [:div {:class (when inline? "col")}
      [:input.form-control (-> {:type field-type
                                :id id
                                :style input-style
                                :disabled readonly
                                :placeholder placeholder
                                :value value
                                :on-change #(->> (event-value %)
                                                 normalizer
                                                 (update-form context key-path)
                                                 on-change)}
                               (assoc-some :class (when error "is-invalid")
                                           :min min-value
                                           :max max-value))]
      [field-validation-message error label]]]))

(defn text-field
  "A basic text field, full page width."
  [context opts]
  [input-field (merge opts {:context context :type "text"})])

(defn text-field-inline
  "A basic text field, label next to field"
  [context opts]
  [input-field (merge opts {:context context :type "text" :inline? true})])

(defn number-field
  "A basic number field, full page width."
  [context {min-value :min
            max-value :max
            :or {min-value 0 max-value 1000000}
            :as opts}]
  [input-field (merge opts {:context context
                            :type "number"
                            :normalizer #(some-> % parse-int (clamp min-value max-value))
                            :min min-value
                            :max max-value})])

(defn textarea-autosize
  "A basic textarea, full page width."
  [context {:keys [label normalizer placeholder on-change]
            key-path :keys}]
  (let [value (get-form-value context key-path)
        error (get-form-error context key-path)
        id (keys-to-id key-path)
        normalizer (or normalizer identity)
        on-change (or on-change identity)]
    [:div.form-group.field
     [:label.administration-field-label {:for id} label]
     [textarea {:id id
                :placeholder placeholder
                :value value
                :class (when error "is-invalid")
                :on-change #(->> (event-value %)
                                 normalizer
                                 (update-form context key-path)
                                 on-change)}]
     [field-validation-message error label]]))

(defn localized-textarea-autosize
  "A textarea for inputting text in all supported languages.
  Has a separate textareas for each language. The data is stored
  in the form as a map of language to text. If `:localizations-key` is
  provided in opts, languages are mapped from `[:localizations lang localizations-key]`
  path."
  [context {:keys [localizations-key label normalizer on-change placeholder]
            key-path :keys}]
  (let [normalizer (or normalizer identity)
        on-change (or on-change identity)]
    (into [:div.form-group.localized-field
           [:label.administration-field-label label]]
          (for [language @rems.config/languages
                :let [sub-key-path (if (some? localizations-key)
                                     [:localizations language localizations-key]
                                     (conj key-path language))
                      value (get-form-value context sub-key-path)
                      error (get-form-error context sub-key-path)
                      id (keys-to-id (if (some? localizations-key)
                                       [:localizations language localizations-key]
                                       sub-key-path))]]
            [:div.row.mb-0
             [:label.col-sm-1.col-form-label {:for id}
              (str/upper-case (name language))]
             [:div.col-sm-11
              [textarea {:id id
                         :placeholder placeholder
                         :value value
                         :class (when error "is-invalid")
                         :on-change #(->> (event-value %)
                                          normalizer
                                          (update-form context sub-key-path)
                                          on-change)}]
              [field-validation-message error label]]]))))

(defn- localized-text-field-lang [context {:keys [keys-prefix label lang localizations-key normalizer on-change]}]
  (let [key-path (if localizations-key
                   [:localizations lang localizations-key]
                   (conj (vec keys-prefix) lang))
        value (get-form-value context key-path)
        error (get-form-error context key-path)
        id (keys-to-id key-path)
        normalizer (or normalizer identity)
        on-change (or on-change identity)]
    [:div.row.mb-0
     [:label.col-sm-1.col-form-label {:for id}
      (str/upper-case (name lang))]
     [:div.col-sm-11
      [textarea {:id id
                 :min-rows 1
                 :value value
                 :class (when error "is-invalid")
                 :on-change #(->> (event-value %)
                                  normalizer
                                  (update-form context key-path)
                                  on-change)}]
      [field-validation-message error label]]]))

(defn localized-text-field
  "A text field for inputting text in all supported languages.
  Has a separate text fields for each language. The data is stored
  in the form as a map of language to text. If `:localizations-key` is
  provided in opts, languages are mapped from `[:localizations lang localizations-key]`
  path."
  [context {:keys [collapse? label localizations-key normalizer on-change]
            key-path :keys}]
  (let [id (keys-to-id (if (some? localizations-key)
                         [localizations-key]
                         key-path))
        fields (into [:div.spaced-vertically]
                     (for [lang @rems.config/languages]
                       [localized-text-field-lang context
                        {:keys-prefix key-path
                         :label label
                         :lang lang
                         :localizations-key localizations-key
                         :normalizer normalizer
                         :on-change on-change}]))]
    (if collapse?
      [:div.form-group.field.localized-field
       [:label.administration-field-label.d-flex.align-items-center
        label
        [collapsible/toggle-control id]]
       [collapsible/minimal {:id id
                             :collapse fields}]]
      [:div.form-group.localized-field
       [:label.administration-field-label label]
       fields])))

(defn checkbox
  "A single checkbox, on its own line."
  [context {:keys [label negate? on-change]
            key-path :keys}]
  (let [id (keys-to-id key-path)
        value (get-form-value context key-path)
        val-fn (if negate?
                 (comp not boolean)
                 boolean)
        on-change (or on-change identity)]
    [:div.form-group.field
     [:div.form-check
      [:input.form-check-input {:id id
                                :type "checkbox"
                                :checked (val-fn value)
                                :on-change #(->> (event-checked %)
                                                 val-fn
                                                 (update-form context key-path)
                                                 on-change)}]
      [:label.form-check-label {:for id}
       label]]]))

(defn- radio-button [context {:keys [key-path label on-change orientation readonly value]}]
  (let [name (keys-to-id key-path)
        id (keys-to-id (conj key-path value))
        form-value (get-form-value context key-path)
        error (get-form-error context key-path)
        on-change (or on-change identity)]
    [:div.form-check {:class (when (= :horizontal orientation)
                               "form-check-inline")}
     [:input.form-check-input {:id id
                               :type "radio"
                               :disabled readonly
                               :class (when error "is-invalid")
                               :name name
                               :value value
                               :checked (= value form-value)
                               :on-change #(when (event-checked %)
                                             (->> value
                                                  (update-form context key-path)
                                                  on-change))}]
     [:label.form-check-label {:for id}
      label]]))

(defn radio-button-group
  "A list of radio buttons.
  `id`           - the id of the group
  `keys`         - keys for options
  `label`        - (optional) label text for group
  `on-change`    - (optional) callback
  `orientation`  - `:horizontal` or `:vertical`
  `options`      - list of `{:value \"...\", :label \"...\"}`
  `readonly`     - boolean"
  [context {:keys [id label orientation options readonly on-change]
            key-path :keys}]
  [:div.form-group.field {:id id}
   (when label
     [:label.administration-field-label {:for id} label])
   (into [:div.form-control]
         (for [{:keys [value label]} options]
           ^{:key (str id "-" (name value))}
           [radio-button context {:key-path key-path
                                  :label label
                                  :on-change on-change
                                  :orientation orientation
                                  :readonly readonly
                                  :value value}]))])

(defn inline-info-field [text value & [opts]]
  [info-field text value (merge {:inline? true} opts)])

(defn localized-info-field
  "An info field for displaying text in all supported languages.
  The data is passed in as a map of language to text.
  If :localizations-key is passed in opts, language to text is
  mapped from `[:localizations lang localizations-key]` instead."
  [m {label :label
      localizations-key :localizations-key}]
  (into [:<>]
        (for [lang @rems.config/languages]
          [inline-info-field (text-format :t.label/parens label (str/upper-case (name lang)))
           (if (some? localizations-key)
             (get-in m [:localizations lang localizations-key])
             (get m lang))])))

(defn organization-field [context {:keys [readonly on-change]
                                   key-path :keys}]
  (let [on-change (or on-change identity)
        on-update #(->> %
                        (update-form context key-path)
                        on-change)
        id (keys-to-id key-path)
        potential-value (get-form-value context key-path)
        error (get-form-error context key-path)
        owned-organizations @(rf/subscribe [:owned-organizations])
        valid-organizations (->> owned-organizations
                                 (into [] (comp (filter :enabled) (remove :archived))))
        disallowed (roles/disallow-setting-organization? @rems.globals/roles)
        ;; if item was copied then this org could be something old
        ;; where we have no access to so reset here
        value (if (or readonly
                      disallowed
                      (contains? (set (mapv :organization/id valid-organizations))
                                 (:organization/id potential-value)))
                potential-value

                  ;; not accessible, reset
                (on-update nil))

        item-selected? #(= (:organization/id %) (:organization/id value))]
    [:div.form-group.field
     [:label.administration-field-label {:for id}
      (text :t.administration/organization)]
     (if (or readonly disallowed)
       [fields/readonly-field {:id id
                               :value (localized (:organization/name value))}]
       [dropdown/dropdown
        {:id id
         :items (->> valid-organizations
                     (mapv #(assoc % ::label (localized (:organization/name %)))))
         :item-key :organization/id
         :item-label ::label
         :item-selected? item-selected?
         :on-change on-update}])
     [field-validation-message error (text :t.administration/organization)]]))

(defn date-field [context {:keys [label normalizer on-change optional validation]
                           key-path :keys
                           max-value :max
                           min-value :min}]
  (let [id (keys-to-id key-path)
        value (get-form-value context key-path)
        error (get-form-error context key-path)
        normalizer (or normalizer identity)
        on-change (or on-change identity)]
    ;; TODO: format readonly value in user locale (give field-wrapper a formatted :value and :previous-value in opts)
    [:div.form-group.field
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
                           :min min-value
                           :max max-value
                           :on-change #(->> (event-value %)
                                            normalizer
                                            (update-form context key-path)
                                            on-change)}]
     [field-validation-message error label]]))

(defn perform-action-button [{:keys [loading?] :as props}]
  [atoms/rate-limited-button
   (-> props
       (dissoc (when (or loading? @(rf/subscribe [:rems.app/any-pending-request?]))
                 :on-click)))])
