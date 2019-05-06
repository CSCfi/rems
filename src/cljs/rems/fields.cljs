(ns rems.fields
  "UI components for form fields"
  (:require [clojure.string :as str]
            [cljs-time.core :as time]
            [rems.atoms :refer [external-link textarea]]
            [rems.guide-utils :refer [lipsum-short lipsum-paragraphs]]
            [rems.text :refer [localized text text-format localize-time]]
            [rems.util :refer [encode-option-keys decode-option-keys linkify]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

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
  [:div.form-control {:id id} (linkify (str/trim (str value)))])

(defn field-wrapper
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
    [field-wrapper opts
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
    [field-wrapper opts
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
    ;; TODO: format readonly value in user locale (give field-wrapper a formatted :value and :previous-value in opts)
    [field-wrapper opts
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
    [field-wrapper
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

(defn multiselect-field [{:keys [validation on-change] :as opts}]
  (let [id (:field/id opts)
        value (:field/value opts)
        options (:field/options opts)
        selected-keys (decode-option-keys value)]
    ;; TODO: for accessibility these checkboxes would be best wrapped in a fieldset
    [field-wrapper
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
        filename (get-in opts [:field/attachment :attachment/filename])
        click-upload (fn [e] (when-not (:readonly opts) (.click (.getElementById js/document (id-to-name id)))))
        filename-field [:div.field
                        [:a.btn.btn-secondary.mr-2
                         {:href (str "/api/applications/attachment/" value)
                          :target :_new}
                         filename " " (external-link)]]
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
                                              (on-change (str filename " (" (localize-time (time/now)) ")"))
                                              (on-set-attachment form-data title)))}]
                      [:button.btn.btn-secondary {:on-click click-upload}
                       (text :t.form/upload)]]
        remove-button [:button.btn.btn-secondary.mr-2
                       {:on-click (fn [event]
                                    (on-change "")
                                    (on-remove-attachment))}
                       (text :t.form/attachment-remove)]]
    [field-wrapper (assoc opts :readonly-component (if (empty? value)
                                                     [:span]
                                                     filename-field))
     (if (empty? value)
       upload-field
       [:div {:style {:display :flex :justify-content :flex-start}}
        filename-field
        remove-button])]))

(defn unsupported-field
  [f]
  [:p.alert.alert-warning "Unsupported field " (pr-str f)])

(def ^:private field-defaults
  {:on-change (fn [_] nil)})

(defn field [field]
  (let [f (merge field-defaults field)]
    (case (:field/type f)
      :attachment [attachment-field f]
      :date [date-field f]
      :description [text-field f]
      :label [label f]
      :multiselect [multiselect-field f]
      :option [option-field f]
      :text [text-field f]
      :texta [texta-field f]
      [unsupported-field f])))

;;;; Guide

(defn guide []
  [:div
   (component-info field)
   (example "field of type \"text\""
            [:form
             [field {:field/type :text
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}}]])
   (example "field of type \"text\" with maximum length"
            [:form
             [field {:field/type :text
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}
                     :field/max-length 10}]])
   (example "field of type \"text\" with validation error"
            [:form
             [field {:field/type :text
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}
                     :validation {:type :t.form.validation/required}}]])
   (example "non-editable field of type \"text\" without text"
            [:form
             [field {:field/type :text
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}
                     :readonly true}]])
   (example "non-editable field of type \"text\" with text"
            [:form
             [field {:field/type :text
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}
                     :readonly true
                     :field/value lipsum-short}]])
   (example "field of type \"texta\""
            [:form
             [field {:field/type :texta
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}}]])
   (example "field of type \"texta\" with maximum length"
            [:form
             [field {:field/type :texta
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}
                     :field/max-length 10}]])
   (example "field of type \"texta\" with validation error"
            [:form
             [field {:field/type :texta
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}
                     :validation {:type :t.form.validation/required}}]])
   (example "non-editable field of type \"texta\""
            [:form
             [field {:field/type :texta
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}
                     :readonly true
                     :field/value lipsum-paragraphs}]])
   (let [previous-lipsum-paragraphs (-> lipsum-paragraphs
                                        (str/replace "ipsum primis in faucibus orci luctus" "eu mattis purus mi eu turpis")
                                        (str/replace "per inceptos himenaeos" "justo erat hendrerit magna"))]
     [:div
      (example "editable field of type \"texta\" with previous value, diff hidden"
               [:form
                [field {:field/type :texta
                        :field/title {:en "Title"}
                        :field/placeholder {:en "prompt"}
                        :field/value lipsum-paragraphs
                        :field/previous-value previous-lipsum-paragraphs}]])
      (example "editable field of type \"texta\" with previous value, diff shown"
               [:form
                [field {:field/type :texta
                        :field/title {:en "Title"}
                        :field/placeholder {:en "prompt"}
                        :field/value lipsum-paragraphs
                        :field/previous-value previous-lipsum-paragraphs
                        :diff true}]])
      (example "non-editable field of type \"texta\" with previous value, diff hidden"
               [:form
                [field {:field/type :texta
                        :field/title {:en "Title"}
                        :field/placeholder {:en "prompt"}
                        :readonly true
                        :field/value lipsum-paragraphs
                        :field/previous-value previous-lipsum-paragraphs}]])
      (example "non-editable field of type \"texta\" with previous value, diff shown"
               [:form
                [field {:field/type :texta
                        :field/title {:en "Title"}
                        :field/placeholder {:en "prompt"}
                        :readonly true
                        :field/value lipsum-paragraphs
                        :field/previous-value previous-lipsum-paragraphs
                        :diff true}]])
      (example "non-editable field of type \"texta\" with previous value equal to current value"
               [:form
                [field {:field/type :texta
                        :field/title {:en "Title"}
                        :field/placeholder {:en "prompt"}
                        :readonly true
                        :field/value lipsum-paragraphs
                        :field/previous-value lipsum-paragraphs}]])])
   (example "field of type \"attachment\""
            [:form
             [field {:app-id 5
                     :field/id 6
                     :field/type :attachment
                     :field/title {:en "Title"}}]])
   (example "field of type \"attachment\", file uploaded"
            [:form
             [field {:app-id 5
                     :field/id 6
                     :field/type :attachment
                     :field/title {:en "Title"}
                     :field/value "test.txt"}]])
   (example "field of type \"attachment\", previous and new file uploaded, diff shown"
            [:form
             [field {:app-id 5
                     :field/id 6
                     :field/type :attachment
                     :field/title {:en "Title"}
                     :field/value "new.txt"
                     :field/previous-value "old.txt"
                     :diff true}]])
   (example "field of type \"attachment\", previous and new file uploaded, diff hidden"
            [:form
             [field {:app-id 5
                     :field/id 6
                     :field/type :attachment
                     :field/title {:en "Title"}
                     :field/value "new.txt"
                     :field/previous-value "old.txt"}]])
   (example "field of type \"attachment\", previous file uploaded, new deleted, diff shown"
            [:form
             [field {:app-id 5
                     :field/id 6
                     :field/type :attachment
                     :field/title {:en "Title"}
                     :field/previous-value "old.txt"
                     :diff true}]])
   (example "field of type \"attachment\", previous file uploaded, new deleted, diff hidden"
            [:form
             [field {:app-id 5
                     :field/id 6
                     :field/type :attachment
                     :field/title {:en "Title"}
                     :field/previous-value "old.txt"}]])
   (example "non-editable field of type \"attachment\""
            [:form
             [field {:app-id 5
                     :field/id 6
                     :field/type :attachment
                     :field/title {:en "Title"}
                     :readonly true}]])
   (example "non-editable field of type \"attachment\", file uploaded"
            [:form
             [field {:app-id 5
                     :field/id 6
                     :field/type :attachment
                     :field/title {:en "Title"}
                     :readonly true
                     :field/value "test.txt"}]])
   (example "field of type \"date\""
            [:form
             [field {:field/type :date
                     :field/title {:en "Title"}}]])
   (example "field of type \"date\" with value"
            [:form
             [field {:field/type :date
                     :field/title {:en "Title"}
                     :field/value "2000-12-31"}]])
   (example "non-editable field of type \"date\""
            [:form
             [field {:field/type :date
                     :field/title {:en "Title"}
                     :readonly true
                     :field/value ""}]])
   (example "non-editable field of type \"date\" with value"
            [:form
             [field {:field/type :date
                     :field/title {:en "Title"}
                     :readonly true
                     :field/value "2000-12-31"}]])
   (example "field of type \"option\""
            [:form
             [field {:field/type :option
                     :field/title {:en "Title"}
                     :field/value "y"
                     :field/options [{:key "y" :label {:en "Yes" :fi "Kyllä"}}
                                     {:key "n" :label {:en "No" :fi "Ei"}}]}]])
   (example "non-editable field of type \"option\""
            [:form
             [field {:field/type :option
                     :field/title {:en "Title"}
                     :field/value "y"
                     :readonly true
                     :field/options [{:key "y" :label {:en "Yes" :fi "Kyllä"}}
                                     {:key "n" :label {:en "No" :fi "Ei"}}]}]])
   (example "field of type \"multiselect\""
            [:form
             [field {:field/type :multiselect
                     :field/title {:en "Title"}
                     :field/value "egg bacon"
                     :field/options [{:key "egg" :label {:en "Egg" :fi "Munaa"}}
                                     {:key "bacon" :label {:en "Bacon" :fi "Pekonia"}}
                                     {:key "spam" :label {:en "Spam" :fi "Lihasäilykettä"}}]}]])
   (example "non-editable field of type \"multiselect\""
            [:form
             [field {:field/type :multiselect
                     :field/title {:en "Title"}
                     :field/value "egg bacon"
                     :readonly true
                     :field/options [{:key "egg" :label {:en "Egg" :fi "Munaa"}}
                                     {:key "bacon" :label {:en "Bacon" :fi "Pekonia"}}
                                     {:key "spam" :label {:en "Spam" :fi "Lihasäilykettä"}}]}]])
   (example "optional field"
            [:form
             [field {:field/type :texta
                     :field/optional true
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}}]])
   (example "field of type \"label\""
            [:form
             [field {:field/type :label
                     :field/title {:en "Lorem ipsum dolor sit amet"}}]])
   (example "field of type \"description\""
            [:form
             [field {:field/type :description
                     :field/title {:en "Title"}
                     :field/placeholder {:en "prompt"}}]])])
