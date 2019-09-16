(ns rems.application
  (:require [clojure.set :refer [union]]
            [clojure.string :as str]
            [goog.string]
            [medley.core :refer [map-vals]]
            [re-frame.core :as rf]
            [rems.actions.accept-licenses :refer [accept-licenses-action-button]]
            [rems.actions.action :refer [action-button action-form-view action-comment action-collapse-id button-wrapper]]
            [rems.actions.add-licenses :refer [add-licenses-action-button add-licenses-form]]
            [rems.actions.add-member :refer [add-member-action-button add-member-form]]
            [rems.actions.approve-reject :refer [approve-reject-action-button approve-reject-form]]
            [rems.actions.change-resources :refer [change-resources-action-button change-resources-form]]
            [rems.actions.close :refer [close-action-button close-form]]
            [rems.actions.decide :refer [decide-action-button decide-form]]
            [rems.actions.invite-member :refer [invite-member-action-button invite-member-form]]
            [rems.actions.remark :refer [remark-action-button remark-form]]
            [rems.actions.remove-member :refer [remove-member-action-button remove-member-form]]
            [rems.actions.request-decision :refer [request-decision-action-button request-decision-form]]
            [rems.actions.request-review :refer [request-review-action-button request-review-form]]
            [rems.actions.return-action :refer [return-action-button return-form]]
            [rems.actions.review :refer [review-action-button review-form]]
            [rems.application-util :refer [accepted-licenses? form-fields-editable? get-member-name]]
            [rems.atoms :refer [external-link file-download info-field readonly-checkbox textarea document-title success-symbol empty-symbol]]
            [rems.catalogue-util :refer [urn-catalogue-item-link]]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [index-by]]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.guide-utils :refer [lipsum lipsum-short lipsum-paragraphs]]
            [rems.phase :refer [phases]]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-decision localize-event localized localize-state localize-time text text-format]]
            [rems.util :refer [dispatch! fetch parse-int post! in-page-anchor-link]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;;;; Helpers

(defn reload! [application-id]
  (rf/dispatch [:rems.application/reload-application-page application-id]))

(defn- in-processing? [application]
  (not (contains? #{:application.state/approved
                    :application.state/rejected
                    :application.state/closed}
                  (:application/state application))))

(defn- disabled-items-warning [application]
  (when (in-processing? application)
    (when-some [resources (->> (:application/resources application)
                               (filter #(or (not (:catalogue-item/enabled %))
                                            (:catalogue-item/expired %)
                                            (:catalogue-item/archived %)))
                               seq)]
      [:div.alert.alert-danger
       (text :t.form/alert-disabled-resources)
       (into [:ul]
             (for [resource resources]
               [:li (localized (:catalogue-item/title resource))]))])))

(defn navigate-to
  "Navigates to the application with the given id.

  `replace?` parameter can be given to replace history state instead of push."
  [id & [replace?]]
  (dispatch! (str "#/application/" id) replace?))

(defn- format-validation-error [type field]
  [:a {:href "#"
       :on-click (in-page-anchor-link (fields/id-to-name (:field/id field)))}
   (text-format type (localized (:field/title field)))])

(defn- format-submission-errors
  [application errors]
  (let [fields-by-id (->> (get-in application [:application/form :form/fields])
                          (index-by [:field/id]))]
    [:div (text :t.actions.errors/submission-failed)
     (into [:ul]
           (concat
            (for [{:keys [type field-id]} errors]
              [:li (if field-id
                     (format-validation-error type (get fields-by-id field-id))
                     (text type))])))]))


;;;; State

(rf/reg-sub ::application (fn [db _] (::application db)))
(rf/reg-sub ::edit-application (fn [db _] (::edit-application db)))

(rf/reg-event-fx
 ::enter-application-page
 (fn [{:keys [db]} [_ id]]
   {:db (dissoc db ::application ::edit-application ::attachment-success)
    :dispatch [::fetch-application id]}))

(rf/reg-event-fx
 ::fetch-application
 (fn [_ [_ id]]
   (fetch (str "/api/applications/" id)
          {:handler #(rf/dispatch [::fetch-application-result %])})
   {}))

(rf/reg-event-db
 ::fetch-application-result
 (fn [db [_ application]]
   (assoc db
          ::application application
          ::edit-application {:field-values (->> (get-in application [:application/form :form/fields])
                                                 (map (juxt :field/id :field/value))
                                                 (into {}))
                              :show-diff {}
                              :validation-errors nil})))

(rf/reg-event-fx
 ::reload-application-page
 (fn [{:keys [db]} [_ id]]
   {::reload-application id}))

(rf/reg-fx
 ::reload-application
 (fn [id]
   (fetch (str "/api/applications/" id)
          {:handler #(rf/dispatch [::reload-application-result %])})))

(rf/reg-event-db
 ::reload-application-result
 (fn [db [_ application]]
   (assoc db
          ::application application)))

(rf/reg-event-db
 ::set-validation-errors
 (fn [db [_ errors]]
   (assoc-in db [::edit-application :validation-errors] errors)))

(defn- field-values-to-api [field-values]
  (for [[field value] field-values]
    {:field field :value value}))

(defn- save-application! [description application-id field-values]
  (post! "/api/applications/save-draft"
         {:params {:application-id application-id
                   :field-values (field-values-to-api field-values)}
          :handler (flash-message/default-success-handler
                    :top
                    description
                    (fn [_]
                      (rf/dispatch [::fetch-application application-id])))
          :error-handler (flash-message/default-error-handler :top description)}))

(rf/reg-event-fx
 ::save-application
 (fn [{:keys [db]} [_ description]]
   (let [application (::application db)
         edit-application (::edit-application db)]
     (save-application! description
                        (:application/id application)
                        (:field-values edit-application)))
   {:db (assoc-in db [::edit-application :validation-errors] nil)}))

(defn- submit-application! [application description application-id field-values
                            userid]
  ;; TODO: deduplicate with save-application!
  (post! "/api/applications/save-draft"
         {:params {:application-id application-id
                   :field-values (field-values-to-api field-values)}
          :handler (fn [response]
                     (cond
                       (not (:success response))
                       (flash-message/show-default-error! :top description)

                       :else
                       (post! "/api/applications/submit"
                              {:params {:application-id application-id}
                               :handler (fn [response]
                                          (if (:success response)
                                            (do
                                              (rf/dispatch [::fetch-application application-id])
                                              (flash-message/show-default-success! :top description))
                                            (do
                                              (let [validation-errors
                                                    (filter :field-id (:errors response))]
                                                (rf/dispatch [::set-validation-errors validation-errors]))
                                              (flash-message/show-error! :top [format-submission-errors application (:errors response)]))))
                               :error-handler (flash-message/default-error-handler :top description)})))
          :error-handler (flash-message/default-error-handler :top description)}))

(rf/reg-event-fx
 ::submit-application
 (fn [{:keys [db]} [_ description]]
   (let [application (::application db)
         edit-application (::edit-application db)]
     (submit-application! application
                          description
                          (:application/id application)
                          (:field-values edit-application)
                          (get-in (:identity db) [:user :eppn])))
   {:db (assoc-in db [::edit-application :validation-errors] nil)}))

(rf/reg-event-fx
 ::copy-as-new-application
 (fn [{:keys [db]} _]
   (let [application-id (get-in db [::application :application/id])
         description (text :t.form/copy-as-new)]
     (post! "/api/applications/copy-as-new"
            {:params {:application-id application-id}
             :handler (flash-message/default-success-handler
                       :top
                       description
                       (fn [response]
                         (rf/dispatch [:rems.spa/user-triggered-navigation])
                         (dispatch! (str "/#/application/" (:application-id response)))))
             :error-handler (flash-message/default-error-handler :top description)}))
   {}))

(defn- save-attachment [{:keys [db]} [_ field-id file description]]
  (let [application-id (get-in db [::application :application/id])]
    (post! "/api/applications/add-attachment"
           {:url-params {:application-id application-id
                         :field-id field-id}
            :body file
            ;; force saving a draft when you upload an attachment.
            ;; this ensures that the attachment is not left
            ;; dangling (with no references to it)
            :handler (flash-message/default-success-handler
                      :top
                      description
                      (fn [response]
                        ;; no race condition here: events are handled in a FIFO manner
                        (rf/dispatch [::set-field-value field-id (str (:id response))])
                        (rf/dispatch [::set-attachment-success field-id])
                        (rf/dispatch [::save-application description])))
            :error-handler (flash-message/default-error-handler :top description)})
    {}))

(rf/reg-event-fx ::save-attachment save-attachment)

(rf/reg-event-db
 ::set-field-value
 (fn [db [_ field-id value]]
   (assoc-in db [::edit-application :field-values field-id] value)))

(rf/reg-event-db
 ::set-attachment-success
 (fn [db [_ field-id]]
   (assoc db ::attachment-success field-id)))

(rf/reg-sub
 ::attachment-success
 (fn [db] (::attachment-success db)))

(rf/reg-event-db
 ::toggle-diff
 (fn [db [_ field-id]]
   (update-in db [::edit-application :show-diff field-id] not)))

;;;; UI components

(defn- link-license [license]
  (let [title (localized (:license/title license))
        link (localized (:license/link license))]
    [:a.license-title {:href link :target :_blank}
     title " " [external-link]]))

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
                     :ref (fn [elem]
                            (when elem
                              (.on (js/$ elem)
                                   "shown.bs.collapse"
                                   #(.focus elem))))
                     :tab-index "-1"}
      [:div.license-block (str/trim (str text))]]]))

(defn- attachment-license [license]
  (let [title (localized (:license/title license))
        link (str "/api/licenses/attachments/" (localized (:license/attachment-id license)))]
    [:a.license-title {:href link :target :_blank}
     title " " [file-download]]))

(defn license-field [license show-accepted-licenses?]
  [:div.license
   (when show-accepted-licenses?
     (if (:accepted license)
       (success-symbol)
       (empty-symbol)))
   (case (:license/type license)
     :link [link-license license]
     :text [text-license license]
     :attachment [attachment-license license]
     [fields/unsupported-field license])])

(defn- save-button []
  [button-wrapper {:id "save"
                   :text (text :t.form/save)
                   :on-click #(rf/dispatch [::save-application (text :t.form/save)])}])

(defn- submit-button []
  [button-wrapper {:id "submit"
                   :text (text :t.form/submit)
                   :class :btn-primary
                   :on-click #(rf/dispatch [::submit-application (text :t.form/submit)])}])

(defn- copy-as-new-button []
  [button-wrapper {:id "copy-as-new"
                   :text (text :t.form/copy-as-new)
                   :on-click #(rf/dispatch [::copy-as-new-application])}])

(defn- application-fields [application edit-application attachment-success]
  (let [field-values (:field-values edit-application)
        show-diff (:show-diff edit-application)
        field-validations (index-by [:field-id] (:validation-errors edit-application))
        attachments (index-by [:attachment/id] (:application/attachments application))
        form-fields-editable? (form-fields-editable? application)
        readonly? (not form-fields-editable?)]
    [collapsible/component
     {:id "application-fields"
      :title (text :t.form/application)
      :always
      [:div
       (into [:div]
             (for [fld (get-in application [:application/form :form/fields])]
               [fields/field (assoc fld
                                    :on-change #(rf/dispatch [::set-field-value (:field/id fld) %])
                                    :on-set-attachment #(rf/dispatch [::save-attachment (:field/id fld) %1 %2])
                                    :on-remove-attachment #(do
                                                             (rf/dispatch [::set-field-value (:field/id fld) ""])
                                                             (rf/dispatch [::set-attachment-success (:field/id fld)]))
                                    :on-toggle-diff #(rf/dispatch [::toggle-diff (:field/id fld)])
                                    :field/value (get field-values (:field/id fld))
                                    :field/attachment (when (= :attachment (:field/type fld))
                                                        (get attachments (parse-int (:field/value fld))))
                                    :field/previous-attachment (when (= :attachment (:field/type fld))
                                                                 (when-let [prev (:field/previous-value fld)]
                                                                   (get attachments (parse-int prev))))
                                    :success (= attachment-success (:field/id fld))
                                    :diff (get show-diff (:field/id fld))
                                    :validation (field-validations (:field/id fld))
                                    :readonly readonly?
                                    :app-id (:application/id application))]))]}]))

(defn- application-licenses [application edit-application userid]
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
                  (assoc license
                         :accepted (contains? accepted-licenses (:license/id license))
                         :readonly readonly?)
                  show-accepted-licenses?]))
         (if (accepted-licenses? application userid)
           [:div#has-accepted-licenses.alert.alert-success (text :t.form/has-accepted-licenses)]
           (when (contains? permissions :application.command/accept-licenses)
             [:div.commands
              ;; TODO consider saving the form first so that no data is lost for the applicant
              [accept-licenses-action-button application-id (mapv :license/id licenses) #(reload! application-id)]]))]}])))

(defn- format-application-id [config application]
  (let [id-column (get config :application-id-column :id)]
    (case id-column
      :external-id (:application/external-id application)
      :id (:application/id application)
      (:application/id application))))

(defn- application-link [application prefix]
  (let [config @(rf/subscribe [:rems.config/config])]
    [:a {:href (str "/#/application/" (:application/id application))}
     (when prefix
       (str prefix " "))
     (format-application-id config application)]))

(defn- format-event [event]
  {:userid (:event/actor event)
   :event (localize-event (:event/type event))
   :comment (case (:event/type event)
              :application.event/decided
              (str (localize-decision (:application/decision event)) ": " (:application/comment event))

              :application.event/copied-from
              [application-link (:application/copied-from event) (text :t.applications/application)]

              :application.event/copied-to
              [application-link (:application/copied-to event) (text :t.applications/application)]

              (:application/comment event))
   :request-id (:application/request-id event)
   :commenters (:application/commenters event)
   :deciders (:application/deciders event)
   :time (localize-time (:event/time event))})

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
    (into [:div.group]
          (for [e group]
            [event-view e]))))

(defn- get-application-phases [state]
  (cond (contains? #{:application.state/rejected} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
         {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]

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

(defn- application-state [application config]
  (let [state (:application/state application)
        last-activity (:application/last-activity application)
        event-groups (->> (:application/events application)
                          (group-by #(or (:application/request-id %)
                                         (:event/id %)))
                          vals
                          (map (partial sort-by :event/time))
                          (sort-by #(:event/time (first %)))
                          reverse
                          (map #(map format-event %)))]
    [collapsible/component
     {:id "header"
      :title (text :t.applications/state)
      :always (into [:div
                     [:div.mb-3 {:class (str "state-" (name state))}
                      (phases (get-application-phases state))]
                     [info-field
                      (text :t.applications/application)
                      [:<>
                       [:span#application-id (format-application-id config application)]
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
                    (when-let [g (first event-groups)]
                      (into [[:h3 (text :t.form/events)]]
                            (render-event-groups [g]))))
      :collapse (when-let [g (seq (rest event-groups))]
                  (into [:div]
                        (render-event-groups g)))}]))

(defn member-info
  "Renders a applicant, member or invited member of an application

  `:element-id`         - id of the element to generate unique ids
  `:attributes`         - user attributes to display
  `:application`        - application
  `:group?`             - specifies if a group border is rendered
  `:can-remove?`        - can the user be removed?
  `:accepted-licenses?` - has the member accepted the licenses?"
  [{:keys [element-id attributes application group? can-remove? accepted-licenses?]}]
  (let [application-id (:application/id application)
        user-id (or (:eppn attributes) (:userid attributes))
        other-attributes (dissoc attributes :commonName :name :eppn :userid :mail :email)
        title (cond (= (:application/applicant application) user-id) (text :t.applicant-info/applicant)
                    (:userid attributes) (text :t.applicant-info/member)
                    :else (text :t.applicant-info/invited-member))]
    [collapsible/minimal
     {:id (str element-id "-info")
      :class (when group? "group")
      :always [:div
               [:h3 title]
               (when-let [name (get-member-name attributes)]
                 [info-field (text :t.applicant-info/name) name {:inline? true}])
               (when-not (nil? accepted-licenses?)
                 [info-field (text :t.form/accepted-licenses) [readonly-checkbox accepted-licenses?] {:inline? true}])]
      :collapse (into [:div
                       (when user-id
                         [info-field (text :t.applicant-info/username) user-id {:inline? true}])
                       (when-let [mail (or (:mail attributes) (:email attributes))]
                         [info-field (text :t.applicant-info/email) mail {:inline? true}])]
                      (for [[k v] other-attributes]
                        [info-field k v {:inline? true}]))
      :footer (let [element-id (str element-id "-remove-member")]
                [:div {:id element-id}
                 (when can-remove?
                   [:div.commands
                    [remove-member-action-button element-id]])
                 (when can-remove?
                   [remove-member-form element-id attributes application-id (partial reload! application-id)])])}]))

(defn applicants-info
  "Renders the applicants, i.e. applicant and members."
  [application]
  (let [application-id (:application/id application)
        applicant (merge {:userid (:application/applicant application)}
                         (:application/applicant-attributes application))
        members (:application/members application)
        invited-members (:application/invited-members application)
        permissions (:application/permissions application)
        can-add? (contains? permissions :application.command/add-member)
        can-remove? (contains? permissions :application.command/remove-member)
        can-invite? (contains? permissions :application.command/invite-member)
        can-uninvite? (contains? permissions :application.command/uninvite-member)]
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
                                       (seq invited-members))
                           :can-remove? false
                           :accepted-licenses? (when (not= :application.state/draft (:application/state application))
                                                 (accepted-licenses? application (:userid applicant)))}]]
            (concat
             (for [[index member] (map-indexed vector members)]
               [member-info {:element-id (str "member" index)
                             :attributes member
                             :application application
                             :group? true
                             :can-remove? can-remove?
                             :accepted-licenses? (accepted-licenses? application (:userid member))}])
             (for [[index invited-member] (map-indexed vector invited-members)]
               [member-info {:element-id (str "invite" index)
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

(defn- action-buttons [application]
  (let [commands-and-actions [:application.command/save-draft [save-button]
                              :application.command/submit [submit-button]
                              :application.command/return [return-action-button]
                              :application.command/request-comment [request-review-action-button]
                              :application.command/comment [review-action-button]
                              :application.command/request-decision [request-decision-action-button]
                              :application.command/decide [decide-action-button]
                              :application.command/add-licenses [add-licenses-action-button]
                              :application.command/remark [remark-action-button]
                              :application.command/approve [approve-reject-action-button]
                              :application.command/reject [approve-reject-action-button]
                              :application.command/close [close-action-button]
                              :application.command/copy-as-new [copy-as-new-button]]]
    (distinct (for [[command action] (partition 2 commands-and-actions)
                    :when (contains? (:application/permissions application) command)]
                action))))

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
                [review-form app-id reload]
                [remark-form app-id reload]
                [close-form app-id show-comment-field? reload]
                [decide-form app-id reload]
                [return-form app-id reload]
                [add-licenses-form app-id reload]
                [approve-reject-form app-id reload]]]]
    (when (seq actions)
      [collapsible/component
       {:id "actions"
        :title (text :t.form/actions)
        :always (into [:div (into [:div#action-commands]
                                  actions)]
                      forms)}])))

(defn- render-resource [resource]
  ^{:key (:catalogue-item/id resource)}
  [:div.application-resource
   (localized (:catalogue-item/title resource))
   ;; Slight duplication with rems.catalogue/catalogue-item-more-info,
   ;; but the data has a different schema here (V2Resource vs. CatalogueItem)
   ;;
   ;; NB! localized falls back to the default language, so the fallback logic
   ;; here is subtly different
   (when-let [url (or (localized (:catalogue-item/infourl resource))
                      (urn-catalogue-item-link {:resid (:resource/ext-id resource)} {}))]
     [:<>
      (goog.string/unescapeEntities " &mdash; ")
      [:a {:href url :target :_blank}
       (text :t.catalogue/more-info) " " [external-link]]])])

(defn- applied-resources [application userid]
  (let [application-id (:application/id application)
        permissions (:application/permissions application)
        applicant? (= (:application/applicant application) userid)
        can-change-resources? (contains? permissions :application.command/change-resources)
        can-comment? (not applicant?)]
    [collapsible/component
     {:id "resources"
      :title (text :t.form/resources)
      :always [:div.form-items.form-group
               [flash-message/component :change-resources]
               (into [:div.application-resources]
                     (for [resource (:application/resources application)]
                       [render-resource resource]))]
      :footer [:div
               [:div.commands
                (when can-change-resources? [change-resources-action-button (:application/resources application)])]
               [:div#resource-action-forms
                [change-resources-form application can-comment? (partial reload! application-id)]]]}]))

(defn- render-application [{:keys [application edit-application attachment-success config userid]}]
  [:div.container-fluid.editor-content
   [document-title (str (text :t.applications/application) " " (format-application-id config application))]
   (text :t.applications/intro)
   [:div.row
    [:div.col-lg-4.order-lg-last
     [:div#float-actions.mb-3
      [flash-message/component :top]
      [disabled-items-warning application]
      [actions-form application]]]
    [:div.col-lg-8
     [application-state application config]
     [:div.mt-3 [applicants-info application]]
     [:div.mt-3 [applied-resources application userid]]
     [:div.my-3 [application-licenses application edit-application userid]]
     [:div.my-3 [application-fields application edit-application attachment-success]]]]])

;;;; Entrypoint

(defn application-page []
  (let [config @(rf/subscribe [:rems.config/config])
        application @(rf/subscribe [::application])
        edit-application @(rf/subscribe [::edit-application])
        attachment-success @(rf/subscribe [::attachment-success])
        userid (get-in @(rf/subscribe [:identity]) [:user :eppn])
        loading? (not application)]
    (if loading?
      [:div
       [document-title (text :t.applications/application)]
       [spinner/big]]
      [render-application {:application application
                           :edit-application edit-application
                           :attachment-success attachment-success
                           :config config
                           :userid userid}])))


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
                          :application {:application/id 42
                                        :application/applicant "developer"}
                          :accepted-licenses? true}])
   (example "member-info with name missing"
            [member-info {:element-id "info2"
                          :attributes {:eppn "developer"
                                       :mail "developer@uu.id"
                                       :organization "Testers"
                                       :address "Testikatu 1, 00100 Helsinki"}
                          :application {:application/id 42
                                        :application/applicant "developer"}}])
   (example "member-info"
            [member-info {:element-id "info3"
                          :attributes {:userid "alice"}
                          :application {:application/id 42
                                        :application/applicant "developer"}
                          :group? true
                          :can-remove? true}])
   (example "member-info"
            [member-info {:element-id "info4"
                          :attributes {:name "John Smith"
                                       :email "john.smith@invited.com"}
                          :application {:application/id 42
                                        :application/applicant "developer"}
                          :group? true}])

   (component-info applicants-info)
   (example "applicants-info"
            [applicants-info {:application/id 42
                              :application/applicant "developer"
                              :application/applicant-attributes {:eppn "developer"
                                                                 :mail "developer@uu.id"
                                                                 :commonName "Deve Loper"
                                                                 :organization "Testers"
                                                                 :address "Testikatu 1, 00100 Helsinki"}
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

   (example "link license"
            [:form
             [license-field {:license/id 1
                             :license/type :link
                             :license/title {:en "Link to license"}
                             :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}}]])
   (example "link license with validation error"
            [:form
             [license-field {:license/id 1
                             :license/type :link
                             :license/title {:en "Link to license"}
                             :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}
                             :validation {:type :t.form.validation/required}}]])
   (example "text license"
            [:form
             [license-field {:license/id 1
                             :license/type :text
                             :license/title {:en "A Text License"}
                             :license/text {:en lipsum-paragraphs}}]])
   (example "text license with validation error"
            [:form
             [license-field {:license/id 1
                             :license/type :text
                             :license/title {:en "A Text License"}
                             :license/text {:en lipsum-paragraphs}
                             :validation {:type :t.form.validation/required}}]])

   (component-info render-application)
   (example "application, partially filled"
            [render-application
             {:application {:application/id 17
                            :application/state :application.state/draft
                            :application/resources [{:catalogue-item/title {:en "An applied item"}}]
                            :application/form {:form/fields [{:field/id 1
                                                              :field/type :text
                                                              :field/title {:en "Field 1"}
                                                              :field/placeholder {:en "placeholder 1"}}
                                                             {:field/id 2
                                                              :field/type :label
                                                              :title "Please input your wishes below."}
                                                             {:field/id 3
                                                              :field/type :texta
                                                              :field/optional true
                                                              :field/title {:en "Field 2"}
                                                              :field/placeholder {:en "placeholder 2"}}
                                                             {:field/id 4
                                                              :field/type :unsupported
                                                              :field/title {:en "Field 3"}
                                                              :field/placeholder {:en "placeholder 3"}}
                                                             {:field/id 5
                                                              :field/type :date
                                                              :field/title {:en "Field 4"}}]}
                            :application/licenses [{:license/id 4
                                                    :license/type :text
                                                    :license/title {:en "A Text License"}
                                                    :license/text {:en lipsum}}
                                                   {:license/id 5
                                                    :license/type :link
                                                    :license/title {:en "Link to license"}
                                                    :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}}]}
              :edit-application {:field-values {1 "abc"}
                                 :show-diff {}
                                 :validation-errors nil
                                 :accepted-licenses #{5}}}])
   (example "application, applied"
            [render-application
             {:application {:application/id 17
                            :application/state :application.state/submitted
                            :application/resources [{:catalogue-item/title {:en "An applied item"}}]
                            :application/form {:form/fields [{:field/id 1
                                                              :field/type :text
                                                              :field/title {:en "Field 1"}
                                                              :field/placeholder {:en "placeholder 1"}}]}
                            :application/licenses [{:license/id 4
                                                    :license/type :text
                                                    :license/title {:en "A Text License"}
                                                    :license/text {:en lipsum}}]}
              :edit-application {:field-values {1 "abc"}
                                 :accepted-licenses #{4}}}])
   (example "application, approved"
            [render-application
             {:application {:application/id 17
                            :application/state :application.state/approved
                            :application/applicant-attributes {:eppn "eppn"
                                                               :mail "email@example.com"
                                                               :additional "additional field"}
                            :application/resources [{:catalogue-item/title {:en "An applied item"}}]
                            :application/form {:form/fields [{:field/id 1
                                                              :field/type :text
                                                              :field/title {:en "Field 1"}
                                                              :field/placeholder {:en "placeholder 1"}}]}
                            :application/licenses [{:license/id 4
                                                    :license/type :text
                                                    :license/title {:en "A Text License"}
                                                    :license/text {:en lipsum}}]}
              :edit-application {:field-values {1 "abc"}
                                 :accepted-licenses #{4}}}])

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
