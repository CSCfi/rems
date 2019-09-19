(ns rems.administration.create-form
  (:require [clojure.string :as str]
            [goog.string :refer [parseInt]]
            [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.administration.components :refer [checkbox localized-text-field radio-button-group text-field]]
            [rems.administration.items :as items]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format]]
            [rems.util :refer [dispatch! fetch put! post! normalize-option-key parse-int remove-empty-keys visibility-ratio in-page-anchor-link]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ form-id edit-form?]]
   {:db (assoc db
               ::form {:form/fields []}
               ::form-errors nil
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
(rf/reg-sub ::form (fn [db _]
                     (-> (::form db)
                         (update :form/fields #(vec (map-indexed (fn [i field] (assoc field :field/id i)) %))))))
(rf/reg-sub ::form-errors (fn [db _] (::form-errors db)))
(rf/reg-sub ::loading-form? (fn [db _] (::loading-form? db)))
(rf/reg-sub ::edit-form? (fn [db _] (::edit-form? db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-event-db ::add-form-field (fn [db [_]] (update-in db [::form :form/fields] items/add {:field/type :text})))
(rf/reg-event-db ::remove-form-field (fn [db [_ field-index]] (update-in db [::form :form/fields] items/remove field-index)))
(rf/reg-event-db ::move-form-field-up (fn [db [_ field-index]] (update-in db [::form :form/fields] items/move-up field-index)))
(rf/reg-event-db ::move-form-field-down (fn [db [_ field-index]] (update-in db [::form :form/fields] items/move-down field-index)))

(rf/reg-event-db
 ::add-form-field-option
 (fn [db [_ field-index]]
   (update-in db [::form :form/fields field-index :field/options] items/add {})))

(rf/reg-event-db
 ::remove-form-field-option
 (fn [db [_ field-index option-index]]
   (update-in db [::form :form/fields field-index :field/options] items/remove option-index)))

(rf/reg-event-db
 ::move-form-field-option-up
 (fn [db [_ field-index option-index]]
   (update-in db [::form :form/fields field-index :field/options] items/move-up option-index)))

(rf/reg-event-db
 ::move-form-field-option-down
 (fn [db [_ field-index option-index]]
   (update-in db [::form :form/fields field-index :field/options] items/move-down option-index)))

;;;; form submit

(defn- supports-optional? [field]
  (not= :label (:field/type field)))

(defn- supports-placeholder? [field]
  (contains? #{:text :texta :description} (:field/type field)))

(defn- supports-max-length? [field]
  (contains? #{:description :text :texta} (:field/type field)))

(defn- supports-options? [field]
  (contains? #{:option :multiselect} (:field/type field)))

(defn build-localized-string [lstr languages]
  (into {} (for [language languages]
             [language (get lstr language "")])))

(defn- build-request-field [field languages]
  (merge {:field/title (build-localized-string (:field/title field) languages)
          :field/type (:field/type field)
          :field/optional (if (supports-optional? field)
                            (boolean (:field/optional field))
                            false)}
         (when (supports-placeholder? field)
           {:field/placeholder (build-localized-string (:field/placeholder field) languages)})
         (when (supports-max-length? field)
           {:field/max-length (parse-int (:field/max-length field))})
         (when (supports-options? field)
           {:field/options (for [{:keys [key label]} (:field/options field)]
                             {:key key
                              :label (build-localized-string label languages)})})))

(defn build-request [form languages]
  {:form/organization (:form/organization form)
   :form/title (:form/title form)
   :form/fields (mapv #(build-request-field % languages) (:form/fields form))})

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

(def ^:private max-length-range [0 32767])

(defn- validate-max-length [max-length]
  (when-not (str/blank? max-length)
    (let [parsed (parse-int max-length)]
      (when (or (nil? parsed)
                (not (<= (first max-length-range) parsed (second max-length-range))))
        {:field/max-length :t.form.validation/invalid-value}))))

(defn- validate-option [option id languages]
  {id (merge (validate-text-field option :key)
             (validate-localized-text-field option :label languages))})

(defn- validate-options [options languages]
  {:field/options (apply merge (mapv #(validate-option %1 %2 languages) options (range)))})

(defn- validate-field [field id languages]
  {id (merge (validate-text-field field :field/type)
             (validate-localized-text-field field :field/title languages)
             (when (supports-placeholder? field)
               (validate-optional-localized-field field :field/placeholder languages))
             (when (supports-max-length? field)
               (validate-max-length (:field/max-length field)))
             (when (supports-options? field)
               (validate-options (:field/options field) languages)))})

(defn- nil-if-empty [m]
  (when-not (empty? m)
    m))

(defn validate-form [form languages]
  (-> (merge (validate-text-field form :form/organization)
             (validate-text-field form :form/title)
             {:form/fields (apply merge (mapv #(validate-field %1 %2 languages) (:form/fields form) (range)))})
      remove-empty-keys
      nil-if-empty))

(defn- page-title [edit-form?]
  (if edit-form?
    (text :t.administration/edit-form)
    (text :t.administration/create-form)))

(rf/reg-event-fx
 ::send-form
 (fn [{:keys [db]} [_]]
   (let [edit? (::edit-form? db)
         form-errors (validate-form (::form db) (:languages db))
         send-verb (if edit? put! post!)
         send-url (str "/api/forms/" (if edit?
                                       "edit"
                                       "create"))
         description (page-title edit?)
         request (merge (build-request (::form db) (:languages db))
                        (when edit?
                          {:form/id (::form-id db)}))]
     (when-not form-errors
       (send-verb send-url
                  {:params request
                   :handler (flash-message/default-success-handler
                             :top
                             description
                             (fn [response]
                               (dispatch! (str "#/administration/forms/"
                                               (if edit?
                                                 (::form-id db)
                                                 (response :id))))))
                   :error-handler (flash-message/default-error-handler :top description)}))
     {:db (assoc db ::form-errors form-errors)})))

;;;; preview auto-scrolling

(defn true-height [element]
  (let [style (.getComputedStyle js/window element)]
    (+ (.-offsetHeight element)
       (js/parseInt (.-marginTop style))
       (js/parseInt (.-marginBottom style)))))

(defn set-visibility-ratio [frame element ratio]
  (let [element-top (- (.-offsetTop element) (.-offsetTop frame))
        element-height (true-height element)
        top-margin (/ (.-offsetHeight frame) 4)
        position (+ element-top element-height (* -1 ratio element-height) (- top-margin))]
    (.scrollTo frame 0 position)))

(defn first-partially-visible-edit-field []
  (let [fields (array-seq (.querySelectorAll js/document "#create-form .form-field"))
        visible? #(<= 0 (-> % .getBoundingClientRect .-bottom))]
    (first (filter visible? fields))))

(defn autoscroll []
  (when-let [edit-field (first-partially-visible-edit-field)]
    (let [id (.getAttribute edit-field "data-field-id")
          preview-frame (.querySelector js/document "#preview-form .collapse-content")
          preview-field (-> js/document
                            (.getElementById (str "container-field" id)))
          ratio (visibility-ratio edit-field)]
      (set-visibility-ratio preview-frame preview-field ratio))))

(defn enable-autoscroll! []
  (set! (.-onscroll js/window) autoscroll))

;;;; UI

(def ^:private context
  {:get-form ::form
   :get-form-errors ::form-errors
   :update-form ::set-form-field})

(defn- form-organization-field []
  [text-field context {:keys [:form/organization]
                       :label (text :t.administration/organization)
                       :placeholder (text :t.administration/organization-placeholder)}])

(defn- form-title-field []
  [text-field context {:keys [:form/title]
                       :label (text :t.create-form/title)}])

(defn- form-field-title-field [field-index]
  [localized-text-field context {:keys [:form/fields field-index :field/title]
                                 :label (text :t.create-form/field-title)}])

(defn- form-field-placeholder-field [field-index]
  [localized-text-field context {:keys [:form/fields field-index :field/placeholder]
                                 :label (text :t.create-form/placeholder)}])

(defn- form-field-max-length-field [field-index]
  [text-field context {:keys [:form/fields field-index :field/max-length]
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
   [text-field context {:keys [:form/fields field-index :field/options option-index :key]
                        :label (text :t.create-form/option-key)
                        :normalizer normalize-option-key}]
   [localized-text-field context {:keys [:form/fields field-index :field/options option-index :label]
                                  :label (text :t.create-form/option-label)}]])

(defn- form-field-option-fields [field-index]
  (let [form @(rf/subscribe [::form])]
    (into (into [:div]
                (for [option-index (range (count (get-in form [:form/fields field-index :field/options])))]
                  [form-field-option-field field-index option-index]))
          [[:div.form-field-option.new-form-field-option
            [add-form-field-option-button field-index]]])))

(defn- form-field-type-radio-group [field-index]
  [radio-button-group context {:id (str "radio-group-" field-index)
                               :keys [:form/fields field-index :field/type]
                               :label (text :t.create-form/field-type)
                               :orientation :vertical
                               :options [{:value :description, :label (text :t.create-form/type-description)}
                                         {:value :text, :label (text :t.create-form/type-text)}
                                         {:value :texta, :label (text :t.create-form/type-texta)}
                                         {:value :option, :label (text :t.create-form/type-option)}
                                         {:value :multiselect, :label (text :t.create-form/type-multiselect)}
                                         {:value :date, :label (text :t.create-form/type-date)}
                                         {:value :attachment, :label (text :t.create-form/type-attachment)}
                                         {:value :label, :label (text :t.create-form/type-label)}]}])

(defn- form-field-optional-checkbox [field-index]
  [checkbox context {:keys [:form/fields field-index :field/optional]
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
    :on-click (fn []
                (rf/dispatch [:rems.spa/user-triggered-navigation]) ;; scroll to top
                (on-click))}
   (text :t.administration/save)])

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   "/#/administration/forms"
   (text :t.administration/cancel)])

(defn- format-field-validation [field field-errors]
  (let [field-id (:field/id field)]
    [:li (text-format :t.create-form/field-n (inc field-id))
     (into [:ul]
           (concat
            (for [[lang error] (:field/title field-errors)]
              [:li [:a {:href "#"
                        :on-click (in-page-anchor-link (str "fields-" field-id "-title-" (name lang)))}
                    (text-format error (str (text :t.create-form/field-title)
                                            " (" (.toUpperCase (name lang)) ")"))]])
            (for [[lang error] (:field/placeholder field-errors)]
              [:li [:a {:href "#"
                        :on-click (in-page-anchor-link (str "fields-" field-id "-placeholder-" (name lang)))}
                    (text-format error (str (text :t.create-form/placeholder)
                                            " (" (.toUpperCase (name lang)) ")"))]])
            (when (:field/max-length field-errors)
              [[:li [:a {:href "#"
                         :on-click (in-page-anchor-link (str "fields-" field-id "-max-length"))}
                     (text :t.create-form/maxlength) ": " (text (:field/max-length field-errors))]]])
            (for [[option-id option-errors] (into (sorted-map) (:field/options field-errors))]
              [:li (text-format :t.create-form/option-n (inc option-id))
               [:ul
                (when (:key option-errors)
                  [:li [:a {:href "#"
                            :on-click (in-page-anchor-link (str "fields-" field-id "-options-" option-id "-key"))}
                        (text-format (:key option-errors) (text :t.create-form/option-key))]])
                (into [:<>]
                      (for [[lang error] (:label option-errors)]
                        [:li [:a {:href "#"
                                  :on-click (in-page-anchor-link (str "fields-" field-id "-options-" option-id "-label-" (name lang)))}
                              (text-format error (str (text :t.create-form/option-label)
                                                      " (" (.toUpperCase (name lang)) ")"))]]))]])))]))

(defn- format-validation-errors [form-errors form]
  ;; TODO: deduplicate with field definitions
  (into [:ul
         (when (:form/organization form-errors)
           [:li [:a {:href "#"
                     :on-click (in-page-anchor-link "organization")}
                 (text-format (:form/organization form-errors) (text :t.administration/organization))]])

         (when (:form/title form-errors)
           [:li [:a {:href "#"
                     :on-click (in-page-anchor-link "title")}
                 (text-format (:form/title form-errors) (text :t.create-form/title))]])]

        (for [[field-id field-errors] (into (sorted-map) (:form/fields form-errors))]
          (let [field (get-in form [:form/fields field-id])]
            [format-field-validation field field-errors]))))

(defn- validation-errors-summary []
  (let [form @(rf/subscribe [::form])
        errors @(rf/subscribe [::form-errors])]
    (when errors
      [:div.alert.alert-danger (text :t.actions.errors/submission-failed)
       [format-validation-errors errors form]])))

(defn- form-fields [fields]
  (into [:div]
        (for [{id :field/id :as field} fields]
          [:div.form-field {:key id
                            :data-field-id id}
           [:div.form-field-header
            [:h3 (text-format :t.create-form/field-n (inc id))]
            [:div.form-field-controls
             [move-form-field-up-button id]
             [move-form-field-down-button id]
             [remove-form-field-button id]]]

           [form-field-title-field id]
           [form-field-type-radio-group id]
           (when (supports-optional? field)
             [form-field-optional-checkbox id])
           (when (supports-placeholder? field)
             [form-field-placeholder-field id])
           (when (supports-max-length? field)
             [form-field-max-length-field id])
           (when (supports-options? field)
             [form-field-option-fields id])])))

(defn form-preview [form]
  [collapsible/component
   {:id "preview-form"
    :title (text :t.administration/preview)
    :always (into [:div#preview-form-contents]
                  (for [field (:form/fields form)]
                    [fields/field field]))}])

(defn create-form-page []
  (enable-autoscroll!)
  (let [form @(rf/subscribe [::form])
        edit-form? @(rf/subscribe [::edit-form?])
        loading-form? @(rf/subscribe [::loading-form?])]
    [:div
     [administration-navigator-container]
     [document-title (page-title edit-form?)]
     [flash-message/component :top]
     (if loading-form?
       [:div [spinner/big]]
       [:div.container-fluid.editor-content
        [validation-errors-summary]
        [:div.row
         [:div.col-lg
          [collapsible/component
           {:id "create-form"
            :title (page-title edit-form?)
            :always [:div
                     [form-organization-field]
                     [form-title-field]
                     [form-fields (:form/fields form)]

                     [:div.form-field.new-form-field
                      [add-form-field-button]]

                     [:div.col.commands
                      [cancel-button]
                      [save-form-button #(rf/dispatch [::send-form])]]]}]]
         [:div.col-lg
          [form-preview form]]]])]))
