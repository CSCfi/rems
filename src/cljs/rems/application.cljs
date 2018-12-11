(ns rems.application
  (:require [clojure.string :as str]
            [medley.core :refer [map-vals]]
            [re-frame.core :as rf]
            [rems.actions.action :refer [action-form-view action-collapse-id button-wrapper]]
            [rems.actions.approve-reject :refer [approve-reject-form]]
            [rems.actions.close :refer [close-form]]
            [rems.actions.comment :refer [comment-form]]
            [rems.actions.decide :refer [decide-form]]
            [rems.actions.request-comment :refer [request-comment-form]]
            [rems.actions.request-decision :refer [request-decision-form]]
            [rems.actions.return-action :refer [return-form]]
            [rems.atoms :refer [external-link flash-message textarea]]
            [rems.autocomplete :as autocomplete]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [index-by]]
            [rems.db.catalogue :refer [get-catalogue-item-title]]
            [rems.guide-utils :refer [lipsum lipsum-short lipsum-paragraphs]]
            [rems.phase :refer [phases]]
            [rems.spinner :as spinner]
            [rems.status-modal :refer [status-modal]]
            [rems.text :refer [localize-decision localize-event localize-item localize-state localize-time text text-format]]
            [rems.util :refer [dispatch! fetch post!]]
            [secretary.core :as secretary])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;;;; Helpers

(defn scroll-to-top! []
  (.setTimeout js/window #(.scrollTo js/window 0 0) 500)) ;; wait until faded out

;; TODO named secretary routes give us equivalent functions
;; TODO should the secretary route definitions be in this ns too?

(defn apply-for [items]
  (let [url (str "#/application?items=" (str/join "," (sort (map :id items))))]
    (dispatch! url)))

(defn navigate-to [id]
  (dispatch! (str "#/application/" id)))












;;;; State

(defn- reset-state [db]
  (assoc db
         ::application nil
         ::edit-application nil
         ;; dynamic applications put all state under ::edit-application

         ;; static applications
         ::judge-comment ""
         ::review-comment ""
         ::send-third-party-review-request-success false))

(rf/reg-sub ::application (fn [db _] (::application db)))
(rf/reg-sub ::edit-application (fn [db _] (::edit-application db)))

(rf/reg-event-fx
 ::enter-application-page
 (fn [{:keys [db]} [_ id]]
   (merge {:db (reset-state db)
           ::fetch-application id}
          (when (contains? (get-in db [:identity :roles]) :approver)
            {::fetch-potential-third-party-reviewers (get-in db [:identity :user])}))))

(defn fetch-application [id on-success]
  (fetch (str "/api/applications/" id)
         {:handler on-success}))

(comment
  (fetch-application 19 prn))

(rf/reg-fx
 ::fetch-application
 (fn [id]
   (fetch-application id #(rf/dispatch [::fetch-application-result %]))))

(rf/reg-event-db
 ::fetch-application-result
 (fn [db [_ application]]
   (assoc db
          ::application application
          ::edit-application {:items (into {} (for [item (:items application)]
                                                [(:id item) {:value (:value item)}]))
                              :licenses (into {} (map (juxt :id :approver) (:licenses application)))})))

(rf/reg-event-fx
 ::enter-new-application-page
 (fn [{:keys [db]} [_ items]]
   {:db (reset-state db)
    ::fetch-draft-application items}))

(rf/reg-fx
 ::fetch-draft-application
 (fn [items]
   (fetch (str "/api/applications/draft")
          {:handler #(rf/dispatch [::fetch-application-result %])
           :params {:catalogue-items items}})))


(rf/reg-event-db
 ::set-status
 (fn [db [_ {:keys [status description validation error]}]]
   (assert (contains? #{:pending :saved :failed nil} status))
   (-> db
       (assoc-in [::edit-application :status] {:open? (not (nil? status))
                                               :status status
                                               :description description
                                               :error error})
       (assoc-in [::edit-application :validation] validation))))

(defn- save-application [app description application-id catalogue-items items licenses on-success]
  (post! "/api/applications/save"
         {:handler (fn [resp]
                     (if (:success resp)
                       (on-success resp)
                       (rf/dispatch [::set-status {:status :failed
                                                   :description description
                                                   :validation (:validation resp)}])))
          :error-handler (fn [error] (rf/dispatch [::set-status {:status :failed
                                                                 :description description
                                                                 :error error}]))
          :params (merge {:command "save"
                          :items (map-vals :value items)
                          :licenses licenses}
                         (if application-id
                           {:application-id application-id}
                           {:catalogue-items catalogue-items}))}))

(defn- submit-application [app description application-id catalogue-items items licenses]
  (if (= :workflow/dynamic (get-in app [:workflow :type]))
    (post! "/api/applications/command"
           {:handler (fn [resp]
                       (if (:success resp)
                         (do (rf/dispatch [::set-status {:status :saved
                                                         :description description}])
                             ;; HACK: we both set the location, and fire a fetch-application event
                             ;; because if the location didn't change, secretary won't fire the event
                             (navigate-to application-id)
                             (rf/dispatch [::enter-application-page application-id]))
                         (rf/dispatch [::set-status {:status :failed
                                                     :description description
                                                     :validation (:validation resp)}])))
            :error-handler (fn [error] (rf/dispatch [::set-status {:status :failed
                                                                   :description description
                                                                   :error error}]))
            :params {:type :rems.workflow.dynamic/submit
                     :application-id application-id}})
    (post! "/api/applications/save"
           {:handler (fn [resp]
                       (if (:success resp)
                         (do (rf/dispatch [::set-status {:status :saved
                                                         :description description}])
                             ;; HACK: we both set the location, and fire a fetch-application event
                             ;; because if the location didn't change, secretary won't fire the event
                             (navigate-to application-id)
                             (rf/dispatch [::enter-application-page application-id]))
                         (rf/dispatch [::set-status {:status :failed
                                                     :description description
                                                     :validation (:validation resp)}])))
            :error-handler (fn [error] (rf/dispatch [::set-status {:status :failed
                                                                   :description description
                                                                   :error error}]))
            :params (merge {:command "submit"
                            :items (map-vals :value items)
                            :licenses licenses}
                           (if application-id
                             {:application-id application-id}
                             {:catalogue-items catalogue-items}))})))

(rf/reg-event-fx
 ::save-application
 (fn [{:keys [db]} [_ command description]]
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
     (when-not app-id ;; fresh application
       (doseq [i catalogue-items]
         (rf/dispatch [:rems.cart/remove-item i])))
     ;; TODO disable form while saving?
     (rf/dispatch [::set-status {:status :pending
                                 :description description}])
     (save-application app description app-id catalogue-ids items licenses
                       (fn [resp]
                         (if (= command "submit")
                           (fetch-application (:id resp)
                                              (fn [app]
                                                (submit-application (:application app) description (:id resp) catalogue-ids items licenses)))
                           (do
                             (rf/dispatch [::set-status {:status :saved
                                                         :description description}])
                             ;; HACK: we both set the location, and fire a fetch-application event
                             ;; because if the location didn't change, secretary won't fire the event
                             (navigate-to (:id resp))
                             (rf/dispatch [::enter-application-page (:id resp)]))))))
   {}))

(defn- save-attachment [application-id field-id form-data description]
  (post! (str "/api/applications/add_attachment?application-id=" application-id "&field-id=" field-id)
         {:body form-data
          :error-handler (fn [_] (rf/dispatch [::set-status {:status :failed
                                                             :description description}]))}))

(defn- save-application-with-attachment [field-id form-data catalogue-items items licenses description]
  (let [payload {:command "save"
                 :items (map-vals :value items)
                 :licenses licenses
                 :catalogue-items catalogue-items}]
    ;; TODO this logic should be rewritten as a chain of save, save-attachment instead
    (post! "/api/applications/save"
           {:handler (fn [resp]
                       (if (:success resp)
                         (do (save-attachment (:id resp) field-id form-data description)
                             (rf/dispatch [::set-status {:status :saved
                                                         :description description}]) ; TODO here should be saving?
                             ;; HACK: we both set the location, and fire a fetch-application event
                             ;; because if the location didn't change, secretary won't fire the event
                             (navigate-to (:id resp))
                             (rf/dispatch [::enter-application-page (:id resp)]))
                         (rf/dispatch [::set-status {:status :failed
                                                     :description description ; TODO here should be saving?
                                                     :validation (:validation resp)}])))
            :error-handler (fn [_] (rf/dispatch [::set-status {:status :failed
                                                               :description description}])) ; TODO here should be saving?
            :params payload})))

(rf/reg-event-fx
 ::save-attachment
 (fn [{:keys [db]} [_ field-id file description]]
   (let [application-id (get-in db [::application :application :id])]
     (if application-id
       (save-attachment application-id field-id file description)
       (let [catalogue-items (get-in db [::application :catalogue-items])
             catalogue-ids (mapv :id catalogue-items)
             items (get-in db [::edit-application :items])
             ;; TODO change api to booleans
             licenses (into {}
                            (for [[id checked?] (get-in db [::edit-application :licenses])
                                  :when checked?]
                              [id "approved"]))]
         (save-application-with-attachment field-id file catalogue-ids items licenses description))))))





;;; Dynamic workflow state






;;; Static workflow state

(rf/reg-sub ::judge-comment (fn [db _] (::judge-comment db)))

(rf/reg-event-db ::set-field (fn [db [_ id value]] (assoc-in db [::edit-application :items id :value] value)))
(rf/reg-event-db ::toggle-diff (fn [db [_ id]] (update-in db [::edit-application :items id :diff] not)))
(rf/reg-event-db ::set-license (fn [db [_ id value]] (assoc-in db [::edit-application :licenses id] value)))
(rf/reg-event-db ::set-judge-comment (fn [db [_ value]] (assoc db ::judge-comment value)))

(rf/reg-fx
 ::fetch-potential-third-party-reviewers
 (fn [user]
   (fetch (str "/api/applications/reviewers")
          {:handler #(do (rf/dispatch [::set-potential-third-party-reviewers %])
                         (rf/dispatch [::set-selected-third-party-reviewers #{}]))
           :headers {"x-rems-user-id" (:eppn user)}})))

(defn enrich-user [user]
  (assoc user :display (str (:name user) " (" (:email user) ")")))

(rf/reg-event-db
 ::set-potential-third-party-reviewers
 (fn [db [_ reviewers]]
   (assoc db ::potential-third-party-reviewers (map enrich-user reviewers))))

(rf/reg-sub ::potential-third-party-reviewers (fn [db _] (::potential-third-party-reviewers db)))

(rf/reg-event-db
 ::set-selected-third-party-reviewers
 (fn [db [_ reviewers]]
   (assoc db ::selected-third-party-reviewers reviewers)))

(rf/reg-event-db
 ::add-selected-third-party-reviewer
 (fn [db [_ reviewer]]
   (if (contains? (::selected-third-party-reviewers db) reviewer)
     db
     (update db ::selected-third-party-reviewers conj reviewer))))

(rf/reg-event-db
 ::remove-selected-third-party-reviewer
 (fn [db [_ reviewer]]
   (update db ::selected-third-party-reviewers disj reviewer)))

(rf/reg-sub ::selected-third-party-reviewers (fn [db _] (::selected-third-party-reviewers db)))
(rf/reg-sub ::review-comment (fn [db _] (::review-comment db)))

(rf/reg-event-db
 ::set-review-comment
 (fn [db [_ value]]
   (assoc db ::review-comment value)))

(defn- send-third-party-review-request [reviewers user application-id round comment description]
  (post! "/api/applications/review_request"
         {:params {:application-id application-id
                   :round round
                   :comment comment
                   :recipients (map :userid reviewers)}
          :handler (fn [resp]
                     (rf/dispatch [::set-status {:status :saved
                                                 :description description}])
                     (rf/dispatch [::send-third-party-review-request-success true])
                     (rf/dispatch [::enter-application-page application-id])
                     (scroll-to-top!))
          :error-handler (fn [error]
                           (rf/dispatch [::set-status {:status :failed
                                                       :description description
                                                       :error error}]))}))

(rf/reg-event-fx
 ::send-third-party-review-request
 (fn [{:keys [db]} [_ reviewers comment description]]
   (let [application-id (get-in db [::application :application :id])
         round (get-in db [::application :application :curround])
         user (get-in db [:identity :user])]
     (send-third-party-review-request reviewers user application-id round comment description)
     {:dispatch [::set-status {:status :pending
                               :description description}]})))

(rf/reg-event-db
 ::send-third-party-review-request-success
 (fn [db [_ value]]
   (assoc db ::send-third-party-review-request-message value)))

(rf/reg-sub
 ::send-third-party-review-request-message
 (fn [db _]
   (::send-third-party-review-request-message db)))

(defn- judge-application [command application-id round comment description]
  (post! "/api/applications/judge"
         {:params {:command command
                   :application-id application-id
                   :round round
                   :comment comment}
          :handler (fn [resp]
                     (rf/dispatch [::set-status {:status :saved
                                                 :description description}])
                     (rf/dispatch [::enter-application-page application-id]))
          :error-handler (fn [error]
                           (rf/dispatch [::set-status {:status :failed
                                                       :description description
                                                       :error error}]))}))

(rf/reg-event-fx
 ::judge-application
 (fn [{:keys [db]} [_ command description]]
   (let [application-id (get-in db [::application :application :id])
         round (get-in db [::application :application :curround])
         comment (get db ::judge-comment "")]
     (rf/dispatch [::set-status {:status :pending
                                 :description description}])
     (rf/dispatch [::set-judge-comment ""])
     (judge-application command application-id round comment description)
     {})))











;;;; UI components

(defn- format-validation-messages
  [msgs language]
  (into [:ul]
        (for [m msgs]
          ^{:key (str "message_list_item_" m)}
          [:li (text-format (:key m) (get-in m [:title language]))])))

(defn- pdf-button [id]
  (when id
    [:a.btn.btn-secondary
     {:href (str "/api/applications/" id "/pdf")
      :target :_new}
     "PDF " (external-link)]))

(defn- set-field-value
  [id]
  (fn [event]
    (rf/dispatch [::set-field id (.. event -target -value)])))

(defn- id-to-name [id]
  (if-not id
    (str "id_missing")
    (str "field" id)))

(defn- set-attachment
  [id description]
  (fn [event]
    (let [filecontent (aget (.. event -target -files) 0)
          form-data (doto (js/FormData.)
                      (.append "file" filecontent))]
      (rf/dispatch [::set-field id (.-name filecontent)])
      (rf/dispatch [::save-attachment id form-data description]))))

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
     (text-format (:key validation) title)]))

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
  :optional - boolean, true if the field is not required
  :value - string, the current value of the field
  :previous-value - string, the previously submitted value of the field
  :diff - boolean, true if should show the diff between :value and :previous-value
  :diff-component - HTML, custom component for rendering a diff
  :validation - validation errors

  editor-component - HTML, form component for editing the field"
  [{:keys [title id readonly readonly-component optional value previous-value diff diff-component validation]} editor-component]
  [:div.form-group.field
   [:label {:for (id-to-name id)}
    title " "
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
  [{:keys [id inputprompt value validation] :as opts}]
  [basic-field opts
   [:input.form-control {:type "text"
                         :id (id-to-name id)
                         :name (id-to-name id)
                         :placeholder inputprompt
                         :class (when validation "is-invalid")
                         :value value
                         :on-change (set-field-value id)}]])

(defn- texta-field
  [{:keys [id inputprompt value validation] :as opts}]
  [basic-field opts
   [textarea {:id (id-to-name id)
              :name (id-to-name id)
              :placeholder inputprompt
              :class (if validation "form-control is-invalid" "form-control")
              :value value
              :on-change (set-field-value id)}]])

(defn attachment-field
  [{:keys [title id value validation app-id] :as opts}]
  (let [download-link (when (not-empty value)
                        [:a {:href (str "/api/applications/attachments/?application-id=" app-id "&field-id=" id)
                             :target "_blank"}
                         value])]
    ;; TODO: custom :diff-component, for example link to both old and new attachment
    [basic-field (assoc opts :readonly-component [:div.form-control download-link])
     [:div
      [:div.upload-file
       [:input {:style {:display "none"}
                :type "file"
                :id (id-to-name id)
                :name (id-to-name id)
                :accept ".pdf, .doc, .docx, .ppt, .pptx, .txt, image/*"
                :class (when validation "is-invalid")
                :on-change (set-attachment id title)}]
       [:button.btn.btn-secondary {:on-click (fn [e] (.click (.getElementById js/document (id-to-name id))))}
        (text :t.form/upload)]]
      download-link]]))

(defn- date-field
  [{:keys [id value min max validation] :as opts}]
  ;; TODO: format readonly value in user locale (give basic-field a formatted :value and :previous-value in opts)
  [basic-field opts
   [:input.form-control {:type "date"
                         :name (id-to-name id)
                         :class (when validation "is-invalid")
                         :defaultValue value
                         :min min
                         :max max
                         :on-change (set-field-value id)}]])

(defn- label [{title :title}]
  [:div.form-group
   [:label title]])

(defn- set-license-approval
  [id]
  (fn [event]
    (rf/dispatch [::set-license id (.. event -target -checked)])))

(defn- license [id title approved readonly validation content]
  [:div.license
   [:div.form-check
    [:input.form-check-input {:type "checkbox"
                              :name (str "license" id)
                              :disabled readonly
                              :class (when validation "is-invalid")
                              :default-checked approved
                              :on-change (set-license-approval id)}]
    [:span.form-check-label content]]
   [field-validation-message validation title]])

(defn- link-license
  [{:keys [title id textcontent readonly approved validation]}]
  [license id title approved readonly validation
   [:a {:href textcontent :target "_blank"}
    title " " (external-link)]])

(defn- text-license
  [{:keys [title id textcontent approved readonly validation]}]
  [license id title approved readonly validation
   [:div.license-panel
    [:h6.license-title
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

(defn- field [f]
  (case (:type f)
    "attachment" [attachment-field f]
    "date" [date-field f]
    "description" [text-field f]
    "text" [text-field f]
    "texta" [texta-field f]
    "label" [label f]
    "license" (case (:licensetype f)
                "link" [link-license f]
                "text" [text-license f]
                [unsupported-field f])
    [unsupported-field f]))

(defn- save-button []
  [button-wrapper {:id "save"
                   :text (text :t.form/save)
                   :on-click #(rf/dispatch [::save-application "save" (text :t.form/save)])}])

(defn- submit-button []
  [button-wrapper {:id "submit"
                   :text (text :t.form/submit)
                   :class :btn-primary
                   :on-click #(rf/dispatch [::save-application "submit" (text :t.form/submit)])}])

(defn- editable-state? [state]
  (contains? #{"draft" "returned" "withdrawn"
               :rems.workflow.dynamic/draft :rems.workflow.dynamic/returned}
             state))

(defn- fields [form edit-application]
  (let [application (:application form)
        {:keys [items licenses validation]} edit-application
        validation-by-field-id (index-by [:type :id] validation)
        state (:state application)
        editable? (editable-state? state)
        readonly? (not editable?)]
    [collapsible/component
     {:id "form"
      :title (text :t.form/application)
      :always
      [:div
       (into [:div]
             (for [item (:items form)]
               ^{:key (id-to-name (:id item))}
               [field (assoc (localize-item item)
                             :validation (get-in validation-by-field-id [:item (:id item)])
                             :readonly readonly?
                             :value (get-in items [(:id item) :value])
                             ;; TODO: db doesn't yet contain :previous-value so this is always nil
                             :previous-value (get-in items [(:id item) :previous-value])
                             :diff (get-in items [(:id item) :diff])
                             :app-id (:id application))]))
       (when-let [form-licenses (not-empty (:licenses form))]
         [:div.form-group.field
          [:h4 (text :t.form/licenses)]
          (into [:div#licenses]
                (for [license form-licenses]
                  ^{:key (id-to-name (:id license))}
                  [field (assoc (localize-item license)
                                :validation (get-in validation-by-field-id [:license (:id license)])
                                :readonly readonly?
                                :approved (get licenses (:id license)))]))])]}]))

(defn- info-field
  "A component that shows a readonly field with title and value.

  Used for e.g. displaying applicant attributes."
  [title value]
  [:div.form-group
   [:label title]
   [:div.form-control value]])

(defn- format-event [event]
  {:userid (:userid event)
   :event (localize-event (:event event))
   :comment (:comment event)
   :time (localize-time (:time event))})

(defn- events-view [events]
  (let [has-users? (boolean (some :userid events))]
    [:div
     [:h4 (text :t.form/events)]
     (into [:table#event-table.table.table-hover.mb-0
            [:thead
             [:tr
              (when has-users?
                [:th (text :t.form/user)])
              [:th (text :t.form/event)]
              [:th (text :t.form/comment)]
              [:th (text :t.form/date)]]]
            (into [:tbody]
                  (for [e (sort-by :time > events)]
                    ^{:key (str "event_row_" (:userid e) "_"(:event e) "_" (:comment e) "_" (:time e))}
                    [:tr
                     (when has-users?
                       [:td (:userid e)])
                     [:td (:event e)]
                     [:td.event-comment (:comment e)]
                     [:td.date (:time e)]]))])]))

(defn- application-header [state phases-data events]
  (let [;; the event times have millisecond differences, so they need to be formatted to minute precision before deduping
        events (->> events
                    (map format-event)
                    dedupe)
        last-event (when (:comment (last events))
                     (last events))]
    [collapsible/component
     {:id "header"
      :title [:span#application-state
              (str
               (text :t.applications/state)
               (when state (str ": " (localize-state state))))]
      :always [:div
               [:div.mb-3 {:class (str "state-" state)} (phases phases-data)]
               (when last-event
                 (info-field (text :t.applications/latest-comment)
                             (:comment last-event)))]
      :collapse (when (seq events)
                  [events-view events])}]))


(defn applicant-info [id user-attributes]
  [collapsible/component
   {:id id
    :title (str (text :t.applicant-info/applicant))
    :always [:div.row
             [:div.col-md-6
              [info-field (text :t.applicant-info/username) (or (get user-attributes "commonName")
                                                                (get user-attributes "eppn"))]]
             [:div.col-md-6
              [info-field (text :t.applicant-info/email) (get user-attributes "mail")]]]
    :collapse (into [:form]
                    (for [[k v] (dissoc user-attributes "commonName" "mail")]
                      ^{:key (str k "_" v)}
                      [info-field k v]))}])


(defn- approve-button []
  [button-wrapper {:id "approve"
                   :class "btn-primary"
                   :text (text :t.actions/approve)
                   :on-click #(rf/dispatch [::judge-application "approve" (text :t.actions/approve)])}])

(defn- reject-button []
  [button-wrapper {:id "reject"
                   :class "btn-danger"
                   :text (text :t.actions/reject)
                   :on-click #(rf/dispatch [::judge-application "reject" (text :t.actions/reject)])}])

(defn- return-button []
  [button-wrapper {:id "return"
                   :text (text :t.actions/return)
                   :on-click #(rf/dispatch [::judge-application "return" (text :t.actions/return)])}])

(defn- review-button []
  [button-wrapper {:id "review"
                   :text (text :t.actions/review)
                   :class "btn-primary"
                   :on-click #(rf/dispatch [::judge-application "review" (text :t.actions/review)])}])

(defn- third-party-review-button []
  [button-wrapper {:id "request-review"
                   :text (text :t.actions/review)
                   :class "btn-primary"
                   :on-click #(rf/dispatch [::judge-application "third-party-review" (text :t.actions/review)])}])

(defn- close-button []
  [button-wrapper {:id "close"
                   :text (text :t.actions/close)
                   :on-click #(rf/dispatch [::judge-application "close" (text :t.actions/close)])}])

(defn- withdraw-button []
  [button-wrapper {:id "withdraw"
                   :text (text :t.actions/withdraw)
                   :on-click #(rf/dispatch [::judge-application "withdraw" (text :t.actions/withdraw)])}])

(defn- action-button [id content on-click]
  [:button.btn.mr-3
   {:id id
    :class (if (contains? #{"approve" "approve-reject"} id) "btn-primary" "btn-secondary")
    :type "button"
    :data-toggle "collapse"
    :data-target (str "#" (action-collapse-id id))
    :on-click on-click}
   (str content " ...")])

(defn- approve-action-button []
  [action-button "approve" (text :t.actions/approve)])

(defn- reject-action-button []
  [action-button "reject" (text :t.actions/reject)])

(defn- static-return-action-button []
  [action-button "static-return" (text :t.actions/return)])

(defn- review-action-button []
  [action-button "review" (text :t.actions/review)])

(defn- third-party-review-action-button []
  [action-button "third-party-review" (text :t.actions/review)])

(defn- request-comment-action-button []
  [action-button "request-comment" (text :t.actions/request-comment) #(rf/dispatch [:rems.actions.request-comment/open-form])])

(defn- request-decision-action-button []
  [action-button "request-decision" (text :t.actions/request-decision) #(rf/dispatch [:rems.actions.request-decision/open-form])])

(defn- comment-action-button []
  [action-button "comment" (text :t.actions/comment) #(rf/dispatch [:rems.actions.comment/open-form])])

(defn- close-action-button []
  [action-button "close" (text :t.actions/close) #(rf/dispatch [:rems.actions.close/open-form])])

(defn- decide-action-button []
  [action-button "decide" (text :t.actions/decide) #(rf/dispatch [:rems.actions.decide/open-form])])

(defn- return-action-button []
  [action-button "return" (text :t.actions/return) #(rf/dispatch [:rems.actions.return/open-form])])

(defn- approve-reject-action-button []
  [action-button "approve-reject" (text :t.actions/approve-reject)])

(defn- applicant-close-action-button []
  [action-button "applicant-close" (text :t.actions/close)])

(defn- approver-close-action-button []
  [action-button "approver-close" (text :t.actions/close)])

(defn- withdraw-action-button []
  [action-button "withdraw" (text :t.actions/withdraw)])

(defn- review-request-action-button []
  [action-button "review-request" (text :t.actions/review-request)])

(defn action-form [id title comment-title button content]
  [action-form-view id
   title
   comment-title
   [button]
   content
   @(rf/subscribe [::judge-comment])
   #(rf/dispatch [::set-judge-comment (.. % -target -value)])])

(defn- approve-form []
  [action-form "approve"
   (text :t.actions/approve)
   (text :t.form/add-comments-shown-to-applicant)
   [approve-button]])

(defn- reject-form []
  [action-form "reject"
   (text :t.actions/reject)
   (text :t.form/add-comments-shown-to-applicant)
   [reject-button]])

(defn- static-return-form []
  [action-form "static-return"
   (text :t.actions/return)
   (text :t.form/add-comments-shown-to-applicant)
   [return-button]])

(defn- review-form []
  [action-form "review"
   (text :t.actions/review)
   (text :t.form/add-comments-not-shown-to-applicant)
   [review-button]])

(defn- third-party-review-form []
  [action-form "third-party-review"
   (text :t.actions/review)
   (text :t.form/add-comments-not-shown-to-applicant)
   [third-party-review-button]])

(defn- applicant-close-form []
  [action-form "applicant-close"
   (text :t.actions/close)
   (text :t.form/add-comments)
   [close-button]])

(defn- approver-close-form []
  [action-form "approver-close"
   (text :t.actions/close)
   (text :t.form/add-comments-shown-to-applicant)
   [close-button]])

(defn- withdraw-form []
  [action-form "withdraw"
   (text :t.actions/withdraw)
   (text :t.form/add-comments)
   [withdraw-button]])

(defn- request-review-form []
  (let [selected-third-party-reviewers @(rf/subscribe [::selected-third-party-reviewers])
        potential-third-party-reviewers @(rf/subscribe [::potential-third-party-reviewers])
        review-comment @(rf/subscribe [::review-comment])]
    [action-form "review-request"
     (text :t.actions/review-request)
     nil
     [button-wrapper {:id "request-review"
                      :text (text :t.actions/review-request)
                      :on-click #(rf/dispatch [::send-third-party-review-request selected-third-party-reviewers review-comment (text :t.actions/review-request)])}]
     [:div [:div.form-group
            [:label {:for "review-comment"} (text :t.form/add-comments-not-shown-to-applicant)]
            [textarea {:id "review-comment"
                       :name "comment" :placeholder (text :t.form/comment)
                       :on-change #(rf/dispatch [::set-review-comment (.. % -target -value)])}]]
      [:div.form-group
       [:label (text :t.actions/review-request-selection)]
       [autocomplete/component
        {:value (sort-by :display selected-third-party-reviewers)
         :items potential-third-party-reviewers
         :value->text #(:display %2)
         :item->key :userid
         :item->text :display
         :item->value identity
         :search-fields [:name :email]
         :add-fn #(rf/dispatch [::add-selected-third-party-reviewer %])
         :remove-fn #(rf/dispatch [::remove-selected-third-party-reviewer %])}]]]]))

(defn- dynamic-actions [app]
  (distinct
   (mapcat #:rems.workflow.dynamic
           {:submit [^{:key "save_action_button"} [save-button]
                     ^{:key "submit_action_button"} [submit-button]]
            :add-member nil ; TODO implement
            :return [^{:key "return_action_button"} [return-action-button]]
            :request-decision [^{:key "request-decision_action_button"} [request-decision-action-button]]
            :decide [^{:key "decide_action_button"} [decide-action-button]]
            :request-comment [^{:key "request-comment_action_button"} [request-comment-action-button]]
            :comment [^{:key "comment_action_button"} [comment-action-button]]
            :approve [^{:key "approve_action_button"} [approve-reject-action-button]]
            :reject [^{:key "reject_action_button"} [approve-reject-action-button]]
            :close [^{:key "close_action_button"} [close-action-button]]}
           (:possible-commands app))))

(defn- static-actions [app]
  (let [state (:state app)
        editable? (editable-state? state)]
    (concat (when (:can-close? app)
              [(if (:is-applicant? app)
                 ^{:key "applicant-close_action_button"}
                 [applicant-close-action-button]
                 ^{:key "approver-close_action_button"}
                 [approver-close-action-button])])
            (when (:can-withdraw? app)
              [^{:key "withdraw_action_button"}
              [withdraw-action-button]])
            (when (:can-approve? app)
              [^{:key "review_request_action_button"}
               [review-request-action-button]
               ^{:key "static_return_action_button"}
               [static-return-action-button]
               ^{:key "reject_action_button"}
               [reject-action-button]
               ^{:key "approve_action_button"}
               [approve-action-button]])
            (when (= :normal (:review-type app))
              [^{:key "review_action_button"}
               [review-action-button]])
            (when (= :third-party (:review-type app))
              [^{:key "third-party-review_action_button"}
               [third-party-review-action-button]])
            (when (and (:is-applicant? app) editable?)
              [^{:key "save_action_button"}
               [save-button]
               ^{:key "submit_action_button"}
               [submit-button]]))))

(defn- actions-form [app]
  (let [actions (if (= :workflow/dynamic (get-in app [:workflow :type]))
                  (dynamic-actions app)
                  (static-actions app))
        reload #(rf/dispatch [:rems.application/enter-application-page (:id app)])
        forms [[:div#actions-forms.mt-3
                [approve-form]
                [reject-form]
                [static-return-form]
                [review-form]
                [request-review-form]
                [request-comment-form (:id app) reload]
                [request-decision-form (:id app) reload]
                [comment-form (:id app) reload]
                [close-form (:id app) reload]
                [decide-form (:id app) reload]
                [return-form (:id app) reload]
                [approve-reject-form (:id app) reload]
                [third-party-review-form]
                [applicant-close-form]
                [approver-close-form]
                [withdraw-form]]]]
    (when (seq actions)
      [collapsible/component
       {:id "actions"
        :title (text :t.form/actions)
        :always (into [:div [:div.commands actions]] forms)}])))

(defn- disabled-items-warning [catalogue-items]
  (let [language @(rf/subscribe [:language])]
    (when-some [items (seq (filter #(= "disabled" (:state %)) catalogue-items))]
      [:div.alert.alert-danger
       (text :t.form/alert-disabled-items)
       (into [:ul]
             (for [item items]
               ^{:key (str "disabled_item_" item)}
               [:li (get-catalogue-item-title item language)]))])))

(defn- applied-resources [catalogue-items]
  (let [language @(rf/subscribe [:language])]
    [collapsible/component
     {:id "resources"
      :title (text :t.form/resources)
      :always [:div.form-items.form-group
               (into [:ul]
                     (for [item catalogue-items]
                       ^{:key (id-to-name (:id item))}
                       [:li (get-catalogue-item-title item language)]))]}]))

(defn- dynamic-event->event [{:keys [time actor event comment decision]}]
  {:userid actor
   :time time
   :event (name event)
   :comment (if (= :event/decided event)
              (str (localize-decision decision) ": " comment)
              comment)})

(defn- render-application [application edit-application language status]
  (let [app (:application application)
        state (:state app)
        phases (:phases application)
        events (concat (:events app)
                       (map dynamic-event->event (:dynamic-events app)))
        user-attributes (:applicant-attributes application)
        messages (remove nil?
                         [(disabled-items-warning (:catalogue-items application)) ; NB: eval this here so we get nil or a warning
                          (when @(rf/subscribe [::send-third-party-review-request-message])
                            [flash-message
                             {:status :success
                              :contents (text :t.actions/review-request-success)}])
                          (when (:validation edit-application)
                            [flash-message
                             {:status :danger
                              :contents [:div (text :t.form/validation.errors)
                                         [format-validation-messages (:validation edit-application) language]]}])])]
    [:div
     [:div {:class "float-right"} [pdf-button (:id app)]]
     [:h2 (text :t.applications/application)]
     (into [:div] messages)
     [application-header state phases events]
     (when user-attributes
       [:div.mt-3 [applicant-info "applicant-info" user-attributes]])
     [:div.mt-3 [applied-resources (:catalogue-items application)]]
     [:div.my-3 [fields application edit-application]]
     [:div.mb-3 [actions-form app]]
     (when (:open? status)
       [status-modal (assoc status
                            :content (when (seq messages) (into [:div] messages))
                            :on-close #(rf/dispatch [::set-status nil]))])]))

;;;; Entrypoint

(defn application-page []
  (let [application @(rf/subscribe [::application])
        edit-application @(rf/subscribe [::edit-application])
        language @(rf/subscribe [:language])
        loading? (not application)
        status (:status edit-application)]
    (if loading?
      [:div
       [:h2 (text :t.applications/application)]
       [spinner/big]]
      [render-application application edit-application language status])))






;;;; Guide

(defn guide []
  (let [next-id (atom 0)
        next-id! #(str "auto_id_" (swap! next-id inc))]
    [:div
    (component-info info-field)
    (example "info-field with data"
             [info-field "Name" "Bob Tester"])
    (component-info applicant-info)
    (example "applicant-info"
             [applicant-info "info1" {"eppn" "developer@uu.id"
                                      "mail" "developer@uu.id"
                                      "commonName" "Deve Loper"
                                      "organization" "Testers"
                                      "address" "Testikatu 1, 00100 Helsinki"}])
    (example "applicant-info with name missing"
             [applicant-info "info2" {"eppn" "developer@uu.id"
                                      "mail" "developer@uu.id"
                                      "organization" "Testers"
                                      "address" "Testikatu 1, 00100 Helsinki"}])

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
              [field {:id (next-id!) :type "text" :title "Title" :inputprompt "prompt"}]])
    (example "field of type \"text\" with validation error"
             [:form
              [field {:id (next-id!) :type "text" :title "Title" :inputprompt "prompt"
                      :validation {:key :t.form.validation.required}}]])
    (example "non-editable field of type \"text\" without text"
             [:form
              [field {:id (next-id!) :type "text" :title "Title" :inputprompt "prompt" :readonly true}]])
    (example "non-editable field of type \"text\" with text"
             [:form
              [field {:id (next-id!) :type "text" :title "Title" :inputprompt "prompt" :readonly true :value lipsum-short}]])
    (example "field of type \"texta\""
             [:form
              [field {:id (next-id!) :type "texta" :title "Title" :inputprompt "prompt"}]])
    (example "field of type \"texta\" with validation error"
             [:form
              [field {:id (next-id!) :type "texta" :title "Title" :inputprompt "prompt"
                      :validation {:key :t.form.validation.required}}]])
    (example "non-editable field of type \"texta\""
             [:form
              [field {:type "texta" :title "Title" :inputprompt "prompt" :readonly true :value lipsum-paragraphs}]])
    (let [previous-lipsum-paragraphs (-> lipsum-paragraphs
                                         (str/replace "ipsum primis in faucibus orci luctus" "eu mattis purus mi eu turpis")
                                         (str/replace "per inceptos himenaeos" "justo erat hendrerit magna"))]
      [:div
       (example "editable field of type \"texta\" with previous value, diff hidden"
                [:form
                 [field {:id (next-id!) :type "texta" :title "Title" :inputprompt "prompt" :value lipsum-paragraphs :previous-value previous-lipsum-paragraphs}]])
       (example "editable field of type \"texta\" with previous value, diff shown"
                [:form
                 [field {:id (next-id!) :type "texta" :title "Title" :inputprompt "prompt" :value lipsum-paragraphs :previous-value previous-lipsum-paragraphs :diff true}]])
       (example "non-editable field of type \"texta\" with previous value, diff hidden"
                [:form
                 [field {:id (next-id!) :type "texta" :title "Title" :inputprompt "prompt" :readonly true :value lipsum-paragraphs :previous-value previous-lipsum-paragraphs}]])
       (example "non-editable field of type \"texta\" with previous value, diff shown"
                [:form
                 [field {:id (next-id!) :type "texta" :title "Title" :inputprompt "prompt" :readonly true :value lipsum-paragraphs :previous-value previous-lipsum-paragraphs :diff true}]])
       (example "non-editable field of type \"texta\" with previous value equal to current value"
                [:form
                 [field {:id (next-id!) :type "texta" :title "Title" :inputprompt "prompt" :readonly true :value lipsum-paragraphs :previous-value lipsum-paragraphs}]])])
    (example "field of type \"attachment\""
             [:form
              [field {:id (next-id!) :type "attachment" :title "Title"}]])
    (example "field of type \"attachment\", file uploaded"
             [:form
              [field {:id (next-id!) :type "attachment" :title "Title" :value "test.txt"}]])
    (example "non-editable field of type \"attachment\""
             [:form
              [field {:id (next-id!) :type "attachment" :title "Title" :readonly true}]])
    (example "non-editable field of type \"attachment\", file uploaded"
             [:form
              [field {:id (next-id!) :type "attachment" :title "Title" :readonly true :value "test.txt"}]])
    (example "field of type \"date\""
             [:form
              [field {:id (next-id!) :type "date" :title "Title"}]])
    (example "field of type \"date\" with value"
             [:form
              [field {:id (next-id!) :type "date" :title "Title" :value "2000-12-31"}]])
    (example "non-editable field of type \"date\""
             [:form
              [field {:id (next-id!) :type "date" :title "Title" :readonly true :value ""}]])
    (example "non-editable field of type \"date\" with value"
             [:form
              [field {:id (next-id!) :type "date" :title "Title" :readonly true :value "2000-12-31"}]])
    (example "optional field"
             [:form
              [field {:id (next-id!) :type "texta" :optional "true" :title "Title" :inputprompt "prompt"}]])
    (example "field of type \"label\""
             [:form
              [field {:id (next-id!) :type "label" :title "Lorem ipsum dolor sit amet"}]])
    (example "field of type \"description\""
             [:form
              [field {:id (next-id!) :type "description" :title "Title" :inputprompt "prompt"}]])
    (example "link license"
             [:form
              [field {:id (next-id!) :type "license" :title "Link to license" :licensetype "link" :textcontent "/guide"}]])
    (example "link license with validation error"
             [:form
              [field {:id (next-id!) :type "license" :title "Link to license" :licensetype "link" :textcontent "/guide"
                      :validation {:field {:title "Link to license"} :key :t.form.validation.required}}]])
    (example "text license"
             [:form
              [field {:id (next-id!) :type "license" :title "A Text License" :licensetype "text"
                      :textcontent lipsum-paragraphs}]])
    (example "text license with validation error"
             [:form
              [field {:id (next-id!) :type "license" :title "A Text License" :licensetype "text" :textcontent lipsum-paragraphs
                      :validation {:field {:title "A Text License"} :key :t.form.validation.required}}]])

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
               :licenses {5 true 6 true}}])]))
