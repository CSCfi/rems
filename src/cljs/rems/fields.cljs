(ns rems.fields
  "UI components for form fields"
  (:require [clojure.string :as str]
            [rems.atoms :refer [external-link textarea]]
            [rems.text :refer [localized text text-format]]))

(defn- id-to-name [id]
  (str "field" id))

(defn- diff [value previous-value]
  (let [dmp (js/diff_match_patch.)
        diff (.diff_main dmp
                         (str/trim (str previous-value))
                         (str/trim (str value)))]
    (.diff_cleanupSemantic dmp diff)
    diff))

(defn- formatted-diff [value previous-value]
  (->> (diff value previous-value)
       (map (fn [[change text]]
              (cond
                (pos? change) [:ins text]
                (neg? change) [:del text]
                :else text)))))

(defn- diff-field [{:keys [id value previous-value]}]
  (into [:div.form-control.diff {:id id}]
        (formatted-diff value previous-value)))

(defn- field-validation-message [validation title]
  (when validation
    [:div {:class "text-danger"}
     (text-format (:type validation) title)]))

(defn- toggle-diff-button [item-id diff-visible on-toggle-diff]
  [:a.toggle-diff {:href "#"
                   :on-click (fn [event]
                               (.preventDefault event)
                               (on-toggle-diff))}
   [:i.fas.fa-exclamation-circle]
   " "
   (if diff-visible
     (text :t.form/diff-hide)
     (text :t.form/diff-show))])

(defn readonly-field [{:keys [id value]}]
  [:div.form-control {:id id} (str/trim (str value))])

(defn basic-field
  "Common parts of a form field.

  :field/id - number (required), field id
  :field/title - string (required), field title to show to the user
  :field/max-length - maximum number of characters (optional)
  :field/optional - boolean, true if the field is not required
  :field/value - string, the current value of the field
  :field/previous-value - string, the previously submitted value of the field
  :readonly - boolean, true if the field should not be editable
  :readonly-component - HTML, custom component for a readonly field
  :diff - boolean, true if should show the diff between :value and :previous-value
  :diff-component - HTML, custom component for rendering a diff
  :validation - validation errors

  editor-component - HTML, form component for editing the field"
  [{:keys [readonly readonly-component diff diff-component validation on-toggle-diff] :as opts} editor-component]
  (let [id (:field/id opts)
        title (localized (:field/title opts))
        optional (:field/optional opts)
        value (:field/value opts)
        previous-value (:field/previous-value opts)
        max-length (:field/max-length opts)]
    [:div.form-group.field
     [:label {:for (id-to-name id)}
      title " "
      (when max-length
        (text-format :t.form/maxlength (str max-length)))
      " "
      (when optional
        (text :t.form/optional))]
     (when (and previous-value
                (not= value previous-value))
       [toggle-diff-button id diff on-toggle-diff])
     (cond
       diff (or diff-component
                [diff-field {:id (id-to-name id)
                             :value value
                             :previous-value previous-value}])
       readonly (or readonly-component
                    [readonly-field {:id (id-to-name id)
                                     :value value}])
       :else editor-component)
     [field-validation-message validation title]]))

(defn- event-value [event]
  (.. event -target -value))

(defn text-field
  [{:keys [validation on-change] :as opts}]
  (let [id (:field/id opts)
        placeholder (localized (:field/placeholder opts))
        value (:field/value opts)
        max-length (:field/max-length opts)]
    [basic-field opts
     [:input.form-control {:type "text"
                           :id (id-to-name id)
                           :name (id-to-name id)
                           :placeholder placeholder
                           :max-length max-length
                           :class (when validation "is-invalid")
                           :defaultValue value
                           :on-change (comp on-change event-value)}]]))

(defn texta-field
  [{:keys [validation on-change] :as opts}]
  (let [id (:field/id opts)
        placeholder (localized (:field/placeholder opts))
        value (:field/value opts)
        max-length (:field/max-length opts)]
    [basic-field opts
     [textarea {:id (id-to-name id)
                :name (id-to-name id)
                :placeholder placeholder
                :max-length max-length
                :class (if validation "form-control is-invalid" "form-control")
                :defaultValue value
                :on-change (comp on-change event-value)}]]))

(defn date-field
  [{:keys [min max validation on-change] :as opts}]
  (let [id (:field/id opts)
        value (:field/value opts)]
    ;; TODO: format readonly value in user locale (give basic-field a formatted :value and :previous-value in opts)
    [basic-field opts
     [:input.form-control {:type "date"
                           :id (id-to-name id)
                           :name (id-to-name id)
                           :class (when validation "is-invalid")
                           :defaultValue value
                           :min min
                           :max max
                           :on-change (comp on-change event-value)}]]))

(defn- option-label [value options]
  (let [label (->> options
                   (filter #(= value (:key %)))
                   first
                   :label)]
    (localized label)))

(defn option-field [{:keys [validation on-change] :as opts}]
  (let [id (:field/id opts)
        value (:field/value opts)
        options (:field/options opts)]
    [basic-field
     (assoc opts :readonly-component [readonly-field {:id (id-to-name id)
                                                      :value (option-label value options)}])
     (into [:select.form-control {:id (id-to-name id)
                                  :name (id-to-name id)
                                  :class (when validation "is-invalid")
                                  :defaultValue value
                                  :on-change (comp on-change event-value)}
            [:option {:value ""}]]
           (for [{:keys [key label]} options]
             [:option {:value key}
              (localized label)]))]))

(defn label [opts]
  (let [title (:field/title opts)]
    [:div.form-group
     [:label (localized title)]]))

;; TODO move to util?
(defn normalize-option-key
  "Strips disallowed characters from an option key"
  [key]
  (str/replace key #"\s+" ""))

(defn encode-option-keys
  "Encodes a set of option keys to a string"
  [keys]
  (->> keys
       sort
       (str/join " ")))

(defn decode-option-keys
  "Decodes a set of option keys from a string"
  [value]
  (-> value
      (str/split #"\s+")
      set
      (disj "")))

(defn multiselect-field [{:keys [validation on-change] :as opts}]
  (let [id (:field/id opts)
        value (:field/value opts)
        options (:field/options opts)
        selected-keys (decode-option-keys value)]
    ;; TODO: for accessibility these checkboxes would be best wrapped in a fieldset
    [basic-field
     (assoc opts :readonly-component [readonly-field {:id (id-to-name id)
                                                      :value (->> options
                                                                  (filter #(contains? selected-keys (:key %)))
                                                                  (map #(localized (:label %)))
                                                                  (str/join ", "))}])
     (into [:div]
           (for [{:keys [key label]} options]
             (let [option-id (str (id-to-name id) "-" key)
                   on-change (fn [event]
                               (let [checked (.. event -target -checked)
                                     selected-keys (if checked
                                                     (conj selected-keys key)
                                                     (disj selected-keys key))]
                                 (on-change (encode-option-keys selected-keys))))]
               [:div.form-check
                [:input.form-check-input {:type "checkbox"
                                          :id option-id
                                          :name option-id
                                          :class (when validation "is-invalid")
                                          :value key
                                          :checked (contains? selected-keys key)
                                          :on-change on-change}]
                [:label.form-check-label {:for option-id}
                 (localized label)]])))]))

;; TODO: custom :diff-component, for example link to both old and new attachment
(defn attachment-field
  [{:keys [validation app-id on-change on-set-attachment on-remove-attachment] :as opts}]
  (let [id (:field/id opts)
        title (localized (:field/title opts))
        value (:field/value opts)
        click-upload (fn [e] (when-not (:readonly opts) (.click (.getElementById js/document (id-to-name id)))))
        filename-field [:div.field
                        [:a.btn.btn-secondary.mr-2
                         {:href (str "/api/applications/attachments?application-id=" app-id "&field-id=" id)
                          :target :_new}
                         value " " (external-link)]]
        upload-field [:div.upload-file.mr-2
                      [:input {:style {:display "none"}
                               :type "file"
                               :id (id-to-name id)
                               :name (id-to-name id)
                               :accept ".pdf, .doc, .docx, .ppt, .pptx, .txt, image/*"
                               :class (when validation "is-invalid")
                               :on-change (fn [event]
                                            (let [filecontent (aget (.. event -target -files) 0)
                                                  filename (.-name filecontent)
                                                  form-data (doto (js/FormData.)
                                                              (.append "file" filecontent))]
                                              (on-change filename)
                                              (on-set-attachment form-data title)))}]
                      [:button.btn.btn-secondary {:on-click click-upload}
                       (text :t.form/upload)]]
        remove-button [:button.btn.btn-secondary.mr-2
                       {:on-click (fn [event]
                                    (on-change "")
                                    (on-remove-attachment (text :t.form/attachment-remove)))}
                       (text :t.form/attachment-remove)]]
    [basic-field (assoc opts :readonly-component (if (empty? value)
                                                   [:span]
                                                   filename-field))
     (if (empty? value)
       upload-field
       [:div {:style {:display :flex :justify-content :flex-start}}
        filename-field
        remove-button])]))
