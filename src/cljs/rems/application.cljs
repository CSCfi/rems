(ns rems.application
  (:require [clojure.string :as str]
            [medley.core :refer [map-vals]]
            [re-frame.core :as rf]
            [rems.actions.action :refer [action-button action-form-view action-comment action-collapse-id button-wrapper]]
            [rems.actions.add-member :refer [add-member-action-button add-member-form]]
            [rems.actions.approve-reject :refer [approve-reject-action-button approve-reject-form]]
            [rems.actions.close :refer [close-action-button close-form]]
            [rems.actions.comment :refer [comment-action-button comment-form]]
            [rems.actions.decide :refer [decide-action-button decide-form]]
            [rems.actions.invite-member :refer [invite-member-action-button invite-member-form]]
            [rems.actions.remove-member :refer [remove-member-action-button remove-member-form]]
            [rems.actions.request-comment :refer [request-comment-action-button request-comment-form]]
            [rems.actions.request-decision :refer [request-decision-action-button request-decision-form]]
            [rems.actions.return-action :refer [return-action-button return-form]]
            [rems.application-util :refer [form-fields-editable?]]
            [rems.atoms :refer [external-link flash-message info-field textarea]]
            [rems.autocomplete :as autocomplete]
            [rems.catalogue-util :refer [get-catalogue-item-title]]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [index-by]]
            [rems.guide-utils :refer [lipsum lipsum-short lipsum-paragraphs]]
            [rems.phase :refer [phases]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [localize-decision localize-event localize-item localize-state localize-time text text-format]]
            [rems.util :refer [dispatch! fetch post!]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;;;; Helpers

(defn scroll-to-top! []
  (.setTimeout js/window #(.scrollTo js/window 0 0) 500)) ;; wait until faded out

(defn reload! [application-id]
  (rf/dispatch [:rems.application/enter-application-page application-id]))

;; TODO named secretary routes give us equivalent functions
;; TODO should the secretary route definitions be in this ns too?

(defn apply-for [items]
  (let [url (str "#/application?items=" (str/join "," (sort (map :id items))))]
    (dispatch! url)))

(defn navigate-to
  "Navigates to the application with the given id.

  `replace?` parameter can be given to replace history state instead of push."
  [id & [replace?]]
  (dispatch! (str "#/application/" id) replace?))

(defn- format-validation-messages
  [application msgs]
  (let [fields-by-id (index-by [:id] (map localize-item (:items application)))
        licenses-by-id (index-by [:id] (map localize-item (:licenses application)))]
    [:div (text :t.form/validation.errors)
     (into [:ul]
           (concat
            (for [{:keys [type field-id]} (filter :field-id msgs)]
              [:li (text-format type (:title (fields-by-id field-id)))])
            (for [{:keys [type license-id]} (filter :license-id msgs)]
              [:li (text-format type (:title (licenses-by-id license-id)))])))]))











;;;; State

(rf/reg-sub ::application (fn [db _] (::application db)))
(rf/reg-sub ::edit-application (fn [db _] (::edit-application db)))

(rf/reg-event-fx
 ::enter-application-page
 (fn [{:keys [db]} [_ id]]
   (merge {:db (assoc db
                      ::application nil
                      ::edit-application nil)
           ::fetch-application id})))

(rf/reg-fx
 ::fetch-application
 (fn [id]
   (fetch (str "/api/v2/applications/" id "/migration")
          {:handler #(rf/dispatch [::fetch-application-result %])})))

(rf/reg-event-db
 ::fetch-application-result
 (fn [db [_ application]]
   (assoc db
          ::application application
          ::edit-application {:items (into {} (for [item (:items application)]
                                                [(:id item) (select-keys item [:value :previous-value])]))
                              :licenses (into {} (map (juxt :id :approved) (:licenses application)))})))

(rf/reg-event-db
 ::set-validation
 (fn [db [_ errors]]
   (prn errors)
   (assoc-in db [::edit-application :validation] errors)))

(defn- save-application [app description application-id catalogue-items items licenses]
  (status-modal/common-pending-handler! description)
  (post! "/api/applications/save"
         {:handler (partial status-modal/common-success-handler! #(rf/dispatch [::enter-application-page application-id]))
          :error-handler status-modal/common-error-handler!
          :params (merge {:command "save"
                          :items (map-vals :value items)
                          :licenses licenses}
                         (if application-id
                           {:application-id application-id}
                           {:catalogue-items catalogue-items}))}))

(rf/reg-event-fx
 ::save-application
 (fn [{:keys [db]} [_ description]]
   (let [app (get-in db [::application :application])
         app-id (get-in db [::application :application :id])
         catalogue-items (get-in db [::application :catalogue-items])
         catalogue-ids (mapv :id catalogue-items)
         items (get-in db [::edit-application :items])
         ;; TODO change api to booleans
         licenses (into {}
                        (for [[id checked?] (get-in db [::edit-application :licenses])
                              :when checked?]
                          [id "approved"]))]
     (save-application app description app-id catalogue-ids items licenses))
   {:db (assoc-in db [::edit-application :validation] nil)}))

(defn- submit-application [application description application-id catalogue-items items licenses]
  (status-modal/common-pending-handler! description)
  (post! "/api/applications/save"
         {:handler (fn [response]
                     (if (:success response)
                       (post! "/api/applications/command"
                              {:handler (fn [response]
                                          (if (:success response)
                                            (status-modal/set-success! {:on-close #(rf/dispatch [::enter-application-page application-id (:errors %)])})
                                            (do
                                              (status-modal/set-error! {:result response
                                                                        :error-content (format-validation-messages application (:errors response))})
                                              (rf/dispatch [::set-validation (:errors response)]))))
                               :error-handler status-modal/common-error-handler!
                               :params {:type :rems.workflow.dynamic/submit
                                        :application-id application-id}})
                       (status-modal/common-error-handler! response)))
          :error-handler status-modal/common-error-handler!
          :params (merge {:command "save"
                          :items (map-vals :value items)
                          :licenses licenses}
                         (if application-id
                           {:application-id application-id}
                           {:catalogue-items catalogue-items}))}))

(rf/reg-event-fx
 ::submit-application
 (fn [{:keys [db]} [_ description]]
   (let [application (get-in db [::application])
         app-id (get-in db [::application :application :id])
         catalogue-items (get-in db [::application :catalogue-items])
         catalogue-ids (mapv :id catalogue-items)
         items (get-in db [::edit-application :items])
         ;; TODO change api to booleans
         licenses (into {}
                        (for [[id checked?] (get-in db [::edit-application :licenses])
                              :when checked?]
                          [id "approved"]))]
     (submit-application application description app-id catalogue-ids items licenses))
   {:db (assoc-in db [::edit-application :validation] nil)}))

(defn- save-attachment [{:keys [db]} [_ field-id file description]]
  (let [application-id (get-in db [::application :application :id])]
    (status-modal/common-pending-handler! description)
    (post! (str "/api/applications/add_attachment?application-id=" application-id "&field-id=" field-id)
           {:body file
            :handler (partial status-modal/common-success-handler! nil)
            :error-handler status-modal/common-error-handler!})
    {}))

(rf/reg-event-fx ::save-attachment save-attachment)

(defn- remove-attachment [_ [_ application-id field-id description]]
  (status-modal/common-pending-handler! description)
  (post! (str "/api/applications/remove_attachment?application-id=" application-id "&field-id=" field-id)
         {:body {}
          :handler (partial status-modal/common-success-handler! nil)
          :error-handler status-modal/common-error-handler!})
  {})

(rf/reg-event-fx ::remove-attachment remove-attachment)




;;;; UI components

(defn- pdf-button [id]
  (when id
    [:a.btn.btn-secondary
     {:href (str "/api/applications/" id "/pdf")
      :target :_new}
     "PDF " (external-link)]))

(rf/reg-event-db ::set-field (fn [db [_ id value]] (assoc-in db [::edit-application :items id :value] value)))
(rf/reg-event-db ::set-license (fn [db [_ id value]] (assoc-in db [::edit-application :licenses id] value)))
(defn- set-field-value
  [id]
  (fn [event]
    (rf/dispatch [::set-field id (.. event -target -value)])))

(defn- id-to-name [id]
  (str "field" id))

(defn- set-attachment
  [id description]
  (fn [event]
    (let [filecontent (aget (.. event -target -files) 0)
          form-data (doto (js/FormData.)
                      (.append "file" filecontent))]
      (rf/dispatch [::set-field id (.-name filecontent)])
      (rf/dispatch [::save-attachment id form-data description]))))

(defn- remove-attachment-action
  [app-id id description]
  (fn [event]
    (rf/dispatch [::set-field id nil])
    (rf/dispatch [::remove-attachment app-id id description])))

(defn- readonly-field [{:keys [id value]}]
  [:div.form-control {:id id} (str/trim (str value))])

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

(defn- toggle-diff-button [item-id diff-visible]
  [:a.toggle-diff {:href "#"
                   :on-click (fn [event]
                               (.preventDefault event)
                               (rf/dispatch [::toggle-diff item-id]))}
   [:i.fas.fa-exclamation-circle]
   " "
   (if diff-visible
     (text :t.form/diff-hide)
     (text :t.form/diff-show))])

(defn basic-field
  "Common parts of a form field.

  :title - string (required), field title to show to the user
  :id - number (required), field id
  :readonly - boolean, true if the field should not be editable
  :readonly-component - HTML, custom component for a readonly field
  :maxlength - maximum number of characters (optional)
  :optional - boolean, true if the field is not required
  :value - string, the current value of the field
  :previous-value - string, the previously submitted value of the field
  :diff - boolean, true if should show the diff between :value and :previous-value
  :diff-component - HTML, custom component for rendering a diff
  :validation - validation errors

  editor-component - HTML, form component for editing the field"
  [{:keys [title id readonly readonly-component optional value previous-value diff diff-component validation maxlength]} editor-component]
  [:div.form-group.field
   [:label {:for (id-to-name id)}
    title " "
    (when maxlength
      (text-format :t.form/maxlength (str maxlength)))
    " "
    (when optional
      (text :t.form/optional))]
   (when (and previous-value
              (not= value previous-value))
     [toggle-diff-button id diff])
   (cond
     diff (or diff-component
              [diff-field {:id (id-to-name id)
                           :value value
                           :previous-value previous-value}])
     readonly (or readonly-component
                  [readonly-field {:id (id-to-name id)
                                   :value value}])
     :else editor-component)
   [field-validation-message validation title]])

(defn- text-field
  [{:keys [id inputprompt value validation maxlength] :as opts}]
  [basic-field opts
   [:input.form-control {:type "text"
                         :id (id-to-name id)
                         :name (id-to-name id)
                         :placeholder inputprompt
                         :max-length maxlength
                         :class (when validation "is-invalid")
                         :value value
                         :on-change (set-field-value id)}]])

(defn- texta-field
  [{:keys [id inputprompt value validation maxlength] :as opts}]
  [basic-field opts
   [textarea {:id (id-to-name id)
              :name (id-to-name id)
              :placeholder inputprompt
              :max-length maxlength
              :class (if validation "form-control is-invalid" "form-control")
              :value value
              :on-change (set-field-value id)}]])

;; TODO: custom :diff-component, for example link to both old and new attachment
(defn attachment-field
  [{:keys [title id value validation app-id] :as opts}]
  (let [click-upload (fn [e] (when-not (:readonly opts) (.click (.getElementById js/document (id-to-name id)))))
        filename-field [:div.field
                        [:a.btn.btn-secondary.mr-2
                         {:href (str "/api/applications/attachments/?application-id=" app-id "&field-id=" id)
                          :target :_new}
                         value " " (external-link)]]
        upload-field [:div.upload-file.mr-2
                      [:input {:style {:display "none"}
                               :type "file"
                               :id (id-to-name id)
                               :name (id-to-name id)
                               :accept ".pdf, .doc, .docx, .ppt, .pptx, .txt, image/*"
                               :class (when validation "is-invalid")
                               :on-change (set-attachment id title)}]
                      [:button.btn.btn-secondary {:on-click click-upload}
                       (text :t.form/upload)]]
        remove-button [:button.btn.btn-secondary.mr-2
                       {:on-click (remove-attachment-action app-id id (text :t.form/attachment-remove))}
                       (text :t.form/attachment-remove)]]
    [basic-field (assoc opts :readonly-component (if (empty? value)
                                                   [:span]
                                                   filename-field))
     (if (empty? value)
       upload-field
       [:div {:style {:display :flex :justify-content :flex-start}}
        filename-field
        remove-button])]))

(defn- date-field
  [{:keys [id value min max validation] :as opts}]
  ;; TODO: format readonly value in user locale (give basic-field a formatted :value and :previous-value in opts)
  [basic-field opts
   [:input.form-control {:type "date"
                         :id (id-to-name id)
                         :name (id-to-name id)
                         :class (when validation "is-invalid")
                         :defaultValue value
                         :min min
                         :max max
                         :on-change (set-field-value id)}]])

(defn- option-label [value language options]
  (let [label (->> options
                   (filter #(= value (:key %)))
                   first
                   :label)]
    (get label language value)))

(defn option-field [{:keys [id value options validation language] :as opts}]
  [basic-field
   (assoc opts :readonly-component [readonly-field {:id (id-to-name id)
                                                    :value (option-label value language options)}])
   (into [:select.form-control {:id (id-to-name id)
                                :name (id-to-name id)
                                :class (when validation "is-invalid")
                                :defaultValue value
                                :on-change (set-field-value id)}
          [:option {:value ""}]]
         (for [{:keys [key label]} options]
           [:option {:value key}
            (get label language key)]))])

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

(defn multiselect-field [{:keys [id value options validation language] :as opts}]
  (let [selected-keys (decode-option-keys value)]
    ;; TODO: for accessibility these checkboxes would be best wrapped in a fieldset
    [basic-field
     (assoc opts :readonly-component [readonly-field {:id (id-to-name id)
                                                      :value (->> options
                                                                  (filter #(contains? selected-keys (:key %)))
                                                                  (map #(get (:label %) language (:key %)))
                                                                  (str/join ", "))}])
     (into [:div]
           (for [{:keys [key label]} options]
             (let [option-id (str (id-to-name id) "-" key)
                   on-change (fn [event]
                               (let [checked (.. event -target -checked)
                                     selected-keys (if checked
                                                     (conj selected-keys key)
                                                     (disj selected-keys key))]
                                 (rf/dispatch [::set-field id (encode-option-keys selected-keys)])))]
               [:div.form-check
                [:input.form-check-input {:type "checkbox"
                                          :id option-id
                                          :name option-id
                                          :class (when validation "is-invalid")
                                          :value key
                                          :checked (contains? selected-keys key)
                                          :on-change on-change}]
                [:label.form-check-label {:for option-id}
                 (get label language key)]])))]))

(defn- label [{title :title}]
  [:div.form-group
   [:label title]])

(defn- set-license-approval
  [id]
  (fn [event]
    (rf/dispatch [::set-license id (.. event -target -checked)])))

(defn- license [id title approved readonly validation content]
  [:div.license
   [field-validation-message validation title]
   [:div.form-check
    [:input.form-check-input {:type "checkbox"
                              :name (str "license" id)
                              :disabled readonly
                              :class (when validation "is-invalid")
                              :checked (boolean approved)
                              :on-change (set-license-approval id)}]
    [:span.form-check-label content]]])

(defn- link-license
  [{:keys [title id textcontent readonly approved validation]}]
  [license id title approved readonly validation
   [:a.license-title {:href textcontent :target "_blank"}
    title " " (external-link)]])

(defn- text-license
  [{:keys [title id textcontent approved readonly validation]}]
  [license id title approved readonly validation
   [:div.license-panel
    [:span.license-title
     [:a.license-header.collapsed {:data-toggle "collapse"
                                   :href (str "#collapse" id)
                                   :aria-expanded "false"
                                   :aria-controls (str "collapse" id)}
      title " " [:i {:class "fa fa-ellipsis-h"}]]]
    [:div.collapse {:id (str "collapse" id)}
     [:div.license-block (str/trim textcontent)]]]])

(defn- unsupported-field
  [f]
  [:p.alert.alert-warning "Unsupported field " (pr-str f)])

(defn license-field [f]
  (case (:licensetype f)
    "link" [link-license f]
    "text" [text-license f]
    [unsupported-field f]))

(defn- field [f]
  (case (:type f)
    "attachment" [attachment-field f]
    "date" [date-field f]
    "description" [text-field f]
    "label" [label f]
    "multiselect" [multiselect-field f]
    "option" [option-field f]
    "text" [text-field f]
    "texta" [texta-field f]
    [unsupported-field f]))

(defn- save-button []
  [button-wrapper {:id "save"
                   :text (text :t.form/save)
                   :on-click #(rf/dispatch [::save-application (text :t.form/save)])}])

(defn- submit-button []
  [button-wrapper {:id "submit"
                   :text (text :t.form/submit)
                   :class :btn-primary
                   :on-click #(rf/dispatch [::submit-application (text :t.form/submit)])}])

(defn- application-fields [form edit-application language]
  (let [application (:application form)
        {:keys [items validation]} edit-application
        field-validations (index-by [:field-id] validation)
        form-fields-editable? (form-fields-editable? application)
        readonly? (not form-fields-editable?)]
    [collapsible/component
     {:id "form"
      :title (text :t.form/application)
      :always
      [:div
       (into [:div]
             (for [item (:items form)]
               [field (assoc (localize-item item)
                             :validation (field-validations (:id item))
                             :readonly readonly?
                             :language language
                             :value (get-in items [(:id item) :value])
                             :previous-value (get-in items [(:id item) :previous-value])
                             :diff (get-in items [(:id item) :diff])
                             :app-id (:id application))]))]}]))

(defn- application-licenses [form edit-application language]
  (when-let [form-licenses (not-empty (:licenses form))]
    (let [application (:application form)
          {:keys [licenses validation]} edit-application
          license-validations (index-by [:license-id] validation)
          form-fields-editable? (form-fields-editable? application)
          readonly? (not form-fields-editable?)]
      [collapsible/component
       {:id "form"
        :title (text :t.form/licenses)
        :always
        [:div.form-group.field
         (into [:div#licenses]
               (for [license form-licenses]
                 [license-field (assoc (localize-item license)
                                       :validation (license-validations (:id license))
                                       :readonly readonly?
                                       :approved (get licenses (:id license)))]))]}])))


;; FIXME Why do we have both this and dynamic-event->event?
(defn- format-event [event]
  {:userid (:userid event)
   :event (localize-event (:event event))
   :comment (:comment event)
   :request-id (:request-id event)
   :commenters (:commenters event)
   :deciders (:deciders event)
   :time (localize-time (:time event))})

(defn- event-view [{:keys [time userid event comment commenters deciders]}]
  [:div.row
   [:label.col-sm-2.col-form-label time]
   [:div.col-sm-10
    [:div.col-form-label [:span userid] " — " [:span event]
     (when-let [targets (seq (concat commenters deciders))]
       [:span ": " (str/join ", " targets)])]
    (when comment [:div comment])]])

(defn- render-event-groups [event-groups]
  (for [group event-groups]
    ^{:key group} [:div.group
                   (for [e group]
                     ^{:key e} [event-view e])]))

(defn- application-header [state phases-data events last-modified]
  (let [;; the event times have millisecond differences, so they need to be formatted to minute precision before deduping
        event-groups (->> events
                          (map format-event)
                          dedupe
                          (group-by #(or (:request-id %)
                                         ;; Might want to replace this by exposing id from backend
                                         [(:event %) (:time %)]))
                          vals
                          (map (partial sort-by :time))
                          (sort-by #(:time (first %)))
                          reverse)]
    [collapsible/component
     {:id "header"
      :title [:span#application-state
              (str
               (text :t.applications/state)
               (when state (str ": " (localize-state state))))]
      :always [:div
               [:div.mb-3 {:class (str "state-" (if (keyword? state) (name state) state))} (phases phases-data)]
               [:h4 (text-format :t.applications/latest-activity (localize-time last-modified))]
               (when-let [g (first event-groups)]
                 (list
                  [:h4 (text :t.form/events)]
                  (render-event-groups [g])))]
      :collapse (when-let [g (seq (rest event-groups))]
                  (render-event-groups g))}]))

(defn member-info
  "Renders a applicant, member or invited member of an application

  `:element-id`  - id of the element to generate unique ids
  `:attributes`  - user attributes to display
  `:application` - application
  `:group?`      - specifies if a group border is rendered
  `:can-remove?` - can the user be removed?"
  [{:keys [element-id attributes application group? can-remove?]}]
  (let [application-id (:id application)
        user-id (or (:eppn attributes) (:userid attributes))
        sanitized-user-id (-> (or user-id "")
                              str/lower-case
                              (str/replace #"[^a-z]" ""))
        other-attributes (dissoc attributes :commonName :name :eppn :userid :mail :email)
        user-actions-id (str element-id "-" sanitized-user-id "-actions")]
    [collapsible/minimal
     {:id (str element-id "-" sanitized-user-id "-info")
      :class (when group? "group")
      :always
      [:div
       (cond (= (:applicantuserid application) user-id) [:h5 (text :t.applicant-info/applicant)]
             (:userid attributes) [:h5 (text :t.applicant-info/member)]
             :else [:h5 (text :t.applicant-info/invited-member)])
       (when-let [name (or (:commonName attributes) (:name attributes))]
         [info-field (text :t.applicant-info/name) name {:inline? true}])
       (when user-id
         [info-field (text :t.applicant-info/username) user-id {:inline? true}])
       (when-let [mail (or (:mail attributes) (:email attributes))]
         [info-field (text :t.applicant-info/email) mail {:inline? true}])]
      :collapse (when (seq other-attributes)
                  (into [:div]
                        (for [[k v] other-attributes]
                          [info-field k v])))
      :footer [:div {:id user-actions-id}
               (when can-remove?
                 [:div.commands
                  [remove-member-action-button user-actions-id]])
               (when can-remove?
                 [remove-member-form application-id user-actions-id attributes (partial reload! application-id)])]}]))

(defn applicants-info
  "Renders the applicants, i.e. applicant and members."
  [id application applicant-attributes members invited-members]
  (let [application-id (:id application)
        applicant (first (filter (comp #{(:eppn applicant-attributes)} :userid) members))
        non-applicant-members (remove #{applicant} members)
        possible-commands (:possible-commands application)
        can-add? (contains? possible-commands :rems.workflow.dynamic/add-member)
        can-remove? (contains? possible-commands :rems.workflow.dynamic/remove-member)
        can-invite? (contains? possible-commands :rems.workflow.dynamic/invite-member)
        can-uninvite? (contains? possible-commands :rems.workflow.dynamic/uninvite-member)]
    [collapsible/component
     {:id id
      :title (text :t.applicant-info/applicants)
      :always
      (into [:div
             [member-info {:element-id id
                           :attributes (merge applicant applicant-attributes)
                           :application application
                           :group? (or (seq non-applicant-members)
                                       (seq invited-members))
                           :can-remove? false}]]
            (concat
             (for [member non-applicant-members]
               [member-info {:element-id id
                             :attributes member
                             :application application
                             :group? true
                             :can-remove? can-remove?}])
             (for [invited-member invited-members]
               [member-info {:element-id id
                             :attributes invited-member
                             :application application
                             :group? true
                             :can-remove? can-uninvite?}])))
      :footer [:div
               [:div.commands
                (when can-invite? [invite-member-action-button])
                (when can-add? [add-member-action-button])]
               [:div#member-action-forms
                [invite-member-form application-id (partial reload! application-id)]
                [add-member-form application-id (partial reload! application-id)]]]}]))

(defn- dynamic-actions [app]
  (let [commands-and-actions [:rems.workflow.dynamic/save-draft [save-button]
                              :rems.workflow.dynamic/submit [submit-button]
                              :rems.workflow.dynamic/return [return-action-button]
                              :rems.workflow.dynamic/request-decision [request-decision-action-button]
                              :rems.workflow.dynamic/decide [decide-action-button]
                              :rems.workflow.dynamic/request-comment [request-comment-action-button]
                              :rems.workflow.dynamic/comment [comment-action-button]
                              :rems.workflow.dynamic/approve [approve-reject-action-button]
                              :rems.workflow.dynamic/reject [approve-reject-action-button]
                              :rems.workflow.dynamic/close [close-action-button]]]
    (distinct (for [[command action] (partition 2 commands-and-actions)
                    :when (contains? (:possible-commands app) command)]
                action))))

(defn- actions-form [app]
  (let [actions (dynamic-actions app)
        reload (partial reload! (:id app))
        forms [[:div#actions-forms.mt-3
                [request-comment-form (:id app) reload]
                [request-decision-form (:id app) reload]
                [comment-form (:id app) reload]
                [close-form (:id app) reload]
                [decide-form (:id app) reload]
                [return-form (:id app) reload]
                [approve-reject-form (:id app) reload]]]]
    (when (seq actions)
      [collapsible/component
       {:id "actions"
        :title (text :t.form/actions)
        :always (into [:div (into [:div.commands]
                                  actions)]
                      forms)}])))

(defn- disabled-items-warning [catalogue-items]
  (let [language @(rf/subscribe [:language])]
    (when-some [items (seq (filter #(= "disabled" (:state %)) catalogue-items))]
      [:div.alert.alert-danger
       (text :t.form/alert-disabled-items)
       (into [:ul]
             (for [item items]
               [:li (get-catalogue-item-title item language)]))])))

(defn- applied-resources [catalogue-items]
  (let [language @(rf/subscribe [:language])]
    [collapsible/component
     {:id "resources"
      :title (text :t.form/resources)
      :always [:div.form-items.form-group
               (into [:ul]
                     (for [item catalogue-items]
                       ^{:key (:id item)}
                       [:li (get-catalogue-item-title item language)]))]}]))

(defn- dynamic-event->event [event]
  {:event (name (:event/type event))
   :time (:event/time event)
   :userid (:event/actor event)
   :request-id (:application/request-id event)
   :commenters (:application/commenters event)
   :deciders (:application/deciders event)
   :comment (if (= :application.event/decided (:event/type event))
              (str (localize-decision (:application/decision event)) ": " (:application/comment event))
              (:application/comment event))})

(defn- render-application [application edit-application language]
  (let [app (:application application)
        state (:state app)
        last-modified (:last-modified app)
        phases (:phases application)
        events (concat (:events app)
                       (map dynamic-event->event (:dynamic-events app)))
        applicant-attributes (:applicant-attributes application)
        messages (remove nil?
                         [(disabled-items-warning (:catalogue-items application)) ; NB: eval this here so we get nil or a warning
                          (when (:validation edit-application)
                            [flash-message
                             {:status :danger
                              :contents [format-validation-messages application (:validation edit-application)]}])])]
    [:div
     [:div {:class "float-right"} [pdf-button (:id app)]]
     [:h2 (text :t.applications/application)]
     (into [:div] messages)
     [application-header state phases events last-modified]
     [:div.mt-3 [applicants-info "applicants-info" app applicant-attributes (:members app) (:invited-members app)]]
     [:div.mt-3 [applied-resources (:catalogue-items application)]]
     [:div.my-3 [application-fields application edit-application language]]
     [:div.my-3 [application-licenses application edit-application language]]
     [:div.mb-3 [actions-form app]]]))

;;;; Entrypoint

(defn application-page []
  (let [application @(rf/subscribe [::application])
        edit-application @(rf/subscribe [::edit-application])
        language @(rf/subscribe [:language])
        loading? (not application)]
    (if loading?
      [:div
       [:h2 (text :t.applications/application)]
       [spinner/big]]
      [render-application application edit-application language])))






;;;; Guide

(defn guide []
  [:div
   (component-info member-info)
   (example "member-info"
            [member-info {:element-id "info1"
                          :attributes {:eppn "developer@uu.id"
                                       :mail "developer@uu.id"
                                       :commonName "Deve Loper"
                                       :organization "Testers"
                                       :address "Testikatu 1, 00100 Helsinki"}
                          :application {:id 42
                                        :applicantuserid "developer"}}])
   (example "member-info with name missing"
            [member-info {:element-id "info2"
                          :attributes {:eppn "developer"
                                       :mail "developer@uu.id"
                                       :organization "Testers"
                                       :address "Testikatu 1, 00100 Helsinki"}
                          :application {:id 42
                                        :applicantuserid "developer"}}])
   (example "member-info"
            [member-info {:element-id "info3"
                          :attributes {:userid "alice"}
                          :application {:id 42
                                        :applicantuserid "developer"}
                          :group? true
                          :can-remove? true}])
   (example "member-info"
            [member-info {:element-id "info4"
                          :attributes {:name "John Smith" :email "john.smith@invited.com"}
                          :application {:id 42
                                        :applicantuserid "developer"}
                          :group? true}])

   (component-info applicants-info)
   (example "applicants-info"
            [applicants-info "applicants"
             {:id 42
              :applicantuserid "developer"
              :possible-commands #{:rems.workflow.dynamic/add-member
                                   :rems.workflow.dynamic/invite-member}}
             {:eppn "developer"
              :mail "developer@uu.id"
              :commonName "Deve Loper"
              :organization "Testers"
              :address "Testikatu 1, 00100 Helsinki"}
             [{:userid "alice"} {:userid "bob"}]
             [{:name "John Smith" :email "john.smith@invited.com"}]])

   (component-info disabled-items-warning)
   (example "no disabled items"
            [disabled-items-warning []])
   (example "two disabled items"
            [disabled-items-warning
             [{:state "disabled" :localizations {:en {:title "English title 1"}
                                                 :fi {:title "Otsikko suomeksi 1"}}}
              {:state "disabled" :localizations {:en {:title "English title 2"}
                                                 :fi {:title "Otsikko suomeksi 2"}}}
              {:state "enabled" :localizations {:en {:title "English title 3"}
                                                :fi {:title "Otsikko suomeksi 3"}}}]])

   (component-info field)
   (example "field of type \"text\""
            [:form
             [field {:type "text" :title "Title" :inputprompt "prompt"}]])
   (example "field of type \"text\" with maximum length"
            [:form
             [field {:type "text" :title "Title" :inputprompt "prompt" :maxlength 10}]])
   (example "field of type \"text\" with validation error"
            [:form
             [field {:type "text" :title "Title" :inputprompt "prompt"
                     :validation {:type :t.form.validation.required}}]])
   (example "non-editable field of type \"text\" without text"
            [:form
             [field {:type "text" :title "Title" :inputprompt "prompt" :readonly true}]])
   (example "non-editable field of type \"text\" with text"
            [:form
             [field {:type "text" :title "Title" :inputprompt "prompt" :readonly true :value lipsum-short}]])
   (example "field of type \"texta\""
            [:form
             [field {:type "texta" :title "Title" :inputprompt "prompt"}]])
   (example "field of type \"texta\" with maximum length"
            [:form
             [field {:type "texta" :title "Title" :inputprompt "prompt" :maxlength 10}]])
   (example "field of type \"texta\" with validation error"
            [:form
             [field {:type "texta" :title "Title" :inputprompt "prompt"
                     :validation {:type :t.form.validation.required}}]])
   (example "non-editable field of type \"texta\""
            [:form
             [field {:type "texta" :title "Title" :inputprompt "prompt" :readonly true :value lipsum-paragraphs}]])
   (let [previous-lipsum-paragraphs (-> lipsum-paragraphs
                                        (str/replace "ipsum primis in faucibus orci luctus" "eu mattis purus mi eu turpis")
                                        (str/replace "per inceptos himenaeos" "justo erat hendrerit magna"))]
     [:div
      (example "editable field of type \"texta\" with previous value, diff hidden"
               [:form
                [field {:type "texta" :title "Title" :inputprompt "prompt" :value lipsum-paragraphs :previous-value previous-lipsum-paragraphs}]])
      (example "editable field of type \"texta\" with previous value, diff shown"
               [:form
                [field {:type "texta" :title "Title" :inputprompt "prompt" :value lipsum-paragraphs :previous-value previous-lipsum-paragraphs :diff true}]])
      (example "non-editable field of type \"texta\" with previous value, diff hidden"
               [:form
                [field {:type "texta" :title "Title" :inputprompt "prompt" :readonly true :value lipsum-paragraphs :previous-value previous-lipsum-paragraphs}]])
      (example "non-editable field of type \"texta\" with previous value, diff shown"
               [:form
                [field {:type "texta" :title "Title" :inputprompt "prompt" :readonly true :value lipsum-paragraphs :previous-value previous-lipsum-paragraphs :diff true}]])
      (example "non-editable field of type \"texta\" with previous value equal to current value"
               [:form
                [field {:type "texta" :title "Title" :inputprompt "prompt" :readonly true :value lipsum-paragraphs :previous-value lipsum-paragraphs}]])])
   (example "field of type \"attachment\""
            [:form
             [field {:type "attachment" :title "Title"}]])
   (example "field of type \"attachment\", file uploaded"
            [:form
             [field {:type "attachment" :title "Title" :value "test.txt"}]])
   (example "non-editable field of type \"attachment\""
            [:form
             [field {:type "attachment" :title "Title" :readonly true}]])
   (example "non-editable field of type \"attachment\", file uploaded"
            [:form
             [field {:type "attachment" :title "Title" :readonly true :value "test.txt"}]])
   (example "field of type \"date\""
            [:form
             [field {:type "date" :title "Title"}]])
   (example "field of type \"date\" with value"
            [:form
             [field {:type "date" :title "Title" :value "2000-12-31"}]])
   (example "non-editable field of type \"date\""
            [:form
             [field {:type "date" :title "Title" :readonly true :value ""}]])
   (example "non-editable field of type \"date\" with value"
            [:form
             [field {:type "date" :title "Title" :readonly true :value "2000-12-31"}]])
   (example "field of type \"option\""
            [:form
             [field {:type "option" :title "Title" :value "y" :language :en
                     :options [{:key "y" :label {:en "Yes" :fi "Kyllä"}}
                               {:key "n" :label {:en "No" :fi "Ei"}}]}]])
   (example "non-editable field of type \"option\""
            [:form
             [field {:type "option" :title "Title" :value "y" :language :en :readonly true
                     :options [{:key "y" :label {:en "Yes" :fi "Kyllä"}}
                               {:key "n" :label {:en "No" :fi "Ei"}}]}]])
   (example "field of type \"multiselect\""
            [:form
             [field {:type "multiselect" :title "Title" :value "egg bacon" :language :en
                     :options [{:key "egg" :label {:en "Egg" :fi "Munaa"}}
                               {:key "bacon" :label {:en "Bacon" :fi "Pekonia"}}
                               {:key "spam" :label {:en "Spam" :fi "Lihasäilykettä"}}]}]])
   (example "non-editable field of type \"multiselect\""
            [:form
             [field {:type "multiselect" :title "Title" :value "egg bacon" :language :en :readonly true
                     :options [{:key "egg" :label {:en "Egg" :fi "Munaa"}}
                               {:key "bacon" :label {:en "Bacon" :fi "Pekonia"}}
                               {:key "spam" :label {:en "Spam" :fi "Lihasäilykettä"}}]}]])
   (example "optional field"
            [:form
             [field {:type "texta" :optional "true" :title "Title" :inputprompt "prompt"}]])
   (example "field of type \"label\""
            [:form
             [field {:type "label" :title "Lorem ipsum dolor sit amet"}]])
   (example "field of type \"description\""
            [:form
             [field {:type "description" :title "Title" :inputprompt "prompt"}]])
   (example "link license"
            [:form
             [field {:type "license" :title "Link to license" :licensetype "link" :textcontent "/guide"}]])
   (example "link license with validation error"
            [:form
             [field {:type "license" :title "Link to license" :licensetype "link" :textcontent "/guide"
                     :validation {:type :t.form.validation.required}}]])
   (example "text license"
            [:form
             [field {:type "license" :id 1 :title "A Text License" :licensetype "text"
                     :textcontent lipsum-paragraphs}]])
   (example "text license with validation error"
            [:form
             [field {:type "license" :id 1 :title "A Text License" :licensetype "text" :textcontent lipsum-paragraphs
                     :validation {:type :t.form.validation.required}}]])

   (component-info render-application)
   (example "application, partially filled"
            [render-application
             {:title "Form title"
              :application {:id 17 :state "draft"
                            :can-approve? false
                            :can-close? true
                            :review-type nil}
              :catalogue-items [{:title "An applied item"}]
              :items [{:id 1 :type "text" :title "Field 1" :inputprompt "prompt 1"}
                      {:id 2 :type "label" :title "Please input your wishes below."}
                      {:id 3 :type "texta" :title "Field 2" :optional true :inputprompt "prompt 2"}
                      {:id 4 :type "unsupported" :title "Field 3" :inputprompt "prompt 3"}
                      {:id 5 :type "date" :title "Field 4"}]
              :licenses [{:id 4 :type "license" :title "" :textcontent "" :licensetype "text"
                          :localizations {:en {:title "A Text License" :textcontent lipsum}}}
                         {:id 5 :type "license" :licensetype "link" :title "" :textcontent ""
                          :localizations {:en {:title "Link to license" :textcontent "/guide"}}}]}
             {:items {1 "abc"}
              :licenses {4 false 5 true}}
             :en])
   (example "application, applied"
            [render-application
             {:title "Form title"
              :application {:id 17 :state "applied"
                            :can-approve? true
                            :can-close? false
                            :review-type nil}
              :catalogue-items [{:title "An applied item"}]
              :items [{:id 1 :type "text" :title "Field 1" :inputprompt "prompt 1"}]
              :licenses [{:id 2 :type "license" :title "" :licensetype "text"
                          :textcontent ""
                          :localizations {:en {:title "A Text License" :textcontent lipsum}}}]}
             {:items {1 "abc"}
              :licenses {2 true}}
             :en])
   (example "application, approved"
            [render-application
             {:title "Form title"
              :catalogue-items [{:title "An applied item"}]
              :applicant-attributes {:eppn "eppn" :mail "email@example.com" :additional "additional field"}
              :application {:id 17 :state "approved"
                            :can-approve? false
                            :can-close? true
                            :review-type nil
                            :events [{:event "approve" :comment "Looking good, approved!"}]}
              :items [{:id 1 :type "text" :title "Field 1" :inputprompt "prompt 1"}
                      {:id 2 :type "label" :title "Please input your wishes below."}
                      {:id 3 :type "texta" :title "Field 2" :optional true :inputprompt "prompt 2"}
                      {:id 4 :type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
              :licenses [{:id 5 :type "license" :title "A Text License" :licensetype "text"
                          :textcontent lipsum}
                         {:id 6 :type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                          :approved true}]
              :comments [{:comment "a comment"}]}
             {:items {1 "abc" 3 "def"}
              :licenses {5 true 6 true}}])])
