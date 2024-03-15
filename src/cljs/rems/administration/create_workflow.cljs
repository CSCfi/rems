(ns rems.administration.create-workflow
  (:require [better-cond.core :as b]
            [medley.core :refer [indexed remove-nth update-existing]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [localized-text-field organization-field radio-button-group text-field]]
            [rems.administration.items :as items]
            [rems.atoms :as atoms :refer [enrich-user document-title]]
            [rems.collapsible :as collapsible]
            [rems.config :as config]
            [rems.common.application-util :as application-util]
            [rems.common.util :refer [andstr build-index conj-vec keep-keys not-blank replace-key]]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [localized localize-command localize-role localize-state text text-format text-format-map]]
            [rems.util :refer [navigate! post! put! trim-when-string]]))

(rf/reg-event-fx ::enter-page
                 (fn [{:keys [db]} [_ workflow-id]]
                   {:db (assoc db
                               ::workflow-id workflow-id
                               ::actors nil
                               ::commands nil
                               ::editing? (some? workflow-id)
                               ::form {:type :workflow/default})
                    :dispatch-n [[::actors]
                                 [::forms {:disabled true :archived true}]
                                 [::licenses]
                                 [::commands]
                                 (when workflow-id [::workflow])]}))

(rf/reg-sub ::workflow-id (fn [db _] (::workflow-id db)))
(rf/reg-sub ::editing? (fn [db _] (::editing? db)))
(rf/reg-sub ::form (fn [db _] (::form db)))
(rf/reg-sub ::get-field :<- [::form] (fn [form [_ key-path]] (get-in form key-path)))

(rf/reg-event-db ::set-form-field (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-event-db ::fetch-workflow-success
                 (fn [db [_ {:keys [title organization workflow]}]]
                   (update db ::form merge {:title title
                                            :organization organization
                                            :type (:type workflow)
                                            :forms (mapv #(select-keys % [:form/id]) (get workflow :forms))
                                            :handlers (get workflow :handlers)
                                            :licenses (->> (:licenses workflow)
                                                           (mapv #(replace-key % :license/id :id)))
                                            :disable-commands (get workflow :disable-commands)
                                            :voting (get workflow :voting)
                                            :anonymize-handling (:anonymize-handling workflow)
                                            :processing-states (get workflow :processing-states)})))

(fetcher/reg-fetcher ::workflow "/api/workflows/:id" {:path-params (fn [db] {:id (::workflow-id db)})
                                                      :on-success #(rf/dispatch [::fetch-workflow-success %])})
(fetcher/reg-fetcher ::actors "/api/workflows/actors" {:result (partial mapv enrich-user)})
(fetcher/reg-fetcher ::forms "/api/forms")
(fetcher/reg-fetcher ::licenses "/api/licenses")
(fetcher/reg-fetcher ::commands "/api/applications/commands")

(defn- at-least-one-localization [value]
  (->> @(rf/subscribe [:languages])
       (some (fn [lang]
               (not-blank (get value lang))))))

(defn- valid-create-request? [request]
  (and
   ;; required
   (contains? application-util/workflow-types (:type request))
   (seq (keep not-blank (:handlers request)))
   (not-blank (get-in request [:organization :organization/id]))
   (not-blank (:title request))
   ;; optional (validated values)
   (every? :command (:disable-commands request))
   (every? (comp not-blank :processing-state/value) (:processing-states request))
   (every? (comp at-least-one-localization :processing-state/title) (:processing-states request))))

(defn build-create-request [form]
  (let [request (merge
                 {:anonymize-handling (:anonymize-handling form)
                  :disable-commands (->> (:disable-commands form)
                                         (mapv #(update-existing % :when/state vec))
                                         (mapv #(update-existing % :when/role vec)))
                  :forms (mapv #(select-keys % [:form/id]) (:forms form))
                  :handlers (mapv :userid (:handlers form))
                  :licenses (vec (keep-keys {:id :license/id} (:licenses form)))
                  :organization {:organization/id (get-in form [:organization :organization/id])}
                  :processing-states (:processing-states form)
                  :title (trim-when-string (:title form))
                  :type (:type form)
                  :voting (:voting form)})]
    (when (valid-create-request? request)
      request)))

(defn- valid-edit-request? [request]
  (and
   ;; required
   (number? (:id request))
   (seq (keep not-blank (:handlers request)))
   (not-blank (get-in request [:organization :organization/id]))
   (not-blank (:title request))
   ;; optional (validated values)
   (every? :command (:disable-commands request))
   (every? (comp not-blank :processing-state/value) (:processing-states request))
   (every? (comp at-least-one-localization :processing-state/title) (:processing-states request))))

(defn build-edit-request [id form]
  (let [request {:anonymize-handling (:anonymize-handling form)
                 :disable-commands (->> (:disable-commands form)
                                        (mapv #(update-existing % :when/state vec))
                                        (mapv #(update-existing % :when/role vec)))
                 :handlers (mapv :userid (:handlers form))
                 :id id
                 :organization {:organization/id (get-in form [:organization :organization/id])}
                 :processing-states (:processing-states form)
                 :title (:title form)
                 :voting (:voting form)}]
    (when (valid-edit-request? request)
      request)))

(rf/reg-event-fx ::create-workflow
                 (fn [_ [_ request]]
                   (let [description [text :t.administration/create-workflow]]
                     (post! "/api/workflows/create"
                            {:params request
                             :handler (flash-message/default-success-handler
                                       :top description #(navigate! (str "/administration/workflows/" (:id %))))
                             :error-handler (flash-message/default-error-handler :top description)})
                     {:dispatch [:rems.spa/user-triggered-navigation]})))

(rf/reg-event-fx ::edit-workflow
                 (fn [_ [_ request]]
                   (let [description [text :t.administration/edit-workflow]]
                     (put! "/api/workflows/edit"
                           {:params request
                            :handler (flash-message/default-success-handler
                                      :top description #(navigate! (str "/administration/workflows/" (:id request))))
                            :error-handler (flash-message/default-error-handler :top description)})
                     {:dispatch [:rems.spa/user-triggered-navigation]})))

;;;; UI

(def ^:private context
  {:get-form ::form
   :get-form-field ::get-field
   :update-form ::set-form-field})

(defn- workflow-organization []
  [organization-field context {:keys [:organization]}])

(defn- workflow-title []
  [text-field context {:keys [:title]
                       :label (text :t.create-workflow/title)}])

(rf/reg-sub ::workflow-type (fn [db _] (get-in db [::form :type])))

(defn- workflow-type []
  (let [id "workflow-type"]
    [:<>
     [:div.form-group.field
      [:label.administration-field-label {:for id}
       (text :t.administration/workflow-type)]
      [radio-button-group context
       {:id id
        :keys [:type]
        :readonly @(rf/subscribe [::editing?])
        :orientation :horizontal
        :options (concat
                  [{:value :workflow/default
                    :label (text :t.create-workflow/default-workflow)}
                   {:value :workflow/decider
                    :label (text :t.create-workflow/decider-workflow)}]
                  (when (config/dev-environment?)
                    [{:value :workflow/master
                      :label (text :t.create-workflow/master-workflow)}]))}]
      [:p.workflow-type-description
       (case @(rf/subscribe [::workflow-type])
         :workflow/default (text :t.create-workflow/default-workflow-description)
         :workflow/decider (text :t.create-workflow/decider-workflow-description)
         :workflow/master (text :t.create-workflow/master-workflow-description))]]]))

(rf/reg-sub ::workflow-handlers (fn [db _] (get-in db [::form :handlers])))
(rf/reg-event-db ::set-handlers (fn [db [_ handlers]] (assoc-in db [::form :handlers] (sort-by :userid handlers))))

;; TODO: Eventually filter handlers by the selected organization when
;;   we are sure that all the handlers have the organization information?
(defn- workflow-handlers []
  (let [id "handlers-dropdown"]
    [:div.form-group.field
     [:label.administration-field-label {:for id}
      (text :t.create-workflow/handlers)]

     (b/cond
       @(rf/subscribe [::actors :fetching?])
       [spinner/big]

       :let [all-handlers @(rf/subscribe [::actors])
             selected-handlers (set (map :userid @(rf/subscribe [::workflow-handlers])))]

       [dropdown/dropdown
        {:id id
         :items all-handlers
         :item-key :userid
         :item-label :display
         :item-selected? #(contains? selected-handlers (:userid %))
         :multi? true
         :on-change #(rf/dispatch [::set-handlers %])}])]))

(rf/reg-sub ::workflow-forms (fn [db _] (get-in db [::form :forms])))
(rf/reg-event-db ::set-forms (fn [db [_ form-ids]] (assoc-in db [::form :forms] form-ids)))

(defn- render-readonly-forms [{:keys [all-forms id selected-forms]}]
  (let [forms-by-id (build-index {:keys [:form/id]} all-forms)
        forms (for [form-id selected-forms
                    :let [form (get forms-by-id form-id)
                          uri (str "/administration/forms/" form-id)
                          title (:form/internal-name form)]]
                [atoms/link {} uri title])]
    [fields/readonly-field-raw {:id id
                                :value (if (seq forms)
                                         (into [:<>] (interpose ", ") forms)
                                         (text :t.administration/no-forms))}]))

(defn- localize-org-short [x]
  (when-let [org-short (get-in x [:organization :organization/short-name])]
    (text-format :t.label/default
                 (text :t.administration/org)
                 (localized org-short))))

(defn- workflow-forms []
  (let [id "workflow-forms"
        editing? @(rf/subscribe [::editing?])]
    [:div.form-group.field
     [:label.administration-field-label {:for id}
      (cond->> (text :t.administration/forms)
        (not editing?) (text-format :t.label/optional))]

     (b/cond
       @(rf/subscribe [::forms :fetching?])
       [spinner/big]

       :let [all-forms @(rf/subscribe [::forms])
             selected-forms (map :form/id @(rf/subscribe [::workflow-forms]))]
       editing?
       [render-readonly-forms {:all-forms all-forms
                               :id id
                               :selected-forms selected-forms}]
       ;; else creating
       [dropdown/dropdown
        {:id id
         :items (->> all-forms
                     (filter :enabled)
                     (remove :archived)
                     (mapv #(assoc % ::label (text-format :t.label/parens (:form/internal-name %) (localize-org-short %)))))
         :item-key :form/id
         :item-label ::label
         :item-selected? #(contains? (set selected-forms) (:form/id %))
         :multi? true ; TODO support ordering multiple forms
         :on-change #(rf/dispatch [::set-forms %])}])]))

(rf/reg-sub ::workflow-licenses (fn [db _] (get-in db [::form :licenses])))
(rf/reg-event-db ::set-licenses (fn [db [_ licenses]] (assoc-in db [::form :licenses] licenses)))

(defn- render-readonly-licenses [id licenses]
  (let [licenses (for [license licenses
                       :let [uri (str "/administration/licenses/" (:license/id license))
                             title (:title (localized (:localizations license)))]]
                   [atoms/link {} uri title])]
    [fields/readonly-field-raw {:id id
                                :value (if (seq licenses)
                                         (into [:<>] (interpose ", ") licenses)
                                         (text :t.administration/no-licenses))}]))

(defn- workflow-licenses []
  (let [id "workflow-licenses"
        editing? @(rf/subscribe [::editing?])]
    [:div.form-group.field
     [:label.administration-field-label {:for id}
      (cond->> (text :t.create-resource/licenses-selection)
        (not editing?) (text-format :t.label/optional))]

     (b/cond
       @(rf/subscribe [::licenses :fetching?])
       [spinner/big]

       :let [selected-licenses @(rf/subscribe [::workflow-licenses])]

       editing?
       [render-readonly-licenses id selected-licenses]

       ;; else creating
       :let [selected-ids (set (map :id selected-licenses))]
       [dropdown/dropdown
        {:id id
         :items (->> @(rf/subscribe [::licenses])
                     (mapv #(assoc % ::label (text-format :t.label/parens
                                                          (:title (localized (:localizations %)))
                                                          (localize-org-short %)))))
         :item-key :id
         :item-label ::label
         :item-selected? #(contains? selected-ids (:id %))
         :multi? true
         :on-change #(rf/dispatch [::set-licenses (sort-by :id %)])}])]))

(rf/reg-sub ::workflow-anonymize-handling (fn [db _] (get-in db [::form :anonymize-handling])))
(rf/reg-event-db ::toggle-anonymize-handling (fn [db [_]] (update-in db [::form :anonymize-handling] not)))

(defn- workflow-anonymize-handling []
  (let [id "anonymize-handling"
        on-change #(rf/dispatch [::toggle-anonymize-handling])
        value (true? @(rf/subscribe [::workflow-anonymize-handling]))
        editing? @(rf/subscribe [::editing?])]
    [:div.form-group.field
     [:div.form-group.field.form-check.form-check-inline.pointer.toggle-anonymize-handling
      [atoms/checkbox {:id id
                       :class :form-check-input
                       :value value
                       :on-change on-change}]
      [:label.form-check-label {:for id :on-click on-change}
       (cond->> (text :t.administration/anonymize-handling)
         (not editing?) (text-format :t.label/optional))]]
     [:p (text :t.create-workflow/anonymize-handling-explanation)]]))

(rf/reg-sub ::voting (fn [db _] (get-in db [::form :voting])))
(rf/reg-event-db ::set-voting (fn [db [_ voting]] (assoc-in db [::form :voting] voting)))

(defn- select-voting-type-field [{:keys [label on-change value]}]
  [:div.form-group.select-voting-type
   [:label.administration-field-label {:for "voting-type"} label]
   [dropdown/dropdown
    {:id "voting-type"
     :items [{:type nil :label (text :t.administration/no-voting)}
             {:type :handlers-vote :label (text :t.administration/handlers-vote)}
             {:type :reviewers-vote :label (text :t.administration/reviewers-vote)}]
     :item-label :label
     :item-selected? #(= value (:type %))
     :on-change (comp on-change :type)}]])

(defn- workflow-voting []
  (let [voting @(rf/subscribe [::voting])]
    [:div.fields.voting
     [select-voting-type-field {:label (text :t.administration/voting)
                                :value (:type voting)
                                :on-change #(rf/dispatch [::set-voting (assoc voting :type %)])}]]))

(rf/reg-sub ::disable-commands (fn [db _] (get-in db [::form :disable-commands])))
(rf/reg-event-db ::new-disable-command (fn [db _] (update-in db [::form :disable-commands] conj-vec {})))
(rf/reg-event-db ::remove-disable-command (fn [db [_ index]] (update-in db [::form :disable-commands] #(vec (remove-nth index %)))))

(defn- select-command [{:keys [commands
                               index
                               on-change
                               value]}]
  (let [id (str "disable-commands-" index "-select-command")
        technical-commands #{:application.command/assign-external-id
                             :application.command/create
                             :application.command/send-expiration-notifications}]
    [:div.form-group.select-command
     [:label.administration-field-label {:for id}
      (text :t.administration/disabled-command)]
     [dropdown/dropdown
      {:id id
       :items (->> (sort commands)
                   (remove technical-commands)
                   (mapv #(do {::value %
                               ::label (text-format :t.label/parens (localize-command %) (name %))})))
       :item-label ::label
       :item-selected? #(= value (::value %))
       :on-change (comp on-change ::value)}]]))

(defn- select-application-states [{:keys [index
                                          on-change
                                          value]}]
  (let [id (str "disable-commands-" index "-select-application-states")]
    [:div.form-group.select-application-states
     [:label.administration-field-label {:for id}
      (text :t.administration/application-state)]
     [dropdown/dropdown
      {:id id
       :items (->> application-util/states
                   (mapv #(do {::value %
                               ::label (text-format :t.label/parens (localize-state %) (name %))})))
       :item-label ::label
       :item-selected? #(contains? (set value) (::value %))
       :placeholder (text :t.dropdown/placeholder-any-selection)
       :multi? true
       :on-change (comp on-change (partial mapv ::value))}]]))

(defn- select-user-roles [{:keys [index
                                  on-change
                                  value]}]
  (let [id (str "disable-commands-" index "-select-user-roles")
        applicant-roles [:applicant :member]
        expert-roles [:handler :reviewer :decider :past-reviewer :past-decider]
        technical-roles [:expirer :reporter]
        localize-dropdown-role #(if (some #{%} technical-roles)
                                  (text :t.roles/technical-role)
                                  (localize-role %))]
    [:div.form-group.select-application-states
     [:label.administration-field-label {:for id}
      (text :t.administration/user-role)]
     [dropdown/dropdown
      {:id id
       :items (->> (concat applicant-roles expert-roles technical-roles)
                   (mapv #(do {::value %
                               ::label (text-format :t.label/parens (localize-dropdown-role %) (name %))})))
       :item-label ::label
       :item-selected? #(contains? (set value) (::value %))
       :placeholder (text :t.dropdown/placeholder-any-selection)
       :multi? true
       :on-change (comp on-change (partial mapv ::value))}]]))

(defn- workflow-disable-commands []
  (let [id "disable-commands"]
    [:<>
     [:div.form-group {:id id}
      (into [:div.fields.disable-commands]
            (for [[index value] (indexed @(rf/subscribe [::disable-commands]))]
              [:div.form-field.field
               [:div.form-field-header
                [:h4 (text :t.create-workflow/rule)]
                [:div.form-field-controls
                 [items/remove-button (fn []
                                        (rf/dispatch [::remove-disable-command index])
                                        (rf/dispatch [:rems.focus/scroll-into-view (str "#" id) {:block :end}]))]]]
               [select-command {:commands @(rf/subscribe [::commands])
                                :index index
                                :on-change #(rf/dispatch [::set-form-field [:disable-commands index :command] %])
                                :value (:command value)}]
               [:div.row
                [:div.col-md
                 [select-application-states {:index index
                                             :on-change #(rf/dispatch [::set-form-field [:disable-commands index :when/state] (vec %)])
                                             :value (:when/state value)}]]
                [:div.col-md
                 [select-user-roles {:index index
                                     :on-change #(rf/dispatch [::set-form-field [:disable-commands index :when/role] (vec %)])
                                     :value (:when/role value)}]]]]))
      [:div.dashed-group.text-center
       [:a.new-rule {:href "#"
                     :on-click (fn [event]
                                 (.preventDefault event)
                                 (rf/dispatch [::new-disable-command])
                                 (rf/dispatch [:rems.focus/scroll-into-view (str "#" id) {:block :end}]))}
        (text :t.create-workflow/create-new-rule)]]]]))

(rf/reg-sub ::processing-states (fn [db _] (get-in db [::form :processing-states])))
(rf/reg-event-db ::new-processing-state (fn [db _] (update-in db [::form :processing-states] conj-vec {})))
(rf/reg-event-db ::remove-processing-state (fn [db [_ index]] (update-in db [::form :processing-states] #(vec (remove-nth index %)))))

(defn- get-processing-state-title [{title :processing-state/title}]
  (or (not-blank (get title @(rf/subscribe [:language])))
      (text :t.administration/processing-state)))

(defn- workflow-processing-states []
  (let [id "processing-states"]
    [:<>
     [:div.form-group {:id id}
      (into [:div.fields.processing-states]
            (for [[index value] (indexed @(rf/subscribe [::processing-states]))]
              [:div.form-field.field
               [:div.form-field-header
                [:h4 (get-processing-state-title value)]
                [:div.form-field-controls
                 [items/remove-button (fn []
                                        (rf/dispatch [::remove-processing-state index])
                                        (rf/dispatch [:rems.focus/scroll-into-view (str "#" id) {:block :end}]))]]]
               [text-field context {:keys [:processing-states index :processing-state/value]
                                    :label (text :t.administration/technical-value)}]
               [localized-text-field context {:keys [:processing-states index :processing-state/title]
                                              :label (text :t.administration/title)}]]))
      [:div.dashed-group.text-center
       [:a.new-rule {:href "#"
                     :on-click (fn [event]
                                 (.preventDefault event)
                                 (rf/dispatch [::new-processing-state])
                                 (rf/dispatch [:rems.focus/scroll-into-view (str "#" id) {:block :end}]))}
        (text :t.create-workflow/create-new-processing-state)]]]]))

;;;; page component

(defn- get-workflow-title [form language]
  (b/when-some [organization-short (not-blank (get-in form [:organization :organization/short-name language]))
                title (not-blank (:title form))]
    (text-format :t.label/default organization-short title)))

(defn- common-fields []
  [collapsible/component
   {:id "workflow-common-fields"
    :title (or (get-workflow-title @(rf/subscribe [::form])
                                   @(rf/subscribe [:language]))
               (text :t.administration/workflow))
    :always (if @(rf/subscribe [::workflow :fetching?])
              [spinner/big]
              [:div.spaced-vertically-6
               [:div.fields
                [workflow-organization]
                [workflow-title]
                [workflow-type]
                [workflow-anonymize-handling]]
               [:div.fields
                [workflow-handlers]
                [workflow-forms]
                [workflow-licenses]]])}])

(defn- voting-fields []
  (when (:enable-voting @(rf/subscribe [:rems.config/config]))
    (let [loading? @(rf/subscribe [::workflow :fetching?])]
      [collapsible/component
       {:id "workflow-voting-fields"
        :title (text :t.administration/voting)
        :always (if loading?
                  [spinner/big]
                  [:div.spaced-vertically-4
                   [:p (text :t.create-workflow/voting-explanation)]
                   [workflow-voting]])}])))

(defn- disable-commands-fields []
  (let [loading? (or @(rf/subscribe [::workflow :fetching?])
                     @(rf/subscribe [::commands :fetching?]))]
    [collapsible/component
     {:id "workflow-disable-commands-fields"
      :title (text :t.create-workflow/disable-commands)
      :bottom-less-button? false
      :always (if loading?
                [spinner/big]
                [:div.spaced-vertically-4
                 [:p (text :t.create-workflow/disable-commands-explanation)]
                 [workflow-disable-commands]])}]))

(defn- processing-states-fields []
  (let [config @(rf/subscribe [:rems.config/config])
        loading? @(rf/subscribe [::workflow :fetching?])]
    (when (:enable-processing-states config)
      [collapsible/component
       {:id "workflow-processing-states-fields"
        :title (text :t.administration/processing-states)
        :bottom-less-button? false
        :always (if loading?
                  [spinner/big]
                  [:div.spaced-vertically-4
                   [:p (text-format-map :t.create-workflow/processing-states-explanation
                                        {:command (text :t.commands/change-processing-state)})]
                   [workflow-processing-states]])}])))

(defn- save-workflow-action []
  (b/cond
    :let [loading? @(rf/subscribe [::workflow :fetching?])
          editing? @(rf/subscribe [::editing?])
          form @(rf/subscribe [::form])]

    loading?
    (atoms/save-action {:id :save
                        :disabled true})

    editing?
    (let [id @(rf/subscribe [::workflow-id])
          request (build-edit-request id form)]
      (atoms/save-action {:id :save
                          :disabled (not request)
                          :on-click #(rf/dispatch [::edit-workflow request])}))

    (let [request (build-create-request form)]
      (atoms/save-action {:id :save
                          :disabled (not request)
                          :on-click #(rf/dispatch [::create-workflow request])}))))

(defn- cancel-action []
  (let [id @(rf/subscribe [::workflow-id])]
    (atoms/cancel-action {:url (str "/administration/workflows" (andstr "/" id))})))

(defn create-workflow-page []
  [:div
   [administration/navigator]
   [document-title (if @(rf/subscribe [::editing?])
                     (text :t.administration/edit-workflow)
                     (text :t.administration/create-workflow))]
   [flash-message/component :top]

   [:div#workflow-editor.spaced-vertically-4
    [common-fields]
    [voting-fields]
    [disable-commands-fields]
    [processing-states-fields]

    [:div.col.commands
     [atoms/action-button (cancel-action)]
     [atoms/rate-limited-action-button (save-workflow-action)]]]])
