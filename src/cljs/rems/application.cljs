(ns rems.application
  (:require [clojure.string :as str]
            [medley.core :refer [map-vals]]
            [re-frame.core :as rf]
            [rems.actions.accept-licenses :refer [accept-licenses-action-button]]
            [rems.actions.action :refer [action-button action-form-view action-comment action-collapse-id button-wrapper]]
            [rems.actions.add-licenses :refer [add-licenses-action-button add-licenses-form]]
            [rems.actions.add-member :refer [add-member-action-button add-member-form]]
            [rems.actions.approve-reject :refer [approve-reject-action-button approve-reject-form]]
            [rems.actions.change-resources :refer [change-resources-action-button change-resources-form]]
            [rems.actions.close :refer [close-action-button close-form]]
            [rems.actions.comment :refer [comment-action-button comment-form]]
            [rems.actions.decide :refer [decide-action-button decide-form]]
            [rems.actions.remark :refer [remark-action-button remark-form]]
            [rems.actions.invite-member :refer [invite-member-action-button invite-member-form]]
            [rems.actions.remove-member :refer [remove-member-action-button remove-member-form]]
            [rems.actions.request-comment :refer [request-comment-action-button request-comment-form]]
            [rems.actions.request-decision :refer [request-decision-action-button request-decision-form]]
            [rems.actions.return-action :refer [return-action-button return-form]]
            [rems.application-util :refer [accepted-licenses? form-fields-editable? get-applicant-name]]
            [rems.atoms :refer [external-link file-download flash-message info-field readonly-checkbox textarea document-title]]
            [rems.collapsible :as collapsible]
            [rems.common-util :refer [index-by]]
            [rems.fields :as fields]
            [rems.guide-utils :refer [lipsum lipsum-short lipsum-paragraphs]]
            [rems.phase :refer [phases]]
            [rems.spinner :as spinner]
            [rems.status-modal :as status-modal]
            [rems.text :refer [localize-decision localize-event localized localize-item localize-state localize-time text text-format get-localized-title]]
            [rems.util :refer [dispatch! fetch parse-int post!]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;;;; Helpers

(defn scroll-to-top! []
  (.setTimeout js/window #(.scrollTo js/window 0 0) 500)) ;; wait until faded out

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

(defn apply-for [items]
  (let [url (str "#/application?items=" (str/join "," (sort (map :id items))))]
    (dispatch! url)))

(defn navigate-to
  "Navigates to the application with the given id.

  `replace?` parameter can be given to replace history state instead of push."
  [id & [replace?]]
  (dispatch! (str "#/application/" id) replace?))

(defn- format-validation-error [type title]
  [:li (text-format type (localized title))])

(defn- format-validation-errors
  [application errors]
  (let [fields-by-id (->> (get-in application [:application/form :form/fields])
                          (index-by [:field/id]))]
    [:div (text :t.form.validation/errors)
     (into [:ul]
           (concat
            (for [{:keys [type field-id]} errors
                  :when field-id]
              (let [field (get fields-by-id field-id)]
                (format-validation-error type (:field/title field))))))]))


;;;; State

(rf/reg-sub ::application (fn [db _] (::application db)))
(rf/reg-sub ::edit-application (fn [db _] (::edit-application db)))

(rf/reg-event-fx
 ::enter-application-page
 (fn [{:keys [db]} [_ id]]
   {:db (dissoc db ::application ::edit-application)
    ::fetch-application id}))

(rf/reg-fx
 ::fetch-application
 (fn [id]
   (fetch (str "/api/applications/" id)
          {:handler #(rf/dispatch [::fetch-application-result %])})))

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
  (status-modal/common-pending-handler! description)
  (post! "/api/applications/save-draft"
         {:params {:application-id application-id
                   :field-values (field-values-to-api field-values)}
          :handler (partial status-modal/common-success-handler! #(rf/dispatch [::enter-application-page application-id]))
          :error-handler status-modal/common-error-handler!}))

(rf/reg-event-fx
 ::save-application
 (fn [{:keys [db]} [_ description]]
   (let [application (::application db)
         edit-application (::edit-application db)]
     (save-application! description
                        (:application/id application)
                        (:field-values edit-application)))
   {:db (assoc-in db [::edit-application :validation-errors] nil)}))

(defn- submit-application! [application description application-id field-values]
  ;; TODO: deduplicate with save-application!
  (status-modal/common-pending-handler! description)
  (post! "/api/applications/save-draft"
         {:params {:application-id application-id
                   :field-values (field-values-to-api field-values)}
          :handler (fn [response]
                     (if (:success response)
                       (post! "/api/applications/submit"
                              {:params {:application-id application-id}
                               :handler (fn [response]
                                          (if (:success response)
                                            (status-modal/set-success! {:on-close #(rf/dispatch [::enter-application-page application-id])})
                                            (do
                                              (status-modal/set-error! {:result response
                                                                        :error-content (format-validation-errors application (:errors response))})
                                              (rf/dispatch [::set-validation-errors (:errors response)]))))
                               :error-handler status-modal/common-error-handler!})
                       (status-modal/common-error-handler! response)))
          :error-handler status-modal/common-error-handler!}))

(rf/reg-event-fx
 ::submit-application
 (fn [{:keys [db]} [_ description]]
   (let [application (::application db)
         edit-application (::edit-application db)]
     (submit-application! application
                          description
                          (:application/id application)
                          (:field-values edit-application)))
   {:db (assoc-in db [::edit-application :validation-errors] nil)}))

(defn- save-attachment [{:keys [db]} [_ field-id file description]]
  (let [application-id (get-in db [::application :application/id])]
    (status-modal/common-pending-handler! description)
    (post! "/api/applications/add-attachment"
           {:url-params {:application-id application-id
                         :field-id field-id}
            :body file
            ;; force saving a draft when you upload an attachment.
            ;; this ensures that the attachment is not left
            ;; dangling (with no references to it)
            :handler (fn [response]
                       (if (:success response)
                         (do
                           ;; no race condition here: events are handled in a FIFO manner
                           (rf/dispatch [::set-field-value field-id (str (:id response))])
                           (rf/dispatch [::save-application (text :t.form/upload)]))
                         (status-modal/common-error-handler! response)))
            :error-handler status-modal/common-error-handler!})
    {}))

(rf/reg-event-fx ::save-attachment save-attachment)

;;;; UI components

(defn- pdf-button [app-id]
  (when app-id
    [:a.btn.btn-secondary
     {:href (str "/api/applications/" app-id "/pdf")
      :target :_blank}
     "PDF " [external-link]]))

(rf/reg-event-db
 ::set-field-value
 (fn [db [_ field-id value]]
   (assoc-in db [::edit-application :field-values field-id] value)))

(rf/reg-event-db
 ::toggle-diff
 (fn [db [_ field-id]]
   (update-in db [::edit-application :show-diff field-id] not)))

(defn- link-license [opts]
  (let [title (localized (:license/title opts))
        link (localized (:license/link opts))]
    [:div.license
     [:a.license-title {:href link :target :_blank}
      title " " [external-link]]]))

(defn- text-license [opts]
  (let [id (:license/id opts)
        collapse-id (str "collapse" id)
        title (localized (:license/title opts))
        text (localized (:license/text opts))]
    [:div.license
     [:div.license-panel
      [:span.license-title
       [:a.license-header.collapsed {:data-toggle "collapse"
                                     :href (str "#" collapse-id)
                                     :aria-expanded "false"
                                     :aria-controls collapse-id}
        title]]
      [:div.collapse {:id collapse-id}
       [:div.license-block (str/trim (str text))]]]]))

(defn- attachment-license [opts]
  (let [title (localized (:license/title opts))
        link (str "/api/licenses/attachments/" (localized (:license/attachment-id opts)))]
    [:div.license
     [:a.license-title {:href link :target :_blank}
      title " " [file-download]]]))

(defn license-field [f]
  (case (:license/type f)
    :link [link-license f]
    :text [text-license f]
    :attachment [attachment-license f]
    [fields/unsupported-field f]))

(defn- save-button []
  [button-wrapper {:id "save"
                   :text (text :t.form/save)
                   :on-click #(rf/dispatch [::save-application (text :t.form/save)])}])

(defn- submit-button []
  [button-wrapper {:id "submit"
                   :text (text :t.form/submit)
                   :class :btn-primary
                   :on-click #(rf/dispatch [::submit-application (text :t.form/submit)])}])

(defn- application-fields [application edit-application]
  (let [field-values (:field-values edit-application)
        show-diff (:show-diff edit-application)
        field-validations (index-by [:field-id] (:validation-errors edit-application))
        attachments (index-by [:attachment/id] (:application/attachments application))
        form-fields-editable? (form-fields-editable? application)
        readonly? (not form-fields-editable?)]
    [collapsible/component
     {:id "form"
      :title (text :t.form/application)
      :always
      [:div
       (into [:div]
             (for [fld (get-in application [:application/form :form/fields])]
               [fields/field (assoc fld
                                    :on-change #(rf/dispatch [::set-field-value (:field/id fld) %])
                                    :on-set-attachment #(rf/dispatch [::save-attachment (:field/id fld) %1 %2])
                                    :on-remove-attachment #(rf/dispatch [::set-field-value (:field/id fld) ""])
                                    :on-toggle-diff #(rf/dispatch [::toggle-diff (:field/id fld)])
                                    :field/value (get field-values (:field/id fld))
                                    :field/attachment (when (= :attachment (:field/type fld))
                                                        (get attachments (parse-int (:field/value fld))))
                                    :field/previous-attachment (when (= :attachment (:field/type fld))
                                                                 (when-let [prev (:field/previous-value fld)]
                                                                   (get attachments (parse-int prev))))
                                    :diff (get show-diff (:field/id fld))
                                    :validation (field-validations (:field/id fld))
                                    :readonly readonly?
                                    :app-id (:application/id application))]))]}]))

(defn- application-licenses [application edit-application userid]
  (when-let [licenses (not-empty (:application/licenses application))]
    (let [application-id (:application/id application)
          accepted-licenses (get (:application/accepted-licenses application) userid)
          possible-commands (:application/permissions application)
          form-fields-editable? (form-fields-editable? application)
          readonly? (not form-fields-editable?)]
      [collapsible/component
       {:id "form"
        :title (text :t.form/licenses)
        :always
        [:div
         [:p (text :t.form/must-accept-licenses)]
         (into [:div#licenses]
               (for [license licenses]
                 [license-field (assoc license
                                       :accepted (contains? accepted-licenses (:license/id license))
                                       :readonly readonly?)]))
         (if (accepted-licenses? application userid)
           (text :t.form/has-accepted-licenses)
           (when (contains? possible-commands :application.command/accept-licenses)
             [:div.commands
              ;; TODO consider saving the form first so that no data is lost for the applicant
              [accept-licenses-action-button application-id (mapv :license/id licenses) #(reload! application-id)]]))]}])))


(defn- format-event [event]
  {:userid (:event/actor event)
   :event (localize-event (:event/type event))
   :comment (if (= :application.event/decided (:event/type event))
              (str (localize-decision (:application/decision event)) ": " (:application/comment event))
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

(defn- application-id-value [config application]
  (let [id-column (get config :application-id-column :id)]
    (case id-column
      :external-id (:application/external-id application)
      :id (:application/id application)
      (:application/id application))))

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
                      [:span#application-id (application-id-value config application)]
                      {:inline? true}]
                     [info-field
                      (text :t.actions/description)
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
        sanitized-user-id (-> (or user-id (:email attributes) "") ;; use email for invited members
                              str/lower-case
                              (str/replace #"[^a-z]" ""))
        other-attributes (dissoc attributes :commonName :name :eppn :userid :mail :email)
        user-actions-id (str element-id "-" sanitized-user-id "-actions")]
    [collapsible/minimal
     {:id (str element-id "-" sanitized-user-id "-info")
      :class (when group? "group")
      :always
      [:div
       [:h3 (cond (= (:application/applicant application) user-id) (text :t.applicant-info/applicant)
                  (:userid attributes) (text :t.applicant-info/member)
                  :else (text :t.applicant-info/invited-member))]
       (when-let [applicant-name (get-applicant-name application)]
         [info-field (text :t.applicant-info/name) applicant-name {:inline? true}])
       (when user-id
         [info-field (text :t.applicant-info/username) user-id {:inline? true}])
       (when-let [mail (or (:mail attributes) (:email attributes))]
         [info-field (text :t.applicant-info/email) mail {:inline? true}])
       (when-not (nil? accepted-licenses?)
         [info-field (text :t.form/accepted-licenses) [readonly-checkbox accepted-licenses?] {:inline? true}])]
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
  [application]
  (let [id "applicants-info"
        application-id (:application/id application)
        applicant (merge {:userid (:application/applicant application)}
                         (:application/applicant-attributes application))
        members (:application/members application)
        invited-members (:application/invited-members application)
        possible-commands (:application/permissions application)
        can-add? (contains? possible-commands :application.command/add-member)
        can-remove? (contains? possible-commands :application.command/remove-member)
        can-invite? (contains? possible-commands :application.command/invite-member)
        can-uninvite? (contains? possible-commands :application.command/uninvite-member)]
    [collapsible/component
     {:id id
      :title (text :t.applicant-info/applicants)
      :always
      (into [:div
             [member-info {:element-id id
                           :attributes applicant
                           :application application
                           :group? (or (seq members)
                                       (seq invited-members))
                           :can-remove? false
                           :accepted-licenses? (accepted-licenses? application (:userid applicant))}]]
            (concat
             (for [member members]
               [member-info {:element-id id
                             :attributes member
                             :application application
                             :group? true
                             :can-remove? can-remove?
                             :accepted-licenses? (accepted-licenses? application (:userid member))}])
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

(defn- action-buttons [application]
  (let [commands-and-actions [:application.command/save-draft [save-button]
                              :application.command/submit [submit-button]
                              :application.command/return [return-action-button]
                              :application.command/request-decision [request-decision-action-button]
                              :application.command/decide [decide-action-button]
                              :application.command/request-comment [request-comment-action-button]
                              :application.command/comment [comment-action-button]
                              :application.command/remark [remark-action-button]
                              :application.command/add-licenses [add-licenses-action-button]
                              :application.command/approve [approve-reject-action-button]
                              :application.command/reject [approve-reject-action-button]
                              :application.command/close [close-action-button]]]
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
                [request-comment-form app-id reload]
                [request-decision-form app-id reload]
                [comment-form app-id reload]
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

(defn- applied-resources [application userid]
  (let [application-id (:application/id application)
        possible-commands (:application/permissions application)
        applicant? (= (:application/applicant application) userid)
        can-bundle-all? (not applicant?)
        can-change? (contains? possible-commands :application.command/change-resources)
        can-comment? (not applicant?)]
    [collapsible/component
     {:id "resources"
      :title (text :t.form/resources)
      :always [:div.form-items.form-group
               (into [:div.application-resources]
                     (for [resource (:application/resources application)]
                       ^{:key (:catalogue-item/id resource)}
                       [:div.application-resource (localized (:catalogue-item/title resource))]))]
      :footer [:div
               [:div.commands
                (when can-change? [change-resources-action-button (:application/resources application)])]
               [:div#resource-action-forms
                [change-resources-form application can-bundle-all? can-comment? (partial reload! application-id)]]]}]))

(defn- render-application [application edit-application config userid]
  (let [messages (remove nil?
                         [(disabled-items-warning application) ; NB: eval this here so we get nil or a warning
                          (when-let [errors (:validation-errors edit-application)]
                            [flash-message
                             {:status :danger
                              :contents [format-validation-errors application errors]}])])]
    [:div
     [:div {:class "float-right"} [pdf-button (:application/id application)]]
     [document-title (str (text :t.applications/application) " " (application-id-value config application))]
     (text :t.applications/intro)
     (into [:div] messages)
     [application-state application config]
     [:div.mt-3 [applicants-info application]]
     [:div.mt-3 [applied-resources application userid]]
     [:div.my-3 [application-fields application edit-application]]
     [:div.my-3 [application-licenses application edit-application userid]]
     [:div.mb-3 [actions-form application]]]))

;;;; Entrypoint

(defn application-page []
  (let [config @(rf/subscribe [:rems.config/config])
        application @(rf/subscribe [::application])
        edit-application @(rf/subscribe [::edit-application])
        userid (get-in @(rf/subscribe [:identity]) [:user :eppn])
        loading? (not application)]
    (if loading?
      [:div
       [document-title (text :t.applications/application)]
       [spinner/big]]
      [render-application application edit-application config userid])))


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
                                       :catalogue-item/title {:default "Catalogue item 1"}}
                                      {:catalogue-item/enabled false :catalogue-item/archived false
                                       :catalogue-item/title {:default "Catalogue item 2"}}
                                      {:catalogue-item/enabled true :catalogue-item/archived false
                                       :catalogue-item/title {:default "Catalogue item 3"}}]}])

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
             {:application/id 17
              :application/state :application.state/draft
              :application/resources [{:catalogue-item/title {:en "An applied item"}}]
              :application/form {:form/fields [{:field/id 1
                                                :field/type :text
                                                :field/title {:en "Field 1"}
                                                :field/placeholder {:en "prompt 1"}}
                                               {:field/id 2
                                                :field/type :label
                                                :title "Please input your wishes below."}
                                               {:field/id 3
                                                :field/type :texta
                                                :field/optional true
                                                :field/title {:en "Field 2"}
                                                :field/placeholder {:en "prompt 2"}}
                                               {:field/id 4
                                                :field/type :unsupported
                                                :field/title {:en "Field 3"}
                                                :field/placeholder {:en "prompt 3"}}
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
             {:field-values {1 "abc"}
              :show-diff {}
              :validation-errors nil
              :accepted-licenses #{5}}])
   (example "application, applied"
            [render-application
             {:application/id 17
              :application/state :application.state/submitted
              :application/resources [{:catalogue-item/title {:en "An applied item"}}]
              :application/form {:form/fields [{:field/id 1
                                                :field/type :text
                                                :field/title {:en "Field 1"}
                                                :field/placeholder {:en "prompt 1"}}]}
              :application/licenses [{:license/id 4
                                      :license/type :text
                                      :license/title {:en "A Text License"}
                                      :license/text {:en lipsum}}]}
             {:field-values {1 "abc"}
              :accepted-licenses #{4}}])
   (example "application, approved"
            [render-application
             {:application/id 17
              :application/state :application.state/approved
              :application/applicant-attributes {:eppn "eppn"
                                                 :mail "email@example.com"
                                                 :additional "additional field"}
              :application/resources [{:catalogue-item/title {:en "An applied item"}}]
              :application/form {:form/fields [{:field/id 1
                                                :field/type :text
                                                :field/title {:en "Field 1"}
                                                :field/placeholder {:en "prompt 1"}}]}
              :application/licenses [{:license/id 4
                                      :license/type :text
                                      :license/title {:en "A Text License"}
                                      :license/text {:en lipsum}}]}
             {:field-values {1 "abc"}
              :accepted-licenses #{4}}])])
