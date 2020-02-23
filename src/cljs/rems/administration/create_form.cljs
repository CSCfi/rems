(ns rems.administration.create-form
  (:require [clojure.string :as str]
            [goog.string :refer [parseInt]]
            [medley.core :refer [find-first]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [checkbox localized-text-field radio-button-group text-field]]
            [rems.administration.items :as items]
            [rems.atoms :as atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.common.form :refer [field-visible? generate-field-id validate-form-template] :as common-form]
            [rems.common.util :refer [parse-int]]
            [rems.dropdown :as dropdown]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.focus :as focus]
            [rems.roles :as roles]
            [rems.spinner :as spinner]
            [rems.text :refer [text text-format]]
            [rems.util :refer [navigate! fetch put! post! normalize-option-key trim-when-string visibility-ratio focus-input-field]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} [_ form-id edit-form?]]
   (let [roles (get-in db [:identity :roles])
         user-organization (get-in db [:identity :user :organization])
         all-organizations (get-in db [:config :organizations])
         organization (cond
                        (roles/disallow-setting-organization? roles)
                        user-organization

                        (= (count all-organizations) 1)
                        (first all-organizations)

                        :else
                        nil)]
     {:db (assoc db
                 ::form (merge {:form/fields []}
                               (when organization
                                 {:form/organization organization}))
                 ::form-errors nil
                 ::form-id form-id
                 ::edit-form? edit-form?
                 ::organization-read-only? (not (nil? organization)))
      :dispatch-n [[::fetch-form form-id]]})))

(rf/reg-event-fx
 ::fetch-form
 (fn [{:keys [db]} [_ form-id]]
   (when form-id
     (fetch (str "/api/forms/" form-id)
            {:handler #(rf/dispatch [::fetch-form-result %])
             :error-handler (flash-message/default-error-handler :top "Fetch form")})
     {:db (assoc db ::loading-form? true)})))

(rf/reg-event-db
 ::fetch-form-result
 (fn [db [_ form]]
   (-> db
       (assoc ::form form)
       (dissoc ::loading-form?))))

;;;; form state

(defn- field-editor-id [id]
  (str "field-editor-" id))

(defn- field-editor-selector [id index]
  (str "#" (field-editor-id id) "[data-field-index='" index "']"))

(defn- track-moved-field-editor! [id index button-selector]
  (when-some [element (js/document.getElementById (field-editor-id id))]
    (let [before (.getBoundingClientRect element)]
      ;; NB: the element exists already but we wait for it to reappear with the new index
      (focus/on-element-appear (field-editor-selector id index)
                               (fn [element]
                                 (let [after (.getBoundingClientRect element)]
                                   (focus/scroll-offset before after)
                                   (focus/focus-without-scroll (.querySelector element button-selector))))))))

(defn- focus-field-editor! [id]
  (let [selector "textarea"] ;; focus first title field
    (focus/on-element-appear (str "#" (field-editor-id id))
                             (fn [element]
                               (focus/scroll-to-top element)
                               (.focus (.querySelector element selector))))))

(rf/reg-sub ::form (fn [db _]
                     (-> (::form db)
                         (update :form/fields #(vec (map-indexed (fn [i field] (assoc field :field/index i)) %))))))
(rf/reg-sub ::form-errors (fn [db _] (::form-errors db)))
(rf/reg-sub ::loading-form? (fn [db _] (::loading-form? db)))
(rf/reg-sub ::edit-form? (fn [db _] (::edit-form? db)))
(rf/reg-sub ::organization-read-only? (fn [db _] (::organization-read-only? db)))
(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-sub ::selected-organization (fn [db _] (get-in db [::form :form/organization])))
(rf/reg-event-db ::set-selected-organization (fn [db [_ organization]] (assoc-in db [::form :form/organization] organization)))

(rf/reg-event-db
 ::add-form-field
 (fn [db [_ & [index]]]
   (let [new-item (merge (generate-field-id (get-in db [::form :form/fields]))
                         {:field/type :text})]
     (focus-field-editor! (:field/id new-item))
     (update-in db [::form :form/fields] items/add new-item index))))

(rf/reg-event-db
 ::remove-form-field
 (fn [db [_ field-index]]
   (update-in db [::form :form/fields] items/remove field-index)))

(rf/reg-event-db
 ::move-form-field-up
 (fn [db [_ field-index]]
   (track-moved-field-editor! (get-in db [::form :form/fields field-index :field/id])
                              (dec field-index)
                              ".move-up")
   (update-in db [::form :form/fields] items/move-up field-index)))

(rf/reg-event-db
 ::move-form-field-down
 (fn [db [_ field-index]]
   (track-moved-field-editor! (get-in db [::form :form/fields field-index :field/id])
                              (inc field-index)
                              ".move-down")
   (update-in db [::form :form/fields] items/move-down field-index)))

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

(defn- localized-field-title [field lang]
  (get-in field [:field/title lang]))

(defn build-localized-string [lstr languages]
  (into {} (for [language languages]
             [language (trim-when-string (get lstr language ""))])))

(defn- build-request-field [field languages]
  (merge {:field/id (:field/id field)
          :field/title (build-localized-string (:field/title field) languages)
          :field/type (:field/type field)
          :field/optional (if (common-form/supports-optional? field)
                            (boolean (:field/optional field))
                            false)}
         (when (common-form/supports-placeholder? field)
           {:field/placeholder (build-localized-string (:field/placeholder field) languages)})
         (when (common-form/supports-max-length? field)
           {:field/max-length (parse-int (:field/max-length field))})
         (when (common-form/supports-options? field)
           {:field/options (for [{:keys [key label]} (:field/options field)]
                             {:key key
                              :label (build-localized-string label languages)})})
         (when (common-form/supports-privacy? field)
           (when (= :private (get-in field [:field/privacy]))
             {:field/privacy (:field/privacy field)}))
         (when (common-form/supports-visibility? field)
           (when (= :only-if (get-in field [:field/visibility :visibility/type]))
             {:field/visibility (select-keys (:field/visibility field)
                                             [:visibility/type :visibility/field :visibility/values])}))))

(defn build-request [form languages]
  {:form/organization (:form/organization form)
   :form/title (trim-when-string (:form/title form))
   :form/fields (mapv #(build-request-field % languages) (:form/fields form))})

;;;; form validation

(defn- page-title [edit-form?]
  (if edit-form?
    (text :t.administration/edit-form)
    (text :t.administration/create-form)))

(rf/reg-event-fx
 ::send-form
 (fn [{:keys [db]} [_]]
   (let [edit? (::edit-form? db)
         form-errors (validate-form-template (::form db) (:languages db))
         send-verb (if edit? put! post!)
         send-url (str "/api/forms/" (if edit?
                                       "edit"
                                       "create"))
         description [page-title edit?]
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
                               (navigate! (str "/administration/forms/"
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
  (when (and frame element)
    (let [element-top (- (.-offsetTop element) (.-offsetTop frame))
          element-height (true-height element)
          top-margin (/ (.-offsetHeight frame) 4)
          position (+ element-top element-height (* -1 ratio element-height) (- top-margin))]
      (.scrollTo frame 0 position))))

(defn first-partially-visible-edit-field []
  (let [fields (array-seq (.querySelectorAll js/document "#create-form .form-field:not(.new-form-field)"))
        visibility? #(<= 0 (-> % .getBoundingClientRect .-bottom))]
    (first (filter visibility? fields))))

(defn autoscroll []
  (when-let [edit-field (first-partially-visible-edit-field)]
    (let [id (last (str/split (.-id edit-field) #"-"))
          preview-frame (.querySelector js/document "#preview-form-contents")
          preview-field (-> js/document
                            (.getElementById (str "field-preview-" id)))
          ratio (visibility-ratio edit-field)]
      (set-visibility-ratio preview-frame preview-field ratio))))

(defn enable-autoscroll! []
  (set! (.-onscroll js/window) autoscroll))

;;;; UI

(def ^:private context
  {:get-form ::form
   :get-form-errors ::form-errors
   :update-form ::set-form-field})

 (def ^:private organization-dropdown-id "organization-dropdown")

(defn- form-organization-field []
  (let [organizations (:organizations @(rf/subscribe [:rems.config/config]))
        selected-organization @(rf/subscribe [::selected-organization])
        item-selected? #(= % selected-organization)
        readonly @(rf/subscribe [::organization-read-only?])]
    [:div.form-group
     [:label {:for organization-dropdown-id} (text :t.administration/organization)]
     (if readonly
       [fields/readonly-field {:id organization-dropdown-id
                               :value selected-organization}]
       [dropdown/dropdown
        {:id organization-dropdown-id
         :items organizations
         :item-selected? item-selected?
         :on-change #(rf/dispatch [::set-selected-organization %])}])]))

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

(defn- form-fields-that-can-be-used-in-visibility [form]
  (filter #(contains? {:option :multiselect} (:field/type %))
          (:form/fields form)))

(defn- form-field-values [form field-id]
  (let [field (find-first (comp #{field-id} :field/id) (:form/fields form))]
    (case (:field/type field)
      :option (let [options (:field/options field)]
                (map (fn [o] {:value (:key o)
                              :title (:label o)})
                     options))
      [])))

(rf/reg-event-db
 ::form-field-visibility-type
 (fn [db [_ field-index visibility-type]]
   (assoc-in db [::form :form/fields field-index :field/visibility :visibility/type] visibility-type)))

(rf/reg-event-db
 ::form-field-visibility-field
 (fn [db [_ field-index visibility-field]]
   (assoc-in db [::form :form/fields field-index :field/visibility :visibility/field] visibility-field)))

(rf/reg-event-db
 ::form-field-visibility-value
 (fn [db [_ field-index visibility-value]]
   (assoc-in db [::form :form/fields field-index :field/visibility :visibility/values] visibility-value)))

(defn- form-field-visibility
  "Component for specifying form field visibility rules"
  [field-index]
  (let [form @(rf/subscribe [::form])
        form-errors @(rf/subscribe [::form-errors])
        error-type (get-in form-errors [:form/fields field-index :field/visibility :visibility/type])
        error-field (get-in form-errors [:form/fields field-index :field/visibility :visibility/field])
        error-value (get-in form-errors [:form/fields field-index :field/visibility :visibility/values])
        lang @(rf/subscribe [:language])
        id-type (str "fields-" field-index "-visibility-type")
        id-field (str "fields-" field-index "-visibility-field")
        id-value (str "fields-" field-index "-visibility-value")
        label-type (text :t.create-form/type-visibility)
        label-field (text :t.create-form.visibility/field)
        label-value (text :t.create-form.visibility/has-value)
        visibility (get-in form [:form/fields field-index :field/visibility])
        visibility-type (:visibility/type visibility)
        visibility-field (:visibility/field visibility)
        visibility-value (:visibility/values visibility)]
    [:div {:class (when (= :only-if visibility-type) "form-field-visibility")}
     [:div.form-group.field {:id (str "container-field" field-index)}
      [:label {:for id-type} label-type]
      " "
      [:select.form-control
       {:id id-type
        :class (when error-type "is-invalid")
        :on-change #(rf/dispatch [::form-field-visibility-type field-index (keyword (.. % -target -value))])
        :value (or visibility-type "")}
       [:option {:value "always"} (text :t.create-form.visibility/always)]
       [:option {:value "only-if"} (text :t.create-form.visibility/only-if)]]
      [:div.invalid-feedback
       (when error-type (text-format error-type label-type))]]
     (when (= :only-if visibility-type)
       [:<>
        [:div.form-group
         [:label {:for id-field} label-field]
         [:select.form-control
          {:id id-field
           :class (when error-field "is-invalid")
           :on-change #(rf/dispatch [::form-field-visibility-field field-index {:field/id (.. % -target -value)}])
           :value (or (:field/id visibility-field) "")}
          ^{:key "not-selected"} [:option ""]
          (for [field (form-fields-that-can-be-used-in-visibility form)]
            ^{:key (str field-index "-" (:field/id field))}
            [:option {:value (:field/id field)}
             (text-format :t.create-form/field-n (inc (:field/index field)) (localized-field-title field lang))])]
         [:div.invalid-feedback
          (when error-field (text-format error-field label-field))]]
        (when (:field/id visibility-field)
          [:div.form-group
           [:label {:for id-value} label-value]
           [:select.form-control
            {:id id-value
             :class (when error-value "is-invalid")
             :on-change #(rf/dispatch [::form-field-visibility-value field-index [(.. % -target -value)]])
             :value (or (first visibility-value) "")}
            ^{:key "not-selected"} [:option ""]
            (for [value (form-field-values form (:field/id visibility-field))]
              ^{:key (str field-index "-" (:value value))}
              [:option {:value (:value value)} (get-in value [:title lang])])]
           [:div.invalid-feedback
            (when error-value (text-format error-value label-value))]])])]))

(rf/reg-event-db
 ::form-field-privacy
 (fn [db [_ field-index privacy]]
   (assoc-in db [::form :form/fields field-index :field/privacy] privacy)))

(defn- form-field-privacy
  "Component for specifying form field privacy rules.

  Privacy concerns the reviewers as they can see only public fields."
  [field-index]
  (let [form @(rf/subscribe [::form])
        form-errors @(rf/subscribe [::form-errors])
        error (get-in form-errors [:form/fields field-index :field/privacy])
        lang @(rf/subscribe [:language])
        id (str "fields-" field-index "-privacy-type")
        label (text :t.create-form/type-privacy)
        privacy (get-in form [:form/fields field-index :field/privacy])]
    [:div.form-group.field {:id (str "container-field" field-index)}
     [:label {:for id} label]
     " "
     [:select.form-control
      {:id id
       :class (when error "is-invalid")
       :on-change #(rf/dispatch [::form-field-privacy field-index (keyword (.. % -target -value))])
       :value (or privacy "public")}
      [:option {:value "public"} (text :t.create-form.privacy/public)]
      [:option {:value "private"} (text :t.create-form.privacy/private)]]]))

(defn- form-field-type-radio-group [field-index]
  [radio-button-group context {:id (str "radio-group-" field-index)
                               :keys [:form/fields field-index :field/type]
                               :label (text :t.create-form/field-type)
                               :orientation :horizontal
                               :options [{:value :description :label (text :t.create-form/type-description)}
                                         {:value :text :label (text :t.create-form/type-text)}
                                         {:value :texta :label (text :t.create-form/type-texta)}
                                         {:value :option :label (text :t.create-form/type-option)}
                                         {:value :multiselect :label (text :t.create-form/type-multiselect)}
                                         {:value :date :label (text :t.create-form/type-date)}
                                         {:value :email :label (text :t.create-form/type-email)}
                                         {:value :attachment :label (text :t.create-form/type-attachment)}
                                         {:value :label :label (text :t.create-form/type-label)}
                                         {:value :header :label (text :t.create-form/type-header)}]}])

(defn- form-field-optional-checkbox [field-index]
  [checkbox context {:keys [:form/fields field-index :field/optional]
                     :label (text :t.create-form/optional)}])

(defn- add-form-field-button [index]
  [:a {:href "#"
       :on-click (fn [event]
                   (.preventDefault event)
                   (rf/dispatch [::add-form-field index]))}
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
   "/administration/forms"
   (text :t.administration/cancel)])

(defn- format-validation-link [target content]
  [:li [:a {:href "#" :on-click (focus-input-field target)}
        content]])

(defn- format-field-validation [field field-errors]
  (let [field-index (:field/index field)
        lang @(rf/subscribe [:language])]
    [:li (text-format :t.create-form/field-n (inc field-index) (localized-field-title field lang))
     (into [:ul]
           (concat
            (for [[lang error] (:field/title field-errors)]
              (format-validation-link (str "fields-" field-index "-title-" (name lang))
                                      (text-format error (str (text :t.create-form/field-title)
                                                              " (" (.toUpperCase (name lang)) ")"))))
            (for [[lang error] (:field/placeholder field-errors)]
              (format-validation-link (str "fields-" field-index "-placeholder-" (name lang))
                                      (text-format error (str (text :t.create-form/placeholder)
                                                              " (" (.toUpperCase (name lang)) ")"))))
            (when (:field/max-length field-errors)
              [(format-validation-link (str "fields-" field-index "-max-length")
                                       (str (text :t.create-form/maxlength) ": " (text (:field/max-length field-errors))))])
            (when (-> field-errors :field/visibility :visibility/type)
              [(format-validation-link (str "fields-" field-index "-visibility-type")
                                       (str (text :t.create-form/type-visibility) ": " (text-format (-> field-errors :field/visibility :visibility/type) (text :t.create-form/type-visibility))))])
            (when (-> field-errors :field/visibility :visibility/field)
              [(format-validation-link (str "fields-" field-index "-visibility-field")
                                       (str (text :t.create-form/type-visibility) ": " (text-format (-> field-errors :field/visibility :visibility/field) (text :t.create-form.visibility/field))))])
            (when (-> field-errors :field/visibility :visibility/values)
              [(format-validation-link (str "fields-" field-index "-visibility-value")
                                       (str (text :t.create-form/type-visibility) ": " (text-format (-> field-errors :field/visibility :visibility/values) (text :t.create-form.visibility/has-value))))])
            (for [[option-id option-errors] (into (sorted-map) (:field/options field-errors))]
              [:li (text-format :t.create-form/option-n (inc option-id))
               [:ul
                (when (:key option-errors)
                  (format-validation-link (str "fields-" field-index "-options-" option-id "-key")
                                          (text-format (:key option-errors) (text :t.create-form/option-key))))
                (into [:<>]
                      (for [[lang error] (:label option-errors)]
                        (format-validation-link (str "fields-" field-index "-options-" option-id "-label-" (name lang))
                                                (text-format error (str (text :t.create-form/option-label)
                                                                        " (" (.toUpperCase (name lang)) ")")))))]])))]))

(defn- format-validation-errors [form-errors form lang]
  ;; TODO: deduplicate with field definitions
  (into [:ul
         (when (:form/organization form-errors)
           [:li [:a {:href "#" :on-click (focus-input-field "organization")}
                 (text-format (:form/organization form-errors) (text :t.administration/organization))]])

         (when (:form/title form-errors)
           [:li [:a {:href "#" :on-click (focus-input-field "title")}
                 (text-format (:form/title form-errors) (text :t.create-form/title))]])]

        (for [[field-index field-errors] (into (sorted-map) (:form/fields form-errors))]
          (let [field (get-in form [:form/fields field-index])]
            [format-field-validation field field-errors lang]))))

(defn- validation-errors-summary []
  (let [form @(rf/subscribe [::form])
        errors @(rf/subscribe [::form-errors])
        lang @(rf/subscribe [:language])]
    (when errors
      [:div.alert.alert-danger (text :t.actions.errors/submission-failed)
       [format-validation-errors errors form lang]])))

(defn- form-fields [fields]
  (into [:div
         [:div.form-field.new-form-field
          [add-form-field-button 0]]]

        (for [{index :field/index :as field} fields]
          [:<>
           [:div.form-field {:id (field-editor-id (:field/id field))
                             :key index
                             :data-field-index index}
            [:div.form-field-header
             [:h3 (text-format :t.create-form/field-n (inc index) (localized-field-title field @(rf/subscribe [:language])))]
             [:div.form-field-controls
              [move-form-field-up-button index]
              [move-form-field-down-button index]
              [remove-form-field-button index]]]

            [form-field-title-field index]
            [form-field-type-radio-group index]
            (when (common-form/supports-optional? field)
              [form-field-optional-checkbox index])
            (when (common-form/supports-placeholder? field)
              [form-field-placeholder-field index])
            (when (common-form/supports-max-length? field)
              [form-field-max-length-field index])
            (when (common-form/supports-options? field)
              [form-field-option-fields index])
            (when (common-form/supports-privacy? field)
              [form-field-privacy index])
            (when (common-form/supports-visibility? field)
              [form-field-visibility index])]

           [:div.form-field.new-form-field
            [add-form-field-button (inc index)]]])))

(rf/reg-event-db
 ::set-field-value
 (fn [db [_ field-id field-value]]
   (assoc-in db [::preview field-id] field-value)))

(rf/reg-sub
 ::preview
 (fn [db _]
   (::preview db {})))

(defn form-preview [form]
  (let [preview @(rf/subscribe [::preview])
        lang @(rf/subscribe [:language])]
    [collapsible/component
     {:id "preview-form"
      :title (text :t.administration/preview)
      :always (into [:div#preview-form-contents]
                    (for [field (:form/fields form)]
                      [:div.field-preview {:id (str "field-preview-" (:field/id field))}
                       [fields/field (assoc field
                                            :on-change #(rf/dispatch [::set-field-value (:form/id form) (:field/id field) %])
                                            :field/value (get-in preview [(:field/id field)]))]
                       (when-not (field-visible? field preview)
                         [:div {:style {:position :absolute
                                        :top 0
                                        :left 0
                                        :right 0
                                        :bottom 0
                                        :z-index 1
                                        :display :flex
                                        :flex-direction :column
                                        :justify-content :center
                                        :align-items :flex-end
                                        :border-radius "0.4rem"
                                        :margin "-0.5rem"
                                        :background-color "rgba(230,230,230,0.5)"}}
                          [:div.pr-4 (text :t.create-form.visibility/hidden)]])]))}]))

(defn create-form-page []
  (enable-autoscroll!)
  (let [form @(rf/subscribe [::form])
        edit-form? @(rf/subscribe [::edit-form?])
        loading-form? @(rf/subscribe [::loading-form?])]
    [:div
     [administration/navigator]
     [document-title (page-title edit-form?)]
     [flash-message/component :top]
     (if loading-form?
       [:div [spinner/big]]
       [:div.container-fluid
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
                     [:div.col.commands
                      [cancel-button]
                      [save-form-button #(rf/dispatch [::send-form])]]]}]]
         [:div.col-lg
          [form-preview form]]]])]))
