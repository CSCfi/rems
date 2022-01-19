(ns rems.application
  (:require [clojure.string :as str]
            [goog.string]
            [re-frame.core :as rf]
            [rems.actions.accept-licenses :refer [accept-licenses-action-button]]
            [rems.actions.components :refer [button-wrapper]]
            [rems.actions.add-licenses :refer [add-licenses-action-button add-licenses-form]]
            [rems.actions.add-member :refer [add-member-action-button add-member-form]]
            [rems.actions.approve-reject :refer [approve-reject-action-button approve-reject-form]]
            [rems.actions.assign-external-id :refer [assign-external-id-button assign-external-id-form]]
            [rems.actions.change-applicant :refer [change-applicant-action-button change-applicant-form]]
            [rems.actions.change-resources :refer [change-resources-action-button change-resources-form]]
            [rems.actions.close :refer [close-action-button close-form]]
            [rems.actions.decide :refer [decide-action-button decide-form]]
            [rems.actions.delete :refer [delete-action-button delete-form]]
            [rems.actions.invite-decider-reviewer :refer [invite-decider-action-link invite-reviewer-action-link invite-decider-form invite-reviewer-form]]
            [rems.actions.invite-member :refer [invite-member-action-button invite-member-form]]
            [rems.actions.remark :refer [remark-action-button remark-form]]
            [rems.actions.remove-member :refer [remove-member-action-button remove-member-form]]
            [rems.actions.request-decision :refer [request-decision-action-link request-decision-form]]
            [rems.actions.request-review :refer [request-review-action-link request-review-form]]
            [rems.actions.return-action :refer [return-action-button return-form]]
            [rems.actions.review :refer [review-action-button review-form]]
            [rems.actions.revoke :refer [revoke-action-button revoke-form]]
            [rems.application-list :as application-list]
            [rems.administration.duo :refer [resource-duo-view-compact]]
            [rems.common.application-util :refer [accepted-licenses? form-fields-editable? get-member-name]]
            [rems.common.attachment-types :as attachment-types]
            [rems.atoms :refer [external-link file-download info-field readonly-checkbox document-title success-symbol empty-symbol]]
            [rems.common.catalogue-util :refer [catalogue-item-more-info-url]]
            [rems.collapsible :as collapsible]
            [rems.common.form :as form]
            [rems.common.util :refer [build-index index-by parse-int]]
            [rems.fetcher :as fetcher]
            [rems.fields :as fields]
            [rems.focus :as focus]
            [rems.flash-message :as flash-message]
            [rems.guide-util :refer [component-info example lipsum lipsum-paragraphs]]
            [rems.phase :refer [phases]]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-decision localize-event localized localize-state localize-time text text-format]]
            [rems.user :as user]
            [rems.util :refer [navigate! fetch post! focus-input-field focus-when-collapse-opened format-file-size]]))

;;;; Helpers

(defn reload! [application-id & [full-reload?]]
  (rf/dispatch [::fetch-application application-id full-reload?]))

(defn- disabled-items-warning [application]
  (when (contains? (:application/permissions application) :see-everything) ; don't show to applicants
    (when-some [resources (->> (:application/resources application)
                               (filter #(or (not (:catalogue-item/enabled %))
                                            (:catalogue-item/expired %)
                                            (:catalogue-item/archived %)))
                               seq)]
      [:div.alert.alert-warning
       (text :t.form/alert-disabled-resources)
       (into [:ul]
             (for [resource resources]
               [:li (localized (:catalogue-item/title resource))]))])))

(defn- blacklist-warning [application]
  (let [resources-by-id (group-by :resource/ext-id (:application/resources application))
        blacklist (:application/blacklist application)]
    (when (not (empty? blacklist))
      [:div.alert.alert-danger
       (text :t.form/alert-blacklisted-users)
       (into [:ul]
             (for [entry blacklist
                   resource (get resources-by-id (get-in entry [:blacklist/resource :resource/ext-id]))]
               [:li (get-member-name (:blacklist/user entry))
                ": " (localized (:catalogue-item/title resource))]))])))

(defn- format-validation-error [type field]
  [:a {:href "#" :on-click (case (:field/type field)
                             ;; workaround for tables: there's no single input to focus
                             :table #(focus/focus-selector (str "#container-" (fields/field-name field)))
                             ;; default
                             (focus-input-field (fields/field-name field)))}
   (text-format type (localized (:field/title field)))])

(defn- format-validation-errors
  [application errors]
  (let [fields-index (index-by [:form/id :field/id]
                               (for [form (:application/forms application)
                                     field (:form/fields form)]
                                 (assoc field :form/id (:form/id form))))]
    [:div (text :t.actions.errors/validation-errors)
     (into [:ul]
           (concat
            (for [{:keys [type form-id field-id]} errors]
              [:li (if (and form-id field-id)
                     (format-validation-error type (get-in fields-index [form-id field-id]))
                     (text type))])))]))


;;;; State

(rf/reg-sub ::application-id (fn [db _] (::application-id db)))
(rf/reg-sub ::application (fn [db [_ k]] (get-in db [::application (or k :data)])))
(rf/reg-sub ::edit-application (fn [db _] (::edit-application db)))

(rf/reg-event-fx
 ::enter-application-page
 (fn [{:keys [db]} [_ id]]
   {:db (-> db
            (assoc ::application-id (parse-int id))
            (dissoc ::application ::edit-application))
    :dispatch [::fetch-application id true]}))

(rf/reg-event-fx
 ::fetch-application
 (fn [{:keys [db]} [_ id full-reload?]]
   (fetch (str "/api/applications/" id)
          {:handler #(rf/dispatch [::fetch-application-result % full-reload?])
           :error-handler (comp #(rf/dispatch [::fetch-application-result nil full-reload?])
                                (flash-message/default-error-handler :top [text :t.applications/application]))})
   {:db (update db ::application fetcher/started)}))

(defn- initialize-edit-application [db]
  (let [application (get-in db [::application :data])
        field-values (->> (for [form (:application/forms application)
                                field (:form/fields form)]
                            {:form (:form/id form)
                             :field (:field/id field)
                             :value (:field/value field)})
                          (build-index {:keys [:form :field] :value-fn :value}))]
    (assoc db ::edit-application {:field-values field-values
                                  :show-diff {}
                                  :validation-errors nil
                                  :attachment-status {}})))

(rf/reg-event-db
 ::fetch-application-result
 (fn [db [_ application full-reload?]]
   (let [initial-fetch? (not (:initialized? (::application db)))]
     (cond-> (update db ::application fetcher/finished application)
       (or initial-fetch? full-reload?) (initialize-edit-application)))))

(rf/reg-event-db
 ::set-validation-errors
 (fn [db [_ errors]]
   (assoc-in db [::edit-application :validation-errors] errors)))

(defn- field-values-to-api [application field-values]
  (for [form (:application/forms application)
        :let [form-id (:form/id form)]
        field (:form/fields form)
        :let [field-id (:field/id field)]
        :when (form/field-visible? field (get field-values form-id))]
    {:form form-id :field field-id :value (get-in field-values [form-id field-id])}))

(defn- handle-validation-errors [description application on-success]
  (fn [response]
    (if (:success response)
      (on-success)
      (do
        (let [validation-errors (filter :field-id (:errors response))]
          (rf/dispatch [::set-validation-errors validation-errors]))
        ;; validation errors can be too long for :actions location
        (flash-message/show-default-error! :top description [format-validation-errors application (:errors response)])))))

(defn- save-application! [description application field-values on-success]
  (post! "/api/applications/save-draft"
         {:params {:application-id (:application/id application)
                   :field-values (field-values-to-api application field-values)}
          :handler (handle-validation-errors
                    description
                    application
                    on-success)
          :error-handler (flash-message/default-error-handler :actions description)}))


(rf/reg-event-fx
 ::save-application
 (fn [{:keys [db]} [_ description on-success]]
   (let [application (:data (::application db))
         edit-application (::edit-application db)]
     (save-application! description
                        application
                        (:field-values edit-application)
                        #(do
                           (rf/dispatch [::fetch-application (:application/id application)])
                           (if on-success
                             (on-success)
                             (flash-message/show-default-success! :actions description)))))
   {:db (assoc-in db [::edit-application :validation-errors] nil)}))

(defn- submit-application! [description application field-values]
  (save-application! description
                     application
                     field-values
                     (fn []
                       (post! "/api/applications/submit"
                              {:params {:application-id (:application/id application)}
                               :handler (handle-validation-errors
                                         description
                                         application
                                         #(do
                                            (flash-message/show-default-success! :actions description)
                                            (rf/dispatch [::fetch-application (:application/id application)])))
                               :error-handler (flash-message/default-error-handler :actions description)}))))

(rf/reg-event-fx
 ::submit-application
 (fn [{:keys [db]} [_ description]]
   (let [application (:data (::application db))
         edit-application (::edit-application db)]
     (submit-application! description
                          application
                          (:field-values edit-application)))
   {:db (assoc-in db [::edit-application :validation-errors] nil)}))

(rf/reg-event-fx
 ::copy-as-new-application
 (fn [{:keys [db]} _]
   (let [application-id (get-in db [::application :data :application/id])
         description [text :t.form/copy-as-new]]
     (post! "/api/applications/copy-as-new"
            {:params {:application-id application-id}
             :handler (flash-message/default-success-handler
                       :top ; the message will be shown on the new application's page
                       description
                       #(navigate! (str "/application/" (:application-id %))))
             :error-handler (flash-message/default-error-handler :actions description)}))
   {}))

(defn- save-attachment [{:keys [db]} [_ form-id field-id file]]
  (let [application-id (get-in db [::application :data :application/id])
        current-attachments (form/parse-attachment-ids (get-in db [::edit-application :field-values form-id field-id]))
        description [text :t.form/upload]
        config @(rf/subscribe [:rems.config/config])
        file-size (.. file (get "file") -size)
        file-name (.. file (get "file") -name)]
    (rf/dispatch [::set-attachment-status form-id field-id :pending])
    (if (some-> (:attachment-max-size config)
                (< file-size))
      (do
        (rf/dispatch [::set-attachment-status form-id field-id :error])
        (flash-message/show-default-error! :actions description
                                           [:div
                                            [:p [text :t.form/too-large-attachment]]
                                            [:p (str file-name " " (format-file-size file-size))]
                                            [:p [text-format :t.form/attachment-max-size (format-file-size (:attachment-max-size config))]]]))
      (post! "/api/applications/add-attachment"
             {:url-params {:application-id application-id}
              :body file
              ;; force saving a draft when you upload an attachment.
              ;; this ensures that the attachment is not left
              ;; dangling (with no references to it)
              :handler (fn [response]
                         ;; no need to check (:success response) - the API can't fail at the moment
                         ;; no race condition here: events are handled in a FIFO manner
                         (rf/dispatch [::set-field-value form-id field-id (form/unparse-attachment-ids
                                                                           (conj current-attachments (:id response)))])
                         (rf/dispatch [::save-application description
                                       #(rf/dispatch [::set-attachment-status form-id field-id :success])]))
              :error-handler (fn [response]
                               (rf/dispatch [::set-attachment-status form-id field-id :error])
                               (cond (= 413 (:status response))
                                     (flash-message/show-default-error! :actions description
                                                                        [:div
                                                                         [:p [text :t.form/too-large-attachment]]
                                                                         [:p (str file-name " " (format-file-size file-size))]
                                                                         [:p [text-format :t.form/attachment-max-size (format-file-size (:attachment-max-size config))]]])

                                     (= 415 (:status response))
                                     (flash-message/show-default-error! :actions description
                                                                        [:div
                                                                         [:p [text :t.form/invalid-attachment]]
                                                                         [:p [text-format :t.form/upload-extensions attachment-types/allowed-extensions-string]]])

                                     :else ((flash-message/default-error-handler :actions description) response)))})))
  {})

(rf/reg-event-fx ::save-attachment save-attachment)

(rf/reg-event-db
 ::remove-attachment
 (fn [db [_ form-id field-id attachment-id]]
   (update-in db [::edit-application :field-values form-id field-id]
              (comp form/unparse-attachment-ids
                    (partial remove #{attachment-id})
                    form/parse-attachment-ids))))

(rf/reg-event-db
 ::set-field-value
 (fn [db [_ form-id field-id value]]
   (assoc-in db [::edit-application :field-values form-id field-id] value)))

(rf/reg-event-db
 ::set-attachment-status
 (fn [db [_ form-id field-id value]]
   (assoc-in db [::edit-application :attachment-status form-id field-id] value)))

(rf/reg-event-db
 ::toggle-diff
 (fn [db [_ field-id]]
   (update-in db [::edit-application :show-diff field-id] not)))

(fetcher/reg-fetcher ::previous-applications "/api/applications")

(rf/reg-sub
 ::previous-applications-except-current
 (fn [& _]
   [(rf/subscribe [::application-id])
    (rf/subscribe [::previous-applications])
    (rf/subscribe [::previous-applications :error])
    (rf/subscribe [::previous-applications :initialized?])
    (rf/subscribe [::previous-applications :fetching?])
    (rf/subscribe [::previous-applications :searching?])])
 (fn [[application-id data error initialized? fetching? searching?]
      [_id key]]
   (case key
     :error error
     :initialized? initialized?
     :fetching? fetching?
     :searching? searching?
     nil (filterv #(not= application-id (:application/id %)) data))))

(rf/reg-event-db
 ::highlight-request-id
 (fn [db [_ request-id]]
   (assoc db ::highlight-request-id request-id)))

(rf/reg-sub
 ::highlight-request-id
 (fn [db _]
   (::highlight-request-id db)))

;;;; UI components

(defn- pdf-button [app-id]
  (when app-id
    [:a.btn.btn-secondary
     {:href (str "/api/applications/" app-id "/pdf")
      :target :_blank}
     [external-link] " PDF"]))

(defn- attachment-zip-button [application]
  (when-not (empty? (:application/attachments application))
    [:a.btn.btn-secondary
     {:href (str "/api/applications/" (:application/id application) "/attachments?all=false")
      :target :_blank}
     [file-download] " " (text :t.form/attachments-as-zip)]))

(defn- link-license [license]
  (let [title (localized (:license/title license))
        link (localized (:license/link license))]
    [:div
     [:a.license-title {:href link :target :_blank}
      title " " [external-link]]]))

(defn- text-license [license]
  (let [id (:license/id license)
        collapse-id (str "collapse" id)
        title (localized (:license/title license))
        text (localized (:license/text license))]
    [:div.license-panel
     [:span.license-title
      [:a.license-header.collapsed {:data-toggle "collapse"
                                    :href (str "#" collapse-id)
                                    :aria-expanded "false"
                                    :aria-controls collapse-id}
       title]]
     [:div.collapse {:id collapse-id
                     :ref focus-when-collapse-opened
                     :tab-index "-1"}
      [:div.license-block (str/trim (str text))]]]))

(defn- attachment-license [application license]
  (let [title (localized (:license/title license))
        language @(rf/subscribe [:language])
        link (str "/applications/" (:application/id application)
                  "/license-attachment/" (:license/id license)
                  "/" (name language))]
    [:a.license-title {:href link :target :_blank}
     title " " [file-download]]))

(defn license-field [application license show-accepted-licenses?]
  [:div.license.flex-row.d-flex
   [:div.mr-2 (when show-accepted-licenses?
                (if (:accepted license)
                  (success-symbol)
                  (empty-symbol)))]
   (case (:license/type license)
     :link [link-license license]
     :text [text-license license]
     :attachment [attachment-license application license]
     [fields/unsupported-field license])])

(defn- save-button []
  [button-wrapper {:id "save"
                   :text (text :t.form/save)
                   :on-click #(rf/dispatch [::save-application [text :t.form/save]])}])

(defn- submit-button []
  [button-wrapper {:id "submit"
                   :text (text :t.form/submit)
                   :class :btn-primary
                   :on-click #(rf/dispatch [::submit-application [text :t.form/submit]])}])

(defn- copy-as-new-button []
  [button-wrapper {:id "copy-as-new"
                   :text (text :t.form/copy-as-new)
                   :on-click #(rf/dispatch [::copy-as-new-application])}])

(defn- application-fields [application edit-application]
  (let [field-values (:field-values edit-application)
        show-diff (:show-diff edit-application)
        field-validations (index-by [:form-id :field-id] (:validation-errors edit-application))
        attachments (index-by [:attachment/id] (:application/attachments application))
        form-fields-editable? (form-fields-editable? application)
        readonly? (not form-fields-editable?)
        language @(rf/subscribe [:language])]
    (into [:div]
          (for [form (:application/forms application)
                :let [form-id (:form/id form)]
                :when (->> (:form/fields form)
                           (remove :field/private)
                           seq)]
            [collapsible/component
             {:class "mb-3"
              :title (or (get-in form [:form/external-title language]) (text :t.form/application))
              :always (into [:div.fields]
                            (for [field (:form/fields form)
                                  :let [field-id (:field/id field)]
                                  :when (and (form/field-visible? field (get field-values form-id))
                                             (not (:field/private field)))] ; private fields will have empty value anyway
                              [fields/field (merge field
                                                   {:form/id form-id
                                                    :field/value (get-in field-values [form-id field-id])

                                                    :diff (get show-diff field-id)
                                                    :validation (get-in field-validations [form-id field-id])
                                                    :readonly readonly?
                                                    :app-id (:application/id application)
                                                    :on-change #(rf/dispatch [::set-field-value form-id field-id %])
                                                    :on-toggle-diff #(rf/dispatch [::toggle-diff field-id])}
                                                   (when (= :attachment (:field/type field))
                                                     {:field/attachments (->> (get-in field-values [form-id field-id])
                                                                              form/parse-attachment-ids
                                                                              (mapv attachments)
                                                                              ;; The field value can contain an id that's not in attachments when a new attachment has been
                                                                              ;; uploaded, but the application hasn't yet been refetched.
                                                                              (remove nil?))
                                                      :field/previous-attachments (when-let [prev (:field/previous-value field)]
                                                                                    (->> prev
                                                                                         form/parse-attachment-ids
                                                                                         (mapv attachments)))
                                                      :field/attachment-status (get-in edit-application [:attachment-status form-id field-id])
                                                      :on-attach #(rf/dispatch [::save-attachment form-id field-id %1 %2])
                                                      :on-remove-attachment #(rf/dispatch [::remove-attachment form-id field-id %1])}))]))}]))))

(defn- application-licenses [application userid]
  (when-let [licenses (not-empty (:application/licenses application))]
    (let [application-id (:application/id application)
          roles (:application/roles application)
          show-accepted-licenses? (or (contains? roles :member)
                                      (contains? roles :applicant))
          accepted-licenses (get (:application/accepted-licenses application) userid)
          permissions (:application/permissions application)
          form-fields-editable? (form-fields-editable? application)
          readonly? (not form-fields-editable?)]
      [collapsible/component
       {:id "application-licenses"
        :title (text :t.form/licenses)
        :always
        [:div
         [flash-message/component :accept-licenses]
         [:p (text :t.form/must-accept-licenses)]
         (into [:div#licenses]
               (for [license licenses]
                 [license-field
                  application
                  (assoc license
                         :accepted (contains? accepted-licenses (:license/id license))
                         :readonly readonly?)
                  show-accepted-licenses?]))
         (when (contains? permissions :application.command/add-licenses)
           [:<>
            [:div.commands [add-licenses-action-button]]
            [add-licenses-form application-id (partial reload! application-id)]])
         (if (accepted-licenses? application userid)
           [:div#has-accepted-licenses (text :t.form/has-accepted-licenses)]
           (when (contains? permissions :application.command/accept-licenses)
             [:div.commands
              ;; TODO consider saving the form first so that no data is lost for the applicant
              [accept-licenses-action-button application-id (mapv :license/id licenses) #(reload! application-id)]]))]}])))

(defn- application-link [application prefix]
  (let [config @(rf/subscribe [:rems.config/config])]
    [:a {:href (str "/application/" (:application/id application))}
     (when prefix
       (str prefix " "))
     (application-list/format-application-id config application)]))

(defn- event-view [event]
  (let [event-text (localize-event event)
        decision (localize-decision event)
        comment (case (:event/type event)
                  :application.event/copied-from
                  [application-link (:application/copied-from event) (text :t.applications/application)]

                  :application.event/copied-to
                  [application-link (:application/copied-to event) (text :t.applications/application)]

                  (when (not (empty? (:application/comment event)))
                    (:application/comment event)))
        request-id (:application/request-id event)
        attachments (:event/attachments event)
        time (localize-time (:event/time event))]
    [:div.row.event
     {:class (when (:highlight event)
               "border rounded border-primary")}
     [:label.col-sm-2.col-form-label time]
     [:div.col-sm-10
      [:div.col-form-label.event-description [:b event-text]
       (when request-id
         [:div.float-right
          [:a {:href "#"
               :on-click (fn [e]
                           (rf/dispatch [::highlight-request-id request-id])
                           false)}
           " " (text :t.applications/highlight-related-events)]])]
      (when decision
        [:div.event-decision decision])
      (when comment
        [:div.form-control.event-comment comment])
      (when-let [attachments (seq attachments)]
        [fields/attachment-row attachments])]]))

(defn- render-events [events]
  (for [e events]
    [event-view e]))

(defn- get-application-phases [state]
  (cond (contains? #{:application.state/rejected} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
         {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]

        (contains? #{:application.state/revoked} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :approved? true :text :t.phases/approve}
         {:phase :result :completed? true :revoked? true :text :t.phases/revoked}]

        (contains? #{:application.state/approved} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :approved? true :text :t.phases/approve}
         {:phase :result :completed? true :approved? true :text :t.phases/approved}]

        (contains? #{:application.state/closed} state)
        [{:phase :apply :closed? true :text :t.phases/apply}
         {:phase :approve :closed? true :text :t.phases/approve}
         {:phase :result :closed? true :text :t.phases/approved}]

        (contains? #{:application.state/draft :application.state/returned} state)
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        (contains? #{:application.state/submitted} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :active? true :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        :else
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]))

(defn- application-copy-notice [application]
  (let [old-app (:application/copied-from application)
        new-apps (:application/copied-to application)]
    (when (or old-app new-apps)
      [:<>
       " ("
       (when old-app
         [:<> (text :t.applications/copied-from) " " [application-link old-app nil]])
       (when (and old-app new-apps)
         "; ")
       (when new-apps
         (into [:<> (text :t.applications/copied-to) " "]
               (interpose ", " (for [new-app new-apps]
                                 [application-link new-app nil]))))
       ")"])))

(defn- events-with-attachments [application]
  (let [attachments-by-id (index-by [:attachment/id] (:application/attachments application))]
    (for [event (:application/events application)]
      (update event :event/attachments (partial mapv (comp attachments-by-id :attachment/id))))))

(defn- application-state [application config highlight-request-id]
  (let [state (:application/state application)
        last-activity (:application/last-activity application)
        events (->> (events-with-attachments application)
                    (sort-by :event/time)
                    (map #(assoc % :highlight
                                 (and highlight-request-id
                                      (= (:application/request-id %) highlight-request-id))))
                    reverse)
        [events-show-always events-collapse] (split-at 3 events)]
    [collapsible/component
     {:id "header"
      :title (text :t.applications/state)
      :always (into [:div
                     [:div.mb-3
                      [phases state (get-application-phases state)]]
                     [info-field
                      (text :t.applications/application)
                      [:<>
                       [:span#application-id (application-list/format-application-id config application)]
                       [application-copy-notice application]]
                      {:inline? true}]
                     [info-field
                      (text :t.applications/description)
                      (:application/description application)
                      {:inline? true}]
                     [info-field
                      (text :t.applications/state)
                      [:span#application-state (localize-state state)]
                      {:inline? true}]
                     [info-field
                      (text :t.applications/latest-activity)
                      (localize-time last-activity)
                      {:inline? true}]]
                    (when (seq events-show-always)
                      (into [[:h3 (text :t.form/events)]]
                            (render-events events-show-always))))
      :collapse (when (seq events-collapse)
                  (into [:div]
                        (render-events events-collapse)))}]))

(defn member-info
  "Renders a applicant, member or invited member of an application

  `:element-id`         - id of the element to generate unique ids
  `:attributes`         - user attributes to display
  `:application`        - application
  `:group?`             - specifies if a group border is rendered"
  [{:keys [element-id attributes application group?]}]
  (let [application-id (:application/id application)
        user-id (:userid attributes)
        invited-user? (nil? user-id)
        applicant? (= (:userid (:application/applicant application)) user-id)
        title (cond applicant? (text :t.applicant-info/applicant)
                    invited-user? (text :t.applicant-info/invited-member)
                    :else (text :t.applicant-info/member))
        accepted? (accepted-licenses? application user-id)
        permissions (:application/permissions application)
        can-remove? (and (not applicant?)
                         (contains? permissions :application.command/remove-member))
        can-uninvite? (and invited-user?
                           (contains? permissions :application.command/uninvite-member))
        can-change? (and (not applicant?)
                         (not invited-user?)
                         (contains? permissions :application.command/change-applicant))]
    [collapsible/minimal
     {:id (str element-id "-info")
      :class (when group? "group")
      :always [:div
               [:h3 title]
               [user/username attributes]
               (when-not (or invited-user?
                             (= :application.state/draft (:application/state application)))
                 [info-field (text :t.form/accepted-licenses) [readonly-checkbox {:value accepted?}] {:inline? true}])]
      :collapse [user/attributes attributes invited-user?]
      :footer (let [element-id (str element-id "-operations")]
                [:div {:id element-id}
                 [:div.commands
                  (when can-change?
                    [change-applicant-action-button element-id])
                  (when (or can-remove? can-uninvite?)
                    [remove-member-action-button element-id])]
                 [change-applicant-form element-id attributes application-id (partial reload! application-id)]
                 [remove-member-form element-id attributes application-id (partial reload! application-id)]])}]))

(defn applicants-info
  "Renders the applicants, i.e. applicant and members."
  [application]
  (let [application-id (:application/id application)
        applicant (:application/applicant application)
        members (:application/members application)
        invited-members (:application/invited-members application)
        permissions (:application/permissions application)
        can-add? (contains? permissions :application.command/add-member)
        can-invite? (contains? permissions :application.command/invite-member)]
    [collapsible/component
     {:id "applicants-info"
      :title (text :t.applicant-info/applicants)
      :always
      (into [:div
             [flash-message/component :change-members]
             [member-info {:element-id "applicant"
                           :attributes applicant
                           :application application
                           :group? (or (seq members)
                                       (seq invited-members))}]]
            (concat
             (for [[index member] (map-indexed vector (sort-by :name members))]
               [member-info {:element-id (str "member" index)
                             :attributes member
                             :application application
                             :group? true}])
             (for [[index invited-member] (map-indexed vector (sort-by :name invited-members))]
               [member-info {:element-id (str "invite" index)
                             :attributes invited-member
                             :application application
                             :group? true}])))
      :footer [:div
               [:div.commands
                (when can-invite? [invite-member-action-button])
                (when can-add? [add-member-action-button])]
               [:div#member-action-forms
                [invite-member-form application-id (partial reload! application-id)]
                [add-member-form application-id (partial reload! application-id)]]]}]))

(defn- request-review-dropdown []
  [:div.btn-group
   [:button#request-review-dropdown.btn.btn-secondary.dropdown-toggle
    {:data-toggle :dropdown}
    (text :t.actions/request-review-dropdown)]
   [:div.dropdown-menu
    [request-review-action-link]
    [invite-reviewer-action-link]]])

(defn- request-decision-dropdown []
  [:div.btn-group
   [:button#request-decision-dropdown.btn.btn-secondary.dropdown-toggle
    {:data-toggle :dropdown}
    (text :t.actions/request-decision-dropdown)]
   [:div.dropdown-menu
    [request-decision-action-link]
    [invite-decider-action-link]]])

(defn- action-buttons [application]
  (let [commands-and-actions [:application.command/save-draft [save-button]
                              :application.command/submit [submit-button]
                              :application.command/return [return-action-button]
                              ;; this assumes that request-review and invite-reviewer are both possible or neither is
                              :application.command/request-review [request-review-dropdown]
                              :application.command/invite-reviewer [request-review-dropdown]
                              :application.command/review [review-action-button]
                              ;; ditto for decision
                              :application.command/request-decision [request-decision-dropdown]
                              :application.command/invite-decider [request-decision-dropdown]
                              :application.command/decide [decide-action-button]
                              :application.command/remark [remark-action-button]
                              :application.command/approve [approve-reject-action-button]
                              :application.command/reject [approve-reject-action-button]
                              :application.command/revoke [revoke-action-button]
                              :application.command/assign-external-id (when (:enable-assign-external-id-ui @(rf/subscribe [:rems.config/config]))
                                                                        [assign-external-id-button (get application :application/assigned-external-id "")])
                              :application.command/close [close-action-button]
                              :application.command/delete [delete-action-button]
                              :application.command/copy-as-new [copy-as-new-button]]]
    (concat (distinct (for [[command action] (partition 2 commands-and-actions)
                            :when (contains? (:application/permissions application) command)]
                        action))
            (list [pdf-button (:application/id application)]
                  [attachment-zip-button application]))))


(defn- actions-form [application]
  (let [app-id (:application/id application)
        ;; The :see-everything permission is used to determine whether the user
        ;; is allowed to see all comments. It would not make sense for the user
        ;; to be able to write a comment which he then cannot see.
        show-comment-field? (contains? (:application/permissions application) :see-everything)
        actions (action-buttons application)
        reload (partial reload! app-id)
        forms [[:div#actions-forms.mt-3
                [request-review-form app-id reload]
                [request-decision-form app-id reload]
                [invite-decider-form app-id reload]
                [invite-reviewer-form app-id reload]
                [review-form app-id reload]
                [remark-form app-id reload]
                [close-form app-id show-comment-field? reload]
                [revoke-form app-id reload]
                [decide-form app-id reload]
                [return-form app-id reload]
                [approve-reject-form app-id reload]
                [assign-external-id-form app-id reload]
                [delete-form app-id #(do (flash-message/show-default-success! :top [text :t.actions/delete])
                                         (navigate! "/catalogue"))]]]]
    (when (seq actions)
      [collapsible/component
       {:id "actions-collapse"
        :title (text :t.form/actions)
        :always (into [:div (into [:div#action-commands]
                                  actions)]
                      forms)}])))

(defn- render-resource [resource language]
  ^{:key (:catalogue-item/id resource)}
  (let [config @(rf/subscribe [:rems.config/config])
        duo-codes (get-in resource [:resource/duo :duo/codes])]
    [:div.application-resource
     {:class (when (:enable-duo config) "solid-group")}
     [:h3.resource-label
      (localized (:catalogue-item/title resource))
      (when-let [url (catalogue-item-more-info-url resource language config)]
        [:<>
         " – "
         [:a {:href url :target :_blank}
          (text :t.catalogue/more-info) " " [external-link]]])]
     (when (and (:enable-duo config)
                (seq duo-codes))
       (into [:div.resource-duo-codes]
             (for [code duo-codes]
               [resource-duo-view-compact code (:catalogue-item/id resource)])))]))

(defn- applied-resources [application userid language]
  (let [application-id (:application/id application)
        permissions (:application/permissions application)
        applicant? (= (:userid (:application/applicant application)) userid)
        can-change-resources? (contains? permissions :application.command/change-resources)
        can-comment? (not applicant?)]
    [collapsible/component
     {:id "resources"
      :title (text :t.form/resources)
      :always [:div.form-items.form-group
               [flash-message/component :change-resources]
               (into [:div.application-resources]
                     (for [resource (:application/resources application)]
                       [render-resource resource language]))]
      :footer [:div
               [:div.commands
                (when can-change-resources? [change-resources-action-button (:application/resources application)])]
               [:div#resource-action-forms
                [change-resources-form application can-comment? (partial reload! application-id true)]]]}]))

(defn- previous-applications [applicant]
  ;; print mode forces the collapsible open, so fetch the content proactively
  ;; TODO figure out a better solution
  (rf/dispatch [::previous-applications {:query (str "(applicant:\"" applicant "\" OR member:\"" applicant "\") AND -state:draft")}])
  [collapsible/component
   {:id "previous-applications"
    :title (text :t.form/previous-applications)
    :collapse [application-list/component {:applications ::previous-applications-except-current
                                           :hidden-columns #{:created :handlers :todo :last-activity}
                                           :default-sort-column :submitted
                                           :default-sort-order :desc}]}])

(defn- render-application [{:keys [application edit-application config userid highlight-request-id language]}]
  [:<>
   [disabled-items-warning application]
   [blacklist-warning application]
   (text :t.applications/intro)
   [:div.row
    [:div.col-lg-8
     [application-state application config highlight-request-id]
     [:div.mt-3 [applicants-info application]]
     [:div.mt-3 [applied-resources application userid language]]
     (when (contains? (:application/permissions application) :see-everything)
       [:div.mt-3 [previous-applications (get-in application [:application/applicant :userid])]])
     [:div.my-3 [application-licenses application userid]]
     [:div.mt-3 [application-fields application edit-application]]]
    [:div.col-lg-4.spaced-vertically-3
     [:div#actions
      [flash-message/component :actions]
      [actions-form application]]]]])

;;;; Entrypoint

(defn application-page []
  (let [config @(rf/subscribe [:rems.config/config])
        application-id @(rf/subscribe [::application-id])
        application @(rf/subscribe [::application])
        loading? @(rf/subscribe [::application :loading?])
        reloading? @(rf/subscribe [::application :reloading?])
        edit-application @(rf/subscribe [::edit-application])
        highlight-request-id @(rf/subscribe [::highlight-request-id])
        userid (:userid @(rf/subscribe [:user]))
        language @(rf/subscribe [:language])]
    [:div.container-fluid
     [document-title (str (text :t.applications/application)
                          (when application
                            (str " " (application-list/format-application-id config application)))
                          (when-not (str/blank? (:application/description application))
                            (str ": " (:application/description application))))]
     ^{:key application-id} ; re-render to clear flash messages when navigating to another application
     [flash-message/component :top]
     (when loading?
       [spinner/big])
     (when application
       [render-application {:application application
                            :edit-application edit-application
                            :config config
                            :userid userid
                            :highlight-request-id highlight-request-id
                            :language language}])
     ;; Located after the application to avoid re-rendering the application
     ;; when this element is added or removed from virtual DOM.
     (when reloading?
       [:div.reload-indicator
        [spinner/small]])]))

;;;; Guide

(defn guide []
  [:div
   (component-info member-info)
   (example "member-info: applicant with notification email, accepted licenses, researcher status"
            [member-info {:element-id "info1"
                          :attributes {:userid "developer@uu.id"
                                       :email "developer@uu.id"
                                       :name "Deve Loper"
                                       :notification-email "notification@example.com"
                                       :organizations [{:organization/id "Testers"} {:organization/id "Users"}]
                                       :address "Testikatu 1, 00100 Helsinki"
                                       :researcher-status-by "so"}
                          :application {:application/id 42
                                        :application/applicant {:userid "developer@uu.id"}
                                        :application/licenses [{:license/id 1}]
                                        :application/accepted-licenses {"developer@uu.id" #{1}}}}])
   (example "member-info with name missing"
            [member-info {:element-id "info2"
                          :attributes {:userid "developer"
                                       :email "developer@uu.id"
                                       :address "Testikatu 1, 00100 Helsinki"}}])
   (example "member-info with buttons, licenses not accepted"
            [member-info {:element-id "info3"
                          :attributes {:userid "alice"}
                          :application {:application/id 42
                                        :application/applicant {:userid "developer"}
                                        :application/licenses [{:license/id 1}]
                                        :application/permissions #{:application.command/remove-member
                                                                   :application.command/change-applicant}}
                          :group? true}])
   (example "member-info: invited member"
            [member-info {:element-id "info4"
                          :attributes {:name "John Smith"
                                       :email "john.smith@invited.com"}
                          :application {:application/id 42
                                        :application/applicant {:userid "developer"}}
                          :group? true}])
   (example "member-info: invited member, with remove button"
            [member-info {:element-id "info4"
                          :attributes {:name "John Smith"
                                       :email "john.smith@invited.com"}
                          :application {:application/id 42
                                        :application/applicant {:userid "developer"}
                                        :application/permissions #{:application.command/uninvite-member}}
                          :group? true}])

   (component-info applicants-info)
   (example "applicants-info"
            [applicants-info {:application/id 42
                              :application/applicant {:userid "developer"
                                                      :email "developer@uu.id"
                                                      :name "Deve Loper"}
                              :application/members #{{:userid "alice"}
                                                     {:userid "bob"}}
                              :application/invited-members #{{:name "John Smith" :email "john.smith@invited.com"}}
                              :application/licenses [{:license/id 1}]
                              :application/accepted-licenses {"developer" #{1}}
                              :application/permissions #{:application.command/add-member
                                                         :application.command/invite-member}}])
   (component-info disabled-items-warning)
   (example "no disabled items"
            [disabled-items-warning {}])
   (example "two disabled items"
            [disabled-items-warning
             {:application/state :application.state/submitted
              :application/resources [{:catalogue-item/enabled true :catalogue-item/archived true
                                       :catalogue-item/title {:en "Catalogue item 1"}}
                                      {:catalogue-item/enabled false :catalogue-item/archived false
                                       :catalogue-item/title {:en "Catalogue item 2"}}
                                      {:catalogue-item/enabled true :catalogue-item/archived false
                                       :catalogue-item/title {:en "Catalogue item 3"}}]}])
   (component-info blacklist-warning)
   (example "no blacklist"
            [blacklist-warning {}])
   (example "three entries"
            [blacklist-warning {:application/blacklisted-users [{:blacklist/user {:userid "user1" :name "First User" :email "first@example.com"}
                                                                 :blacklist/resource {:resource/ext-id "urn:11"}}
                                                                {:blacklist/user {:userid "user1" :name "First User" :email "first@example.com"}
                                                                 :blacklist/resource {:resource/ext-id "urn:12"}}
                                                                {:blacklist/user {:userid "user2" :name "Second User" :email "second@example.com"}
                                                                 :blacklist/resource {:resource/ext-id "urn:11"}}]}])

   (component-info license-field)
   (example "link license"
            [license-field
             {:application/id 123}
             {:license/id 1
              :license/type :link
              :license/title {:en "Link to license"}
              :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}}
             false])
   (example "link license, not accepted"
            [license-field
             {:application/id 123}
             {:license/id 1
              :license/type :link
              :license/title {:en "Link to license"}
              :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}
              :accepted false}
             true])
   (example "link license, accepted"
            [license-field
             {:application/id 123}
             {:license/id 1
              :license/type :link
              :license/title {:en "Link to license"}
              :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}
              :accepted true}
             true])
   (example "text license"
            [license-field
             {:application/id 123}
             {:license/id 1
              :license/type :text
              :license/title {:en "A Text License"}
              :license/text {:en lipsum-paragraphs}}
             false])
   (example "attachment license"
            [license-field
             {:application/id 123}
             {:license/id 1
              :license/type :attachment
              :license/title {:en "A Text License"}
              :license/text {:en lipsum-paragraphs}}
             false])

   (component-info actions-form)
   (example "all actions available"
            [actions-form {:application/id 123
                           :application/permissions #{:application.command/save-draft
                                                      :application.command/submit
                                                      :application.command/return
                                                      :application.command/request-review
                                                      :application.command/review
                                                      :application.command/request-decision
                                                      :application.command/decide
                                                      :application.command/remark
                                                      :application.command/approve
                                                      :application.command/reject
                                                      :application.command/revoke
                                                      :application.command/assign-external-id
                                                      :application.command/close
                                                      :application.command/copy-as-new}
                           :application/attachments [{:attachment/filename "foo.txt"} {:attachment/filename "bar.txt"}]}])

   (component-info render-application)
   (example "application, partially filled, as applicant"
            [render-application
             {:application {:application/id 17
                            :application/applicant {:userid "applicant"}
                            :application/roles #{:applicant}
                            :application/permissions #{:application.command/accept-licenses}
                            :application/state :application.state/draft
                            :application/resources [{:catalogue-item/title {:en "An applied item"}}]
                            :application/forms [{:form/id 1
                                                 :form/fields [{:field/id "fld1"
                                                                :field/type :text
                                                                :field/title {:en "Field 1"}
                                                                :field/placeholder {:en "placeholder 1"}}
                                                               {:field/id "fld2"
                                                                :field/type :label
                                                                :title "Please input your wishes below."}
                                                               {:field/id "fld3"
                                                                :field/type :texta
                                                                :field/optional true
                                                                :field/title {:en "Field 2"}
                                                                :field/placeholder {:en "placeholder 2"}}
                                                               {:field/id "fld4"
                                                                :field/type :unsupported
                                                                :field/title {:en "Field 3"}
                                                                :field/placeholder {:en "placeholder 3"}}
                                                               {:field/id "fld5"
                                                                :field/type :date
                                                                :field/title {:en "Field 4"}}]}]
                            :application/licenses [{:license/id 4
                                                    :license/type :text
                                                    :license/title {:en "A Text License"}
                                                    :license/text {:en lipsum}}
                                                   {:license/id 5
                                                    :license/type :link
                                                    :license/title {:en "Link to license"}
                                                    :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}}]}
              :edit-application {:field-values {1 {"fld1" "abc"}}
                                 :show-diff {}
                                 :validation-errors nil
                                 :accepted-licenses {"applicant" #{5}}}
              :userid "applicant"}])
   (example "application, applied"
            [render-application
             {:application {:application/id 17
                            :application/state :application.state/submitted
                            :application/resources [{:catalogue-item/title {:en "An applied item"}}]
                            :application/forms [{:form/id 1
                                                 :form/fields [{:field/id "fld1"
                                                                :field/type :text
                                                                :field/title {:en "Field 1"}
                                                                :field/placeholder {:en "placeholder 1"}}]}]
                            :application/licenses [{:license/id 4
                                                    :license/type :text
                                                    :license/title {:en "A Text License"}
                                                    :license/text {:en lipsum}}]}
              :edit-application {:field-values {1 {"fld1" "abc"}}
                                 :accepted-licenses #{4}}}])
   (example "application, approved"
            [render-application
             {:application {:application/id 17
                            :application/state :application.state/approved
                            :application/applicant {:userid "eppn"
                                                    :email "email@example.com"
                                                    :additional "additional field"}
                            :application/resources [{:catalogue-item/title {:en "An applied item"}}]
                            :application/forms [{:form/id 1
                                                 :form/fields [{:field/id "fld1"
                                                                :field/type :text
                                                                :field/title {:en "Field 1"}
                                                                :field/placeholder {:en "placeholder 1"}}]}]
                            :application/licenses [{:license/id 4
                                                    :license/type :text
                                                    :license/title {:en "A Text License"}
                                                    :license/text {:en lipsum}}]}
              :edit-application {:field-values {1 {"fld1" "abc"}}
                                 :accepted-licenses #{4}}}])

   (component-info event-view)
   (example "simple event"
            [event-view {:event/time #inst "2020-01-01T08:35"
                         :event/type :application.event/submitted
                         :event/actor-attributes {:userid "alice" :name "Alice Applicant"}}])
   (example "event with comment & decision"
            [event-view {:event/time #inst "2020-01-01T08:35"
                         :event/type :application.event/decided
                         :event/actor-attributes {:name "Hannah Handler"}
                         :application/decision :rejected
                         :application/comment "This application is bad."}])
   (example "event with comment & decision, highlighted"
            [event-view {:event/time #inst "2020-01-01T08:35"
                         :event/type :application.event/decided
                         :event/actor-attributes {:name "Hannah Handler"}
                         :application/decision :rejected
                         :application/comment "This application is bad."
                         :highlight true}])
   (example "event with long comment & attachment"
            [event-view {:event/time #inst "2020-01-01T08:35"
                         :event/type :application.event/remarked
                         :event/actor-attributes {:name "Hannah Handler"}
                         :application/comment (str lipsum "\n\nA final line.")
                         :event/attachments [{:attachment/filename "verylongfilename_loremipsum_dolorsitamet.pdf"}]}])
   (example "event with many attachments"
            [event-view {:event/time #inst "2020-01-01T08:35"
                         :event/type :application.event/approved
                         :event/actor-attributes {:name "Hannah Handler"}
                         :event/attachments (repeat 30 {:attachment/filename "image.jpeg"})}])

   (component-info application-copy-notice)
   (example "no copies"
            [application-copy-notice {}])
   (example "copied from"
            [application-copy-notice {:application/copied-from {:application/id 1
                                                                :application/external-id "2018/10"}}])
   (example "copied to one"
            [application-copy-notice {:application/copied-to [{:application/id 2
                                                               :application/external-id "2019/20"}]}])
   (example "copied to many"
            [application-copy-notice {:application/copied-to [{:application/id 2
                                                               :application/external-id "2019/20"}
                                                              {:application/id 3
                                                               :application/external-id "2020/30"}]}])
   (example "copied to and from"
            [application-copy-notice {:application/copied-from {:application/id 1
                                                                :application/external-id "2018/10"}
                                      :application/copied-to [{:application/id 2
                                                               :application/external-id "2019/20"}
                                                              {:application/id 3
                                                               :application/external-id "2020/30"}]}])])
