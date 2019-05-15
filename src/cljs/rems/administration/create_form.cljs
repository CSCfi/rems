(ns rems.administration.create-form
  (:require [clojure.string :as str]
            [goog.string :refer [parseInt]]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [checkbox localized-text-field radio-button-group text-field]]
            [rems.administration.items :as items]
            [rems.atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.fields :as fields]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text text-format]]
            [rems.util :refer [dispatch! post! normalize-option-key parse-int]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:db (assoc db ::form {:fields []})}))

;;;; form state

;; TODO rename item->field
(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-event-db ::add-form-field (fn [db [_]] (update-in db [::form :fields] items/add {:type "text"})))
(rf/reg-event-db ::remove-form-field (fn [db [_ field-index]] (update-in db [::form :fields] items/remove field-index)))
(rf/reg-event-db ::move-form-field-up (fn [db [_ field-index]] (update-in db [::form :fields] items/move-up field-index)))
(rf/reg-event-db ::move-form-field-down (fn [db [_ field-index]] (update-in db [::form :fields] items/move-down field-index)))

(rf/reg-event-db
 ::add-form-field-option
 (fn [db [_ field-index]]
   (update-in db [::form :fields field-index :options] items/add {})))

(rf/reg-event-db
 ::remove-form-field-option
 (fn [db [_ field-index option-index]]
   (update-in db [::form :fields field-index :options] items/remove option-index)))

(rf/reg-event-db
 ::move-form-field-option-up
 (fn [db [_ field-index option-index]]
   (update-in db [::form :fields field-index :options] items/move-up option-index)))

(rf/reg-event-db
 ::move-form-field-option-down
 (fn [db [_ field-index option-index]]
   (update-in db [::form :fields field-index :options] items/move-down option-index)))


;;;; form submit

(defn- supports-optional? [field]
  (not= "label" (:type field)))

(defn- supports-input-prompt? [field]
  (contains? #{"text" "texta" "description"} (:type field)))

(defn- supports-maxlength? [field]
  (contains? #{"text" "texta"} (:type field)))

(defn- supports-options? [field]
  (contains? #{"option" "multiselect"} (:type field)))

(defn- localized-string? [lstr languages]
  (and (= (set (keys lstr))
          (set languages))
       (every? string? (vals lstr))))

(defn- valid-required-localized-string? [lstr languages]
  (and (localized-string? lstr languages)
       (every? #(not (str/blank? %))
               (vals lstr))))

(defn- valid-optional-localized-string? [lstr languages]
  (and (localized-string? lstr languages)
       ;; partial translations are not allowed
       (or (every? #(not (str/blank? %))
                   (vals lstr))
           (every? str/blank?
                   (vals lstr)))))

(defn- valid-option? [option languages]
  (and (not (str/blank? (:key option)))
       (valid-required-localized-string? (:label option) languages)))

(defn- valid-request-field? [field languages]
  (and (valid-required-localized-string? (:title field) languages)
       (not (str/blank? (:type field)))
       (if (supports-optional? field)
         (boolean? (:optional field))
         (nil? (:optional field)))
       (if (supports-input-prompt? field)
         (valid-optional-localized-string? (:input-prompt field) languages)
         (nil? (:input-prompt field)))
       (if (supports-maxlength? field)
         (not (neg? (:maxlength field)))
         (nil? (:maxlength field)))
       (if (supports-options? field)
         (every? #(valid-option? % languages) (:options field))
         (nil? (:options field)))))

(defn- valid-request? [request languages]
  (and (not (str/blank? (:organization request)))
       (not (str/blank? (:title request)))
       (every? #(valid-request-field? % languages) (:fields request))))

(defn build-localized-string [lstr languages]
  (into {} (for [language languages]
             [language (get lstr language "")])))

(defn- build-request-field [field languages]
  (merge {:title (build-localized-string (:title field) languages)
          :type (:type field)}
         (when (supports-optional? field)
           {:optional (boolean (:optional field))})
         (when (supports-input-prompt? field)
           {:input-prompt (build-localized-string (:input-prompt field) languages)})
         (when (supports-maxlength? field)
           {:maxlength (parse-int (:maxlength field))})
         (when (supports-options? field)
           {:options (for [{:keys [key label]} (:options field)]
                       {:key key
                        :label (build-localized-string label languages)})})))

(defn build-request [form languages]
  (let [request {:organization (:organization form)
                 :title (:title form)
                 :fields (mapv #(build-request-field % languages) (:fields form))}]
    (when (valid-request? request languages)
      request)))

(rf/reg-event-fx
 ::create-form
 (fn [_ [_ request]]
   (status-modal/common-pending-handler! (text :t.administration/create-form))
   (post! "/api/forms/create" {:params request
                               :handler (partial status-modal/common-success-handler! #(dispatch! (str "#/administration/forms/" (:id %))))
                               :error-handler status-modal/common-error-handler!})
   {}))


;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(defn- form-organization-field []
  [text-field context {:keys [:organization]
                       :label (text :t.administration/organization)
                       :placeholder (text :t.administration/organization-placeholder)}])

(defn- form-title-field []
  [text-field context {:keys [:title]
                       :label (text :t.create-form/title)}])

(defn- form-field-title-field [field-index]
  [localized-text-field context {:keys [:fields field-index :title]
                                 :label (text :t.create-form/field-title)}])

(defn- form-field-input-prompt-field [field-index]
  [localized-text-field context {:keys [:fields field-index :input-prompt]
                                 :label (text :t.create-form/input-prompt)}])

(defn- form-field-maxlength-field [field-index]
  [text-field context {:keys [:fields field-index :maxlength]
                       :label (text :t.create-form/maxlength)}])

(defn- add-form-field-option-button [field-index]
  [:a {:href "#"
       :on-click (fn [event]
                   (.preventDefault event)
                   (rf/dispatch [::add-form-field-option field-index]))}
   (text :t.create-form/add-option)])

(defn- remove-form-field-option-button [field-index option-index]
  [items/remove-button #(rf/dispatch [::remove-form-field-option field-index option-index])])

(defn- move-form-field-option-up-button [field-index option-index]
  [items/move-up-button #(rf/dispatch [::move-form-field-option-up field-index option-index])])

(defn- move-form-field-option-down-button [field-index option-index]
  [items/move-down-button #(rf/dispatch [::move-form-field-option-down field-index option-index])])

(defn- form-field-option-field [field-index option-index]
  [:div.form-field-option
   [:div.form-field-header
    [:h4 (text-format :t.create-form/option-n (inc option-index))]
    [:div.form-field-controls
     [move-form-field-option-up-button field-index option-index]
     [move-form-field-option-down-button field-index option-index]
     [remove-form-field-option-button field-index option-index]]]
   [text-field context {:keys [:fields field-index :options option-index :key]
                        :label (text :t.create-form/option-key)
                        :normalizer normalize-option-key}]
   [localized-text-field context {:keys [:fields field-index :options option-index :label]
                                  :label (text :t.create-form/option-label)}]])

(defn- form-field-option-fields [field-index]
  (let [form @(rf/subscribe [::form])]
    (into (into [:div]
                (for [option-index (range (count (get-in form [:fields field-index :options])))]
                  [form-field-option-field field-index option-index]))
          [[:div.form-field-option.new-form-field-option
            [add-form-field-option-button field-index]]])))

(defn- form-field-type-radio-group [field-index]
  [radio-button-group context {:id (str "radio-group-" field-index)
                               :keys [:fields field-index :type]
                               :orientation :vertical
                               :options [{:value "text", :label (text :t.create-form/type-text)}
                                         {:value "texta", :label (text :t.create-form/type-texta)}
                                         {:value "description", :label (text :t.create-form/type-description)}
                                         {:value "option", :label (text :t.create-form/type-option)}
                                         {:value "multiselect", :label (text :t.create-form/type-multiselect)}
                                         {:value "date", :label (text :t.create-form/type-date)}
                                         {:value "attachment", :label (text :t.create-form/type-attachment)}
                                         {:value "label", :label (text :t.create-form/type-label)}]}])

(defn- form-field-optional-checkbox [field-index]
  [checkbox context {:keys [:fields field-index :optional]
                     :label (text :t.create-form/optional)}])

(defn- add-form-field-button []
  [:a {:href "#"
       :on-click (fn [event]
                   (.preventDefault event)
                   (rf/dispatch [::add-form-field]))}
   (text :t.create-form/add-form-field)])

(defn- remove-form-field-button [field-index]
  [items/remove-button #(rf/dispatch [::remove-form-field field-index])])

(defn- move-form-field-up-button [field-index]
  [items/move-up-button #(rf/dispatch [::move-form-field-up field-index])])

(defn- move-form-field-down-button [field-index]
  [items/move-down-button #(rf/dispatch [::move-form-field-down field-index])])

(defn- save-form-button [on-click]
  (let [form @(rf/subscribe [::form])
        languages @(rf/subscribe [:languages])
        request (build-request form languages)]
    [:button.btn.btn-primary
     {:on-click #(on-click request)
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:on-click #(dispatch! "/#/administration/forms")}
   (text :t.administration/cancel)])

(defn- form-fields [fields]
  (into [:div]
        (map-indexed (fn [field-index field]
                       [:div.form-field {:key field-index}
                        [:div.form-field-header
                         [:h4 (text-format :t.create-form/field-n (inc field-index))]
                         [:div.form-field-controls
                          [move-form-field-up-button field-index]
                          [move-form-field-down-button field-index]
                          [remove-form-field-button field-index]]]

                        [form-field-title-field field-index]
                        [form-field-type-radio-group field-index]
                        (when (supports-optional? field)
                          [form-field-optional-checkbox field-index])
                        (when (supports-input-prompt? field)
                          [form-field-input-prompt-field field-index])
                        (when (supports-maxlength? field)
                          [form-field-maxlength-field field-index])
                        (when (supports-options? field)
                          [form-field-option-fields field-index])])
                     fields)))

(defn- form-field-to-application-field
  "Convert a field from the form create model to the application view model."
  [field]
  (merge {:field/type (keyword (:type field))
          :field/title (:title field)}
         (when (supports-optional? field)
           {:field/optional (:optional field)})
         (when (supports-input-prompt? field)
           {:field/placeholder (:input-prompt field)})
         (when (supports-maxlength? field)
           {:field/max-length (parse-int (:maxlength field))})
         (when (supports-options? field)
           {:field/options (:options field)})))

(defn- field-preview [field]
  [fields/field (form-field-to-application-field field)])

(defn form-preview [form]
  [collapsible/component
   {:id "preview-form"
    :title (text :t.administration/preview)
    :always (into [:div]
                  (for [field (:fields form)]
                    [field-preview field]))}])

(defn create-form-page []
  (let [form @(rf/subscribe [::form])]
    [:div
     [administration-navigator-container]
     [document-title (text :t.administration/create-form)]
     [:div.container-fluid.editor-content
      [:div.row
       [:div.col-lg
        [collapsible/component
         {:id "create-form"
          :title (text :t.administration/create-form)
          :always [:div
                   [form-organization-field]
                   [form-title-field]
                   [form-fields (:fields form)]

                   [:div.form-field.new-form-field
                    [add-form-field-button]]

                   [:div.col.commands
                    [cancel-button]
                    [save-form-button #(rf/dispatch [::create-form %])]]]}]]
       [:div.col-lg
        [form-preview form]]]]]))
