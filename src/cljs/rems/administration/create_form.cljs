(ns rems.administration.create-form
  (:require [clojure.string :as str]
            [goog.string :refer [parseInt]]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [checkbox localized-text-field radio-button-group text-field]]
            [rems.administration.items :as items]
            [rems.administration.status-flags :as status-flags]
            [rems.atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.fields :as fields]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [text text-format]]
            [rems.util :refer [dispatch! fetch put! post! normalize-option-key parse-int remove-empty-keys]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ form-id edit-form?]]
   {:db (assoc db
               ::form {:fields []}
               ::form-id form-id
               ::edit-form? edit-form?)
    :dispatch-n [[::fetch-form form-id]]}))

(rf/reg-event-fx
 ::fetch-form
 (fn [{:keys [db]} [_ form-id]]
   (when form-id
     (fetch (str "/api/forms/" form-id)
            {:handler #(rf/dispatch [::fetch-form-result %])})
     {:db (assoc db ::loading-form? true)})))

(rf/reg-event-db
 ::fetch-form-result
 (fn [db [_ form]]
   (-> db
       (assoc ::form form)
       (dissoc ::loading-form?))))

;;;; form state

;; TODO rename item->field
(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-sub ::form-errors (fn [db _] (::form-errors db)))
(rf/reg-sub ::loading-form? (fn [db _] (::loading-form? db)))
(rf/reg-sub ::edit-form? (fn [db _] (::edit-form? db)))
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

(defn build-localized-string [lstr languages]
  (into {} (for [language languages]
             [language (get lstr language "")])))

(defn- build-request-field [field languages]
  (merge {:title (build-localized-string (:title field) languages)
          :type (:type field)
          :optional (if (supports-optional? field)
                      (boolean (:optional field))
                      false)}
         (when (supports-input-prompt? field)
           {:input-prompt (build-localized-string (:input-prompt field) languages)})
         (when (supports-maxlength? field)
           {:maxlength (parse-int (:maxlength field))})
         (when (supports-options? field)
           {:options (for [{:keys [key label]} (:options field)]
                       {:key key
                        :label (build-localized-string label languages)})})))

(defn build-request [form languages]
  {:organization (:organization form)
   :title (:title form)
   :fields (mapv #(build-request-field % languages) (:fields form))})

;;;; form validation

(defn- validate-text-field [m key]
  (when (str/blank? (get m key))
    {key :t.form.validation/required}))

(defn- validate-localized-text-field [m key languages]
  {key (apply merge (mapv #(validate-text-field (get m key) %) languages))})

(defn- validate-optional-localized-field [m key languages]
  (let [validated (mapv #(validate-text-field (get m key) %) languages)]
    ;; partial translations are not allowed
    (when (not-empty (remove identity validated))
      {key (apply merge validated)})))

(def ^:private maxlength-range [0 32767])

(defn- validate-maxlength [maxlength]
  (when-not (str/blank? maxlength)
    (let [parsed (parse-int maxlength)]
      (when (or (nil? parsed)
                (not (<= (first maxlength-range) parsed (second maxlength-range))))
        {:maxlength :t.form.validation/invalid-value}))))

(defn- validate-option [option id languages]
  {id (merge (validate-text-field option :key)
             (validate-localized-text-field option :label languages))})

(defn- validate-options [options languages]
  {:options (apply merge (mapv #(validate-option %1 %2 languages) options (range)))})

(defn- validate-field [field id languages]
  {id (merge (validate-text-field field :type)
             (validate-localized-text-field field :title languages)
             (validate-optional-localized-field field :input-prompt languages)
             (validate-maxlength (:maxlength field))
             (validate-options (:options field) languages))})

(defn validate-form [form languages]
  (-> (merge (validate-text-field form :organization)
             (validate-text-field form :title)
             {:fields (apply merge (mapv #(validate-field %1 %2 languages) (form :fields) (range)))})
      remove-empty-keys))

(defn- page-title [edit-form?]
  (if edit-form?
    (text :t.administration/edit-form)
    (text :t.administration/create-form)))

(rf/reg-event-fx
 ::send-form
 (fn [{:keys [db]} [_]]
   (let [edit? (db ::edit-form?)
         form-errors (validate-form (db ::form) (db ::languages))
         send-verb (if edit? put! post!)]
     (when (empty? form-errors)
       (status-modal/common-pending-handler! (page-title edit?))
       (send-verb (str "/api/forms/"
                       (if edit?
                         (str (db ::form-id) "/edit")
                         "create"))
                  {:params (build-request (db ::form) (db ::languages))
                   :handler (partial status-modal/common-success-handler! #(dispatch! (str "#/administration/forms/" (:id %))))
                   :error-handler status-modal/common-error-handler!})
      {:db (assoc db ::form-errors form-errors)}))))

;;;; UI

(def ^:private context
  {:get-form ::form
   :get-form-errors ::form-errors
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
  [:button.btn.btn-primary
   {:type :button
    :on-click on-click}
   (text :t.administration/save)])

(defn- cancel-button []
  [:button.btn.btn-secondary
   {:button "type"
    :on-click #(dispatch! "/#/administration/forms")}
   (text :t.administration/cancel)])

(defn- form-fields [fields]
  (into [:div]
        (map-indexed (fn [field-index field]
                       [:div.form-field {:key field-index}
                        [:div.form-field-header
                         [:h3 (text-format :t.create-form/field-n (inc field-index))]
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
  (let [form @(rf/subscribe [::form])
        edit-form? @(rf/subscribe [::edit-form?])
        loading-form? @(rf/subscribe [::loading-form?])]
    [:div
     [administration-navigator-container]
     [document-title (page-title edit-form?)]
     (if loading-form?
       [:div [spinner/big]]
       [:div.container-fluid.editor-content
        [:div.row
         [:div.col-lg
          [collapsible/component
           {:id "create-form"
            :title (page-title edit-form?)
            :always [:div
                     [form-organization-field]
                     [form-title-field]
                     [form-fields (:fields form)]

                     [:div.form-field.new-form-field
                      [add-form-field-button]]

                     [:div.col.commands
                      [cancel-button]
                      [save-form-button #(rf/dispatch ::send-form)]]]}]]
         [:div.col-lg
          [form-preview form]]]])]))
