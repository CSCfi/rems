(ns rems.fields
  "UI components for form fields"
  (:require [clojure.string :as str]
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
