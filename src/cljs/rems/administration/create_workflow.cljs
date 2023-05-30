(ns rems.administration.create-workflow
  (:require [clojure.string :as str]
            [medley.core :refer [find-first indexed remove-nth update-existing]]
            [re-frame.core :as rf]
            [rems.administration.administration :as administration]
            [rems.administration.components :refer [organization-field radio-button-group text-field]]
            [rems.administration.items :as items]
            [rems.atoms :as atoms :refer [enrich-user document-title]]
            [rems.collapsible :as collapsible]
            [rems.config :as config]
            [rems.common.application-util :as application-util]
            [rems.common.util :refer [andstr conj-vec keep-keys replace-key]]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.fields :as fields]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.text :refer [localized localize-command localize-role localize-state text text-format]]
            [rems.util :refer [navigate! post! put! trim-when-string]]))

(defn- item-by-id [items id-key id]
  (find-first #(= (id-key %) id) items))

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

;;; form state

(rf/reg-sub ::form (fn [db _] (::form db)))

(rf/reg-event-db ::set-form-field
                 (fn [db [_ keys value]] (assoc-in db (concat [::form] keys) value)))

(rf/reg-event-db ::fetch-workflow-success
                 (fn [db [_ {:keys [title organization workflow]}]]
                   (update db ::form merge {:title title
                                            :organization organization
                                            :type (:type workflow)
                                            :forms (mapv #(select-keys % [:form/id]) (get workflow :forms))
                                            :handlers (get workflow :handlers)
                                            :licenses (->> (:licenses workflow)
                                                           (mapv #(replace-key % :license/id :id)))
                                            :disable-commands (get workflow :disable-commands)})))

(fetcher/reg-fetcher ::workflow "/api/workflows/:id" {:path-params (fn [db] {:id (::workflow-id db)})
                                                      :on-success #(rf/dispatch [::fetch-workflow-success %])})

;;; form submit

(def workflow-types #{:workflow/default :workflow/decider :workflow/master})

(defn needs-handlers? [type]
  (contains? #{:workflow/default :workflow/decider :workflow/master} type))

(defn- valid-create-request? [request]
  (and
   (contains? workflow-types (:type request))
   (if (needs-handlers? (:type request))
     (seq (:handlers request))
     true)
   (not (str/blank? (get-in request [:organization :organization/id])))
   (not (str/blank? (:title request)))
   (every? :command (:disable-commands request))))

(defn build-create-request [form]
  (let [request (merge
                 {:organization {:organization/id (get-in form [:organization :organization/id])}
                  :title (trim-when-string (:title form))
                  :type (:type form)
                  :forms (mapv #(select-keys % [:form/id]) (:forms form))
                  :licenses (vec (keep-keys {:id :license/id} (:licenses form)))
                  :disable-commands (->> (:disable-commands form)
                                         (mapv #(update-existing % :when/state vec))
                                         (mapv #(update-existing % :when/role vec)))}
                 (when (needs-handlers? (:type form))
                   {:handlers (map :userid (:handlers form))}))]
    (when (valid-create-request? request)
      request)))

(defn- valid-edit-request? [request]
  (and (number? (:id request))
       (seq (:handlers request))
       (not (str/blank? (get-in request [:organization :organization/id])))
       (not (str/blank? (:title request)))
       (every? :command (:disable-commands request))))

(defn build-edit-request [id form]
  (let [request {:organization {:organization/id (get-in form [:organization :organization/id])}
                 :id id
                 :title (:title form)
                 :handlers (map :userid (:handlers form))
                 :disable-commands (->> (:disable-commands form)
                                        (mapv #(update-existing % :when/state vec))
                                        (mapv #(update-existing % :when/role vec)))}]
    (when (valid-edit-request? request)
      request)))

(rf/reg-event-fx ::create-workflow
                 (fn [_ [_ request]]
                   (let [description [text :t.administration/create-workflow]]
                     (post! "/api/workflows/create"
                            {:params request
                             :handler (flash-message/default-success-handler
                                       :top description #(navigate! (str "/administration/workflows/" (:id %))))
                             :error-handler (flash-message/default-error-handler :top description)}))
                   {}))

(rf/reg-event-fx ::edit-workflow
                 (fn [_ [_ request]]
                   (let [description [text :t.administration/edit-workflow]]
                     (put! "/api/workflows/edit"
                           {:params request
                            :handler (flash-message/default-success-handler
                                      :top description #(navigate! (str "/administration/workflows/" (:id request))))
                            :error-handler (flash-message/default-error-handler :top description)}))
                   {}))

(rf/reg-event-db ::set-handlers (fn [db [_ handlers]] (assoc-in db [::form :handlers] (sort-by :userid handlers))))

(fetcher/reg-fetcher ::actors "/api/workflows/actors" {:result (partial mapv enrich-user)})

(rf/reg-event-db ::set-forms (fn [db [_ form-ids]] (assoc-in db [::form :forms] form-ids)))

(fetcher/reg-fetcher ::forms "/api/forms")

(rf/reg-sub ::selected-licenses (fn [db _] (get-in db [::form :licenses])))
(rf/reg-event-db ::set-licenses (fn [db [_ licenses]]
                                  (->> (sort-by :id licenses)
                                       (assoc-in db [::form :licenses]))))
(fetcher/reg-fetcher ::licenses "/api/licenses")

(fetcher/reg-fetcher ::commands "/api/applications/commands")
(rf/reg-sub ::available-commands
            :<- [::commands]
            (fn [commands]
              (->> (sort commands)
                   (remove #{:application.command/assign-external-id
                             :application.command/create
                             :application.command/send-expiration-notifications}))))

;;;; UI

(def ^:private context
  {:get-form ::form
   :update-form ::set-form-field})

(def ^:private handlers-dropdown-id "handlers-dropdown")

(defn- workflow-organization-field []
  [organization-field context {:keys [:organization]}])

(defn- workflow-title-field []
  [text-field context {:keys [:title]
                       :label (text :t.create-workflow/title)}])

(def ^:private licenses-dropdown-id "licenses-dropdown")

(defn- workflow-licenses-field []
  (let [licenses @(rf/subscribe [::licenses])
        selected-licenses @(rf/subscribe [::selected-licenses])
        editing? @(rf/subscribe [::editing?])]
    [:div.form-group
     [:label.administration-field-label {:for licenses-dropdown-id}
      (text :t.create-resource/licenses-selection)]
     (if editing?
       [fields/readonly-field-raw
        {:id "workflow-licenses"
         :values (for [license selected-licenses
                       :let [uri (str "/administration/licenses/" (:license/id license))
                             title (:title (localized (:localizations license)))]]
                   [atoms/link nil uri title])}]
       [dropdown/dropdown
        {:id licenses-dropdown-id
         :items licenses
         :item-key :id
         :item-label (fn [license]
                       (let [title (:title (localized (:localizations license)))
                             organization (localized (get-in license [:organization
                                                                      :organization/short-name]))]
                         (str title " (org: " organization ")"))) ; XXX: workaround for get-localized-title
         :item-selected? #(contains? (set selected-licenses) %)
         :multi? true
         :on-change #(rf/dispatch [::set-licenses %])}])]))

(defn- workflow-type-field []
  [radio-button-group context {:id :workflow-type
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
                                             :label (text :t.create-workflow/master-workflow)}]))}])

(defn- save-workflow-button []
  (let [form @(rf/subscribe [::form])
        id @(rf/subscribe [::workflow-id])
        request (if id
                  (build-edit-request id form)
                  (build-create-request form))]
    [:button.btn.btn-primary
     {:type :button
      :id :save
      :on-click (fn []
                  (rf/dispatch [:rems.spa/user-triggered-navigation])
                  (if id
                    (rf/dispatch [::edit-workflow request])
                    (rf/dispatch [::create-workflow request])))
      :disabled (nil? request)}
     (text :t.administration/save)]))

(defn- cancel-button []
  [atoms/link {:class "btn btn-secondary"}
   (str "/administration/workflows" (andstr "/" @(rf/subscribe [::workflow-id])))
   (text :t.administration/cancel)])

(defn workflow-type-description [description]
  [:div.alert.alert-info description])

(defn- create-rule [{:keys [db]} [_]]
  {:db (update-in db [::form :disable-commands] conj-vec {})
   :rems.focus/scroll-into-view [".disable-commands" {:block :end}]})

(defn- remove-rule [{:keys [db]} [_ rule-index]]
  {:db (update-in db [::form :disable-commands] #(vec (remove-nth rule-index %)))
   :rems.focus/scroll-into-view [".disable-commands" {:block :end}]})

(defn- set-rule [k]
  (fn [db [_ rule-index v]]
    (assoc-in db [::form :disable-commands rule-index k] v)))

(rf/reg-sub ::disable-commands (fn [db _] (get-in db [::form :disable-commands])))
(rf/reg-event-fx ::create-rule create-rule)
(rf/reg-event-fx ::remove-rule remove-rule)
(rf/reg-event-db ::select-rule-command (set-rule :command))
(rf/reg-event-db ::select-rule-application-states (set-rule :when/state))
(rf/reg-event-db ::select-rule-user-roles (set-rule :when/role))

(defn- select-command [{:keys [id commands value on-change]}]
  [:div.form-group.select-command
   [:label.administration-field-label {:for id} (text :t.administration/disabled-command)]
   [dropdown/dropdown
    {:id id
     :items commands
     :item-label #(str (localize-command %) " (" (name %) ")")
     :item-selected? #(= value %)
     :on-change on-change}]])

(defn- select-application-states [{:keys [id value on-change]}]
  [:div.form-group.select-application-states
   [:label.administration-field-label {:for id} (text :t.administration/application-state)]
   [dropdown/dropdown
    {:id id
     :items application-util/states
     :item-label #(str (localize-state %) " (" (name %) ")")
     :item-selected? #(contains? (set value) %)
     :placeholder (text :t.dropdown/placeholder-any-selection)
     :multi? true
     :on-change on-change}]])

(defn- select-user-roles []
  (let [applicant-roles [:applicant :member]
        expert-roles [:handler :reviewer :decider :past-reviewer :past-decider]
        technical-roles [:expirer :reporter]]
    (fn [{:keys [id value on-change]}]
      [:div.form-group.select-application-states
       [:label.administration-field-label {:for id} (text :t.administration/user-role)]
       [dropdown/dropdown
        {:id id
         :items (concat applicant-roles expert-roles technical-roles)
         :item-label #(if (some #{%} technical-roles)
                        (str (text :t.roles/technical-role) " (" (name %) ")")
                        (str (localize-role %) " (" (name %) ")"))
         :item-selected? #(contains? (set value) %)
         :placeholder (text :t.dropdown/placeholder-any-selection)
         :multi? true
         :on-change on-change}]])))

(defn- render-disable-command-rule [rule-index rule]
  (let [id (str "disable-command-" rule-index)]
    [:div.form-field.disable-command
     [:div.form-field-header
      [:h4 (text :t.create-workflow/rule)]
      [:div.form-field-controls
       [items/remove-button #(rf/dispatch [::remove-rule rule-index])]]]
     [select-command {:id (str id "-select-command")
                      :commands @(rf/subscribe [::available-commands])
                      :value (:command rule)
                      :on-change #(rf/dispatch [::select-rule-command rule-index %])}]
     [:div.row
      [:div.col-md
       [select-application-states {:id (str id "-select-application-states")
                                   :value (:when/state rule)
                                   :on-change #(rf/dispatch [::select-rule-application-states rule-index (vec %)])}]]
      [:div.col-md
       [select-user-roles {:id (str id "-select-user-roles")
                           :value (:when/role rule)
                           :on-change #(rf/dispatch [::select-rule-user-roles rule-index (vec %)])}]]]]))

(defn- workflow-disable-commands-field []
  (let [disable-commands @(rf/subscribe [::disable-commands])]
    [:div.form-group.disable-commands
     [:label.administration-field-label
      (text-format :t.label/optional (text :t.create-workflow/disable-commands))]
     (when (seq disable-commands)
       [:div.alert.alert-info (text :t.administration/workflow-disabled-commands-explanation)])
     (into [:<>]
           (for [[rule-index rule] (indexed disable-commands)]
             [render-disable-command-rule rule-index rule]))
     [:div.dashed-group.text-center
      [:a#new-rule {:href "#"
                    :on-click (fn [event]
                                (.preventDefault event)
                                (rf/dispatch [::create-rule]))}
       (text :t.create-workflow/create-new-rule)]]]))

;; TODO: Eventually filter handlers by the selected organization when
;;   we are sure that all the handlers have the organization information?
(defn- workflow-handlers-field []
  (let [form @(rf/subscribe [::form])
        all-handlers @(rf/subscribe [::actors])
        selected-handlers (set (map :userid (get-in form [:handlers])))]
    [:div.form-group
     [:label.administration-field-label {:for handlers-dropdown-id} (text :t.create-workflow/handlers)]
     [dropdown/dropdown
      {:id handlers-dropdown-id
       :items all-handlers
       :item-key :userid
       :item-label :display
       :item-selected? #(contains? selected-handlers (% :userid))
       :multi? true
       :on-change #(rf/dispatch [::set-handlers %])}]]))

(defn- workflow-forms-field []
  (let [all-forms @(rf/subscribe [::forms])
        selected-form-ids (set (mapv :form/id (:forms @(rf/subscribe [::form]))))
        id "workflow-forms"]
    [:div.form-group
     [:label.administration-field-label {:for id} (text :t.administration/forms)]
     (if @(rf/subscribe [::editing?])
       [fields/readonly-field-raw
        {:id id
         :values (for [form (map (partial item-by-id all-forms :form/id) selected-form-ids)]
                   [atoms/link nil
                    (str "/administration/forms/" (:form/id form))
                    (:form/internal-name form)])}]
       [dropdown/dropdown
        {:id id
         :items (->> all-forms (filter :enabled) (remove :archived))
         :item-key :form/id
         :item-label :form/internal-name
         :item-selected? #(contains? selected-form-ids (:form/id %))
         ;; TODO support ordering multiple forms
         :multi? true
         :disabled? @(rf/subscribe [::editing?])
         :on-change #(rf/dispatch [::set-forms %])}])]))

(defn default-workflow-form []
  [:div
   [workflow-type-description (text :t.create-workflow/default-workflow-description)]
   [workflow-handlers-field]
   [workflow-forms-field]])

(defn decider-workflow-form []
  [:div
   [workflow-type-description (text :t.create-workflow/decider-workflow-description)]
   [workflow-handlers-field]
   [workflow-forms-field]])

(defn master-workflow-form []
  [:div
   [workflow-type-description (text :t.create-workflow/master-workflow-description)]
   [workflow-handlers-field]
   [workflow-forms-field]])

(defn create-workflow-page []
  (let [form @(rf/subscribe [::form])
        workflow-type (:type form)
        loading? (or @(rf/subscribe [::actors :fetching?])
                     @(rf/subscribe [::workflow :fetching?]))
        editing? @(rf/subscribe [::editing?])
        title (if editing?
                (text :t.administration/edit-workflow)
                (text :t.administration/create-workflow))]
    [:div
     [administration/navigator]
     [document-title title]
     [flash-message/component :top]
     [collapsible/component
      {:id "create-workflow"
       :title title
       :always (if loading?
                 [:div#workflow-loader [spinner/big]]
                 [:div#workflow-editor.fields
                  [workflow-organization-field]
                  [workflow-title-field]

                  [workflow-type-field]
                  (case workflow-type
                    :workflow/default [default-workflow-form]
                    :workflow/decider [decider-workflow-form]
                    :workflow/master [master-workflow-form])

                  [workflow-licenses-field]
                  [workflow-disable-commands-field]

                  [:div.col.commands
                   [cancel-button]
                   [save-workflow-button]]])}]]))
