(ns rems.fields
  "UI components for form fields"
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :refer [add-symbol attachment-link close-symbol textarea success-symbol]]
            [rems.common.attachment-types :as attachment-types]
            [rems.common.util :refer [getx]]
            [rems.dropdown :as dropdown]
            [rems.guide-utils :refer [lipsum-short lipsum-paragraphs]]
            [rems.common.roles :as roles]
            [rems.text :refer [localized text text-format]]
            [rems.util :refer [encode-option-keys decode-option-keys focus-when-collapse-opened linkify]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn field-name [field]
  (str "form-" (getx field :form/id) "-field-" (getx field :field/id)))

(defn info-collapse
  "Collapse field from Bootstrap that shows extra information about input fields.

  `:info-id` - id of the element being described
  `:aria-label-text` - text describing aria-label of collapse, see more https://developers.google.com/web/fundamentals/accessibility/semantics-aria/aria-labels-and-relationships
  `:focus-when-collapse-opened` - element that is focused when the info is opened
  `:body-text` - component that is shown if open"
  [{:keys [info-id aria-label-text focus-when-collapse-opened body-text]}]
  [:<> [:button.info-button.btn.btn-link
        {:data-toggle "collapse"
         :href (str "#" (str info-id "-collapse"))
         :aria-label aria-label-text
         :aria-expanded "false"
         :aria-controls (str info-id "-collapse")}
        [:i.fa.fa-info-circle]]
   [:div.info-collapse.collapse {:id (str info-id "-collapse")
                                 :ref focus-when-collapse-opened
                                 :tab-index "-1"}
    body-text]])


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

(defn- toggle-diff-button [diff-visible on-toggle-diff]
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
  :form/id - number (required), form id
  :field/title - string (required), field title to show to the user
  :field/max-length - maximum number of characters (optional)
  :field/optional - boolean, true if the field is not required
  :field/value - string, the current value of the field
  :field/previous-value - string, the previously submitted value of the field
  :field/info-text - text for collapsable info field
  :readonly - boolean, true if the field should not be editable
  :readonly-component - HTML, custom component for a readonly field
  :diff - boolean, true if should show the diff between :value and :previous-value
  :diff-component - HTML, custom component for rendering a diff
  :validation - validation errors
  :fieldset - boolean, true if the field should be wrapped in a fieldset

  editor-component - HTML, form component for editing the field"
  [{:keys [readonly readonly-component diff diff-component validation on-toggle-diff fieldset] :as opts} editor-component]
  (let [raw-title (localized (:field/title opts))
        title (linkify raw-title)
        optional (:field/optional opts)
        value (:field/value opts)
        previous-value (:field/previous-value opts)
        max-length (:field/max-length opts)
        info-text (linkify (localized (:field/info-text opts)))
        collapse-aria-label (str (text :t.create-form/collapse-aria-label) raw-title)]
    ;; TODO: simplify fieldset code
    [(if fieldset
       :fieldset.form-group.field
       :div.form-group.field)
     (merge
      {:id (str "container-" (field-name opts))}
      (when fieldset
        {:tab-index -1
         :aria-required (not optional)
         :aria-invalid (when validation true)
         :aria-describedby (when validation
                             (str (field-name opts) "-error"))}))
     [(if fieldset
        :legend.application-field-label
        :label.application-field-label)
      (when (not fieldset)
        {:for (field-name opts)})
      title " "
      (when max-length
        (text-format :t.form/maxlength (str max-length)))
      " "
      (if optional
        (text :t.form/optional)
        (text :t.form/required))
      (when info-text
        [info-collapse
         {:info-id (field-name opts)
          :aria-label-text collapse-aria-label
          :focus-when-collapse-opened focus-when-collapse-opened
          :body-text info-text}])]
     (when (and previous-value
                (not= value previous-value))
       [toggle-diff-button diff on-toggle-diff])
     (cond
       diff (or diff-component
                [diff-field {:id (field-name opts)
                             :value value
                             :previous-value previous-value}])
       readonly (or readonly-component
                    [readonly-field {:id (field-name opts)
                                     :value value}])
       :else editor-component)
     (when validation
       [:div.invalid-feedback
        {:id (str (field-name opts) "-error")
         ;; XXX: Bootstrap's has "display: none" on .invalid-feedback by default
         ;;      and overrides that for example when there is a sibling .form-control.is-invalid,
         ;;      but that doesn't work with checkbox groups nor attachments, and we anyways
         ;;      don't need the feature of hiding this div with CSS when it has no content.
         :style {:display "block"}}
        (text-format (:type validation) raw-title)])]))

(defn- non-field-wrapper [opts children]
  [:div.form-group
   {:id (str "container-" (field-name opts))}
   children])

(defn- event-value [event]
  (.. event -target -value))

(defn text-field
  [{:keys [validation on-change info-text] :as opts}]
  (let [placeholder (localized (:field/placeholder opts))
        value (:field/value opts)
        optional (:field/optional opts)
        max-length (:field/max-length opts)]
    [field-wrapper opts
     [:input.form-control {:type "text"
                           :id (field-name opts)
                           :name (field-name opts)
                           :placeholder placeholder
                           :required (not optional)
                           :aria-invalid (when validation true)
                           :aria-describedby (when validation
                                               (str (field-name opts) "-error"))
                           :max-length max-length
                           :class (when validation "is-invalid")
                           :value value
                           :on-change (comp on-change event-value)}]]))

(defn texta-field
  [{:keys [validation on-change] :as opts}]
  (let [placeholder (localized (:field/placeholder opts))
        value (:field/value opts)
        optional (:field/optional opts)
        max-length (:field/max-length opts)]
    [field-wrapper opts
     [textarea {:id (field-name opts)
                :name (field-name opts)
                :placeholder placeholder
                :required (not optional)
                :aria-invalid (when validation true)
                :aria-describedby (when validation
                                    (str (field-name opts) "-error"))
                :max-length max-length
                :class (when validation "is-invalid")
                :value value
                :on-change (comp on-change event-value)}]]))

(defn date-field
  [{:keys [min max validation on-change] :as opts}]
  (let [value (:field/value opts)
        optional (:field/optional opts)]
    ;; TODO: format readonly value in user locale (give field-wrapper a formatted :value and :previous-value in opts)
    [field-wrapper opts
     [:input.form-control {:type "date"
                           :id (field-name opts)
                           :name (field-name opts)
                           :class (when validation "is-invalid")
                           :value value
                           :required (not optional)
                           :aria-invalid (when validation true)
                           :aria-describedby (when validation
                                               (str (field-name opts) "-error"))
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
  (let [value (:field/value opts)
        options (:field/options opts)
        optional (:field/optional opts)]
    [field-wrapper
     (assoc opts :readonly-component [readonly-field {:id (field-name opts)
                                                      :value (option-label value options)}])
     (into [:select.form-control {:id (field-name opts)
                                  :name (field-name opts)
                                  :class (when validation "is-invalid")
                                  :value value
                                  :required (not optional)
                                  :aria-invalid (when validation true)
                                  :aria-describedby (when validation
                                                      (str (field-name opts) "-error"))
                                  :on-change (comp on-change event-value)}
            [:option {:value ""}]]
           (for [{:keys [key label]} options]
             [:option {:value key}
              (localized label)]))]))

(defn label [opts]
  (let [title (localized (:field/title opts))]
    [non-field-wrapper opts [:label title]]))

(defn multiselect-field [{:keys [validation on-change] :as opts}]
  (let [value (:field/value opts)
        options (:field/options opts)
        selected-keys (decode-option-keys value)]
    [field-wrapper
     (assoc opts
            :fieldset true
            :readonly-component [readonly-field {:id (field-name opts)
                                                 :value (->> options
                                                             (filter #(contains? selected-keys (:key %)))
                                                             (map #(localized (:label %)))
                                                             (str/join ", "))}])
     (into [:div]
           (for [{:keys [key label]} options]
             (let [option-id (str (field-name opts) "-" key)
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

(defn upload-button [id on-upload]
  (let [upload-id (str id "-input")
        info-id (str id "-info")]
    [:div.upload-file.mr-2
     [:input {:style {:display "none"}
              :type "file"
              :id upload-id
              :name upload-id
              :accept attachment-types/allowed-extensions-string
              :on-change (fn [event]
                           (let [filecontent (aget (.. event -target -files) 0)
                                 form-data (doto (js/FormData.)
                                             (.append "file" filecontent))]
                             (on-upload form-data)))}]
     [:button.btn.btn-outline-secondary
      {:id id
       :type :button
       :on-click (fn [e] (.click (.getElementById js/document upload-id)))}
      [add-symbol]
      " "
      (text :t.form/upload)]
     [info-collapse
      {:info-id info-id
       :aria-label-text (text :t.form/upload-extensions)
       :focus-when-collapse-opened focus-when-collapse-opened
       :body-text [:span [text :t.form/upload-extensions]
                   ": "
                   attachment-types/allowed-extensions-string]}]]))

(defn multi-attachment-view [{:keys [key attachments on-attach on-remove-attachment]}]
  [:div.form-group
   (into [:<>]
         (for [attachment attachments]
           [:div.flex-row.d-flex.flex-wrap.mb-2
            [attachment-link attachment]
            [:button.btn.btn-outline-secondary.mr-2
             {:class (str "remove-attachment-" key)
              :type :button
              :on-click (fn [event]
                          (on-remove-attachment (:attachment/id attachment)))}
             [close-symbol]
             " "
             (text :t.form/attachment-remove)]]))
   [upload-button (str "upload-" key) on-attach]])

(defn attachment-row [attachments]
  (into [:div.flex-row.d-flex.flex-wrap]
        (for [att attachments]
          [attachment-link att])))

(defn attachment-field
  [{:keys [validation on-attach on-remove-attachment success] :as opts}]
  [field-wrapper (assoc opts
                        :readonly-component [attachment-row (:field/attachments opts)]
                        :diff-component [:div {:style {:display :flex}}
                                         [:div
                                          (text :t.form/previous-value) ": "
                                          [attachment-row (:field/previous-attachments opts)]]
                                         [:div
                                          (text :t.form/current-value) ": "
                                          [attachment-row (:field/attachments opts)]]])
   [multi-attachment-view {:key (field-name opts)
                           :attachments (:field/attachments opts)
                           :on-attach on-attach
                           :on-remove-attachment on-remove-attachment}]])

(defn header-field [opts]
  (let [title (localized (:field/title opts))]
    [non-field-wrapper opts [:h3 title]]))

(defn organization-field [{:keys [id value on-change readonly]}]
  (let [organizations @(rf/subscribe [:owned-organizations])
        language @(rf/subscribe [:language])
        item-selected? #(= (:organization/id %) (:organization/id value))
        disallowed (roles/disallow-setting-organization? @(rf/subscribe [:roles]))]
    [:div.form-group
     [:label {:for id} (text :t.administration/organization)]
     (if (or readonly disallowed)
       [readonly-field {:id id
                        :value (get-in value [:organization/name language])}]
       [dropdown/dropdown
        {:id id
         :items (->> organizations (filter :enabled) (remove :archived))
         :item-key :organization/id
         :item-label (comp language :organization/name)
         :item-selected? item-selected?
         :on-change on-change}])]))


(defn unsupported-field
  [f]
  [:p.alert.alert-warning "Unsupported field " (pr-str f)])

(def ^:private field-defaults
  {:on-change (fn [_] nil)})

;; TODO: check if this and common.form/field-types could be combined
(defn field [field]
  (let [f (merge field-defaults field)]
    (case (:field/type f)
      :attachment [attachment-field f]
      :date [date-field f]
      :description [text-field f]
      :email [text-field f]
      :header [header-field f]
      :label [label f]
      :multiselect [multiselect-field f]
      :option [option-field f]
      :text [text-field f]
      :texta [texta-field f]
      [unsupported-field f])))

;;;; Guide

(defn guide []
  [:div
   (component-info multi-attachment-view)
   (example "no attachments"
            [multi-attachment-view {:key "action-guide-example-1"
                                    :attachment nil
                                    :on-attach (fn [_] nil)}])
   (example "multiple attachments"
            [multi-attachment-view {:key "action-guide-example-1"
                                    :attachments [{:attachment/filename "attachment.xlsx"}
                                                  {:attachment/filename "data.pdf"}]
                                    :on-attach (fn [_] nil)}])
   (example "multiple attachments, long filenames"
            [multi-attachment-view {:key "action-guide-example-1"
                                    :attachments [{:attachment/filename "this_is_the_very_very_very_long_filename_of_a_test_file_the_file_itself_is_quite_short_though_abcdefghijklmnopqrstuvwxyz0123456789_overflow_overflow_overflow.txt"}
                                                  {:attachment/filename "this_is_another_very_very_very_long_filename_of_another_test_file_the_file_itself_is_quite_short_though_abcdefghijklmnopqrstuvwxyz0123456789_overflow_overflow_overflow.txt"}]
                                    :on-attach (fn [_] nil)}])
   (component-info field)
   (example "field of type \"text\""
            [field {:form/id 1
                    :field/id "1"
                    :field/type :text
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}}])
   (example "field of type \"text\" with info field"
            [field {:form/id 1
                    :field/id "1"
                    :field/type :text
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}
                    :field/info-text {:en "Extra information about the field, \n
                                           maybe it even contains a link, such as https://en.wikipedia.org/wiki/Igor_Stravinsky
                                           \n
                                           or https://en.wikipedia.org/wiki/Dmitri_Shostakovich"}}])
   (example "field of type \"text\" with maximum length"
            [field {:form/id 2
                    :field/id "1"
                    :field/type :text
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}
                    :field/max-length 10}])
   (example "field of type \"text\" with validation error and link in title"
            [field {:form/id 3
                    :field/id "1"
                    :field/type :text
                    :field/title {:en "Title http://google.com"}
                    :field/placeholder {:en "placeholder"}
                    :validation {:type :t.form.validation/required}}])
   (example "non-editable field of type \"text\" without text"
            [field {:form/id 4
                    :field/id "1"
                    :field/type :text
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}
                    :readonly true}])
   (example "non-editable field of type \"text\" with text"
            [field {:form/id 5
                    :field/id "1"
                    :field/type :text
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}
                    :readonly true
                    :field/value lipsum-short}])
   (example "field of type \"texta\""
            [field {:form/id 6
                    :field/id "1"
                    :field/type :texta
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}}])
   (example "field of type \"texta\" with maximum length"
            [field {:form/id 7
                    :field/id "1"
                    :field/type :texta
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}
                    :field/max-length 10}])
   (example "field of type \"texta\" with validation error"
            [field {:form/id 8
                    :field/id "1"
                    :field/type :texta
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}
                    :validation {:type :t.form.validation/required}}])
   (example "non-editable field of type \"texta\""
            [field {:form/id 9
                    :field/id "1"
                    :field/type :texta
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}
                    :readonly true
                    :field/value lipsum-paragraphs}])
   (let [previous-lipsum-paragraphs (-> lipsum-paragraphs
                                        (str/replace "ipsum primis in faucibus orci luctus" "eu mattis purus mi eu turpis")
                                        (str/replace "per inceptos himenaeos" "justo erat hendrerit magna"))]
     [:div
      (example "editable field of type \"texta\" with previous value, diff hidden"
               [field {:form/id 10
                       :field/id "1"
                       :field/type :texta
                       :field/title {:en "Title"}
                       :field/placeholder {:en "placeholder"}
                       :field/value lipsum-paragraphs
                       :field/previous-value previous-lipsum-paragraphs}])
      (example "editable field of type \"texta\" with previous value, diff shown"
               [field {:form/id 11
                       :field/id "1"
                       :field/type :texta
                       :field/title {:en "Title"}
                       :field/placeholder {:en "placeholder"}
                       :field/value lipsum-paragraphs
                       :field/previous-value previous-lipsum-paragraphs
                       :diff true}])
      (example "non-editable field of type \"texta\" with previous value, diff hidden"
               [field {:form/id 12
                       :field/id "1"
                       :field/type :texta
                       :field/title {:en "Title"}
                       :field/placeholder {:en "placeholder"}
                       :readonly true
                       :field/value lipsum-paragraphs
                       :field/previous-value previous-lipsum-paragraphs}])
      (example "non-editable field of type \"texta\" with previous value, diff shown"
               [field {:form/id 13
                       :field/id "1"
                       :field/type :texta
                       :field/title {:en "Title"}
                       :field/placeholder {:en "placeholder"}
                       :readonly true
                       :field/value lipsum-paragraphs
                       :field/previous-value previous-lipsum-paragraphs
                       :diff true}])
      (example "non-editable field of type \"texta\" with previous value equal to current value"
               [field {:form/id 14
                       :field/id "1"
                       :field/type :texta
                       :field/title {:en "Title"}
                       :field/placeholder {:en "placeholder"}
                       :readonly true
                       :field/value lipsum-paragraphs
                       :field/previous-value lipsum-paragraphs}])])
   (example "field of type \"attachment\""
            [field {:app-id 5
                    :form/id 15
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}}])
   (example "field of type \"attachment\", two files uploaded"
            [field {:app-id 5
                    :form/id 16
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :field/value "123"
                    :field/attachments [{:attachment/id 123
                                         :attachment/filename "test.txt"}
                                        {:attachment/id 456
                                         :attachment/filename "second.pdf"}]}])
   (example "field of type \"attachment\", file uploaded, long filename"
            [field {:app-id 5
                    :form/id 16
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :field/value "123"
                    :field/attachments [{:attachment/id 123
                                         :attachment/filename "this_is_the_very_very_very_long_filename_of_a_test_file_the_file_itself_is_quite_short_though_abcdefghijklmnopqrstuvwxyz0123456789_overflow_overflow_overflow.txt"}]}])
   (example "field of type \"attachment\", file uploaded, success indicator"
            [field {:app-id 5
                    :form/id 17
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :field/value "123"
                    :field/attachments [{:attachment/id 123
                                         :attachment/filename "test.txt"}]
                    :success true}])
   (example "field of type \"attachment\", previous and new file uploaded, diff shown"
            [field {:app-id 5
                    :form/id 18
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :field/value "123"
                    :field/previous-value "456"
                    :field/attachments [{:attachment/id 123
                                         :attachment/filename "new.txt"}
                                        {:attachment/id 456
                                         :attachment/filename "new2.txt"}]
                    :field/previous-attachments [{:attachment/id 789
                                                  :attachment/filename "old.txt"}]
                    :diff true}])
   (example "field of type \"attachment\", previous and new file uploaded, diff hidden"
            [field {:app-id 5
                    :form/id 19
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :field/value "123"
                    :field/previous-value "456"
                    :field/attachments [{:attachment/id 123
                                         :attachment/filename "new.txt"}
                                        {:attachment/id 456
                                         :attachment/filename "new2.txt"}]
                    :field/previous-attachment [{:attachment/id 456
                                                 :attachment/filename "old.txt"}]}])
   (example "field of type \"attachment\", lots of files, diff shown"
            [field {:app-id 5
                    :form/id 18
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :field/value "123"
                    :field/previous-value "456"
                    :field/attachments (repeat 20 {:attachment/id 123
                                                   :attachment/filename "new.txt"})
                    :field/previous-attachments (repeat 20 {:attachment/id 789
                                                            :attachment/filename "old.txt"})
                    :diff true}])
   (example "field of type \"attachment\", previous file uploaded, new deleted, diff shown"
            [field {:app-id 5
                    :form/id 20
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :field/previous-value "456"
                    :field/previous-attachments [{:attachment/id 456
                                                  :attachment/filename "old.txt"}]
                    :diff true}])
   (example "field of type \"attachment\", previous file uploaded, new deleted, diff hidden"
            [field {:app-id 5
                    :form/id 21
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :field/previous-value "456"
                    :field/previous-attachments [{:attachment/id 456
                                                  :attachment/filename "old.txt"}]}])
   (example "non-editable field of type \"attachment\""
            [field {:app-id 5
                    :form/id 22
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :readonly true}])
   (example "non-editable field of type \"attachment\", files uploaded"
            [field {:app-id 5
                    :form/id 23
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :readonly true
                    :field/value "123"
                    :field/attachments [{:attachment/id 123
                                         :attachment/filename "test.txt"}
                                        {:attachment/id 456
                                         :attachment/filename "second.pdf"}]}])
   (example "non-editable field of type \"attachment\", many many files"
            [field {:app-id 5
                    :form/id 23
                    :field/id "6"
                    :field/type :attachment
                    :field/title {:en "Title"}
                    :readonly true
                    :field/value "123"
                    :field/attachments (repeat 100 {:attachment/id 123
                                                    :attachment/filename "test.txt"})}])
   (example "field of type \"date\""
            [field {:form/id 24
                    :field/id "1"
                    :field/type :date
                    :field/title {:en "Title"}}])
   (example "field of type \"date\" with value"
            [field {:form/id 25
                    :field/id "1"
                    :field/type :date
                    :field/title {:en "Title"}
                    :field/value "2000-12-31"}])
   (example "non-editable field of type \"date\""
            [field {:form/id 26
                    :field/id "1"
                    :field/type :date
                    :field/title {:en "Title"}
                    :readonly true
                    :field/value ""}])
   (example "non-editable field of type \"date\" with value"
            [field {:form/id 27
                    :field/id "1"
                    :field/type :date
                    :field/title {:en "Title"}
                    :readonly true
                    :field/value "2000-12-31"}])
   (example "field of type \"option\""
            [field {:form/id 28
                    :field/id "1"
                    :field/type :option
                    :field/title {:en "Title"}
                    :field/value "y"
                    :field/options [{:key "y" :label {:en "Yes" :fi "Kyllä"}}
                                    {:key "n" :label {:en "No" :fi "Ei"}}]}])
   (example "non-editable field of type \"option\""
            [field {:form/id 29
                    :field/id "1"
                    :field/type :option
                    :field/title {:en "Title"}
                    :field/value "y"
                    :readonly true
                    :field/options [{:key "y" :label {:en "Yes" :fi "Kyllä"}}
                                    {:key "n" :label {:en "No" :fi "Ei"}}]}])
   (example "field of type \"multiselect\""
            [field {:form/id 30
                    :field/id "1"
                    :field/type :multiselect
                    :field/title {:en "Title"}
                    :field/value "egg bacon"
                    :field/options [{:key "egg" :label {:en "Egg" :fi "Munaa"}}
                                    {:key "bacon" :label {:en "Bacon" :fi "Pekonia"}}
                                    {:key "spam" :label {:en "Spam" :fi "Lihasäilykettä"}}]}])
   (example "non-editable field of type \"multiselect\""
            [field {:form/id 31
                    :field/id "1"
                    :field/type :multiselect
                    :field/title {:en "Title"}
                    :field/value "egg bacon"
                    :readonly true
                    :field/options [{:key "egg" :label {:en "Egg" :fi "Munaa"}}
                                    {:key "bacon" :label {:en "Bacon" :fi "Pekonia"}}
                                    {:key "spam" :label {:en "Spam" :fi "Lihasäilykettä"}}]}])
   (example "optional field"
            [field {:form/id 32
                    :field/id "1"
                    :field/type :texta
                    :field/optional true
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}}])
   (example "field of type \"label\""
            [field {:form/id 33
                    :field/id "1"
                    :field/type :label
                    :field/title {:en "Lorem ipsum dolor sit amet"}}])
   (example "field of type \"description\""
            [field {:form/id 34
                    :field/id "1"
                    :field/type :description
                    :field/title {:en "Title"}
                    :field/placeholder {:en "placeholder"}}])])
