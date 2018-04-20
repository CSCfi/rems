(ns rems.application
  (:require [ajax.core :refer [GET PUT]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.collapsible :as collapsible]
            [rems.db.catalogue :refer [get-catalogue-item-title]]
            [rems.phase :refer [phases get-application-phases]]
            [rems.text :refer [text text-format localize-state localize-event localize-time]]
            [rems.util :refer [dispatch! index-by]]
            [secretary.core :as secretary])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;;;; Routes and route helpers ;;;;
;; TODO named secretary routes give us equivalent functions
;; TODO should the secretary route definitions be in this ns too?

(defn apply-for [items]
  (let [url (str "#/application?items=" (str/join "," (sort (map :id items))))]
    (dispatch! url)))

(defn navigate-to [id]
  (dispatch! (str "#/application/" id)))

;;;; Events and actions ;;;;

(rf/reg-event-db
 ::zero-state
 (fn [db _]
   (assoc db :application nil :edit-application nil ::judge-comment "")))

(rf/reg-sub
 :application
 (fn [db _]
   (:application db)))

(rf/reg-event-fx
 ::start-fetch-application
 (fn [{:keys [db]} [_ id]]
   {::fetch-application [(get-in db [:identity :user]) id]}))

(rf/reg-event-fx
 ::start-new-application
 (fn [{:keys [db]} [_ items]]
   {::fetch-draft-application [(get-in db [:identity :user]) items]}))

(defn- fetch-application [user id]
  (GET (str "/api/application/" id) {:handler #(rf/dispatch [::fetch-application-result %])
                                     :response-format :json
                                     :headers {"x-rems-user-id" (:eppn user)}
                                     :keywords? true}))

(defn- fetch-draft-application [user items]
  (GET (str "/api/application/") {:handler #(rf/dispatch [::fetch-application-result %])
                                  :params {:catalogue-items items}
                                  :response-format :json
                                  :headers {"x-rems-user-id" (:eppn user)}
                                  :keywords? true}))

(rf/reg-fx
 ::fetch-application
 (fn [[user id]]
   (fetch-application user id)))

(rf/reg-fx
 ::fetch-draft-application
 (fn [[user items]]
   (fetch-draft-application user items)))

(rf/reg-event-db
 ::fetch-application-result
 (fn [db [_ application]]
   (assoc db
          :application application
          ;; TODO: should this be here?
          :edit-application
          {:items (into {}
                        (for [field (:items application)]
                          [(:id field) (:value field)]))
           :licenses (into {}
                           (for [license (:licenses application)]
                             [(:id license) (:approved license)]))})))

(rf/reg-sub
 :edit-application
 (fn [db _]
   (:edit-application db)))

(rf/reg-event-db
 ::set-field
 (fn [db [_ id value]]
   (assoc-in db [:edit-application :items id] value)))

(rf/reg-event-db
 ::set-license
 (fn [db [_ id value]]
   (assoc-in db [:edit-application :licenses id] value)))

(rf/reg-sub
 ::judge-comment
 (fn [db _]
   (::judge-comment db)))

(rf/reg-event-db
 ::set-judge-comment
 (fn [db [_ value]]
   (assoc db ::judge-comment value)))

;; status can be :pending :saved :failed or nil
(rf/reg-event-db
 ::set-status
 (fn [db [_ value validation]]
   (-> db
       (assoc-in [:edit-application :status] value)
       (assoc-in [:edit-application :validation] validation))))

(defn- save-application [command user application-id catalogue-items items licenses]
  (let [payload (merge {:command command
                        :items items
                        :licenses licenses}
                       (if application-id
                         {:application-id application-id}
                         {:catalogue-items catalogue-items}))]
    (PUT "/api/application/save"
         {:handler (fn [resp]
                     (if (:success resp)
                       (do (rf/dispatch [::set-status :saved])
                           ;; HACK: we both set the location, and fire a fetch-application event
                           ;; because if the location didn't change, secretary won't fire the event
                           (navigate-to (:id resp))
                           (rf/dispatch [::start-fetch-application (:id resp)]))
                       (rf/dispatch [::set-status :failed (:validation resp)])))
          :error-handler (fn [err]
                           (rf/dispatch [::set-status :failed]))
          :format :json
          :params payload})))

(rf/reg-event-fx
 ::save-application
 (fn [{:keys [db]} [_ command]]
   (let [app-id (get-in db [:application :application :id])
         catalogue-ids (mapv :id (get-in db [:application :catalogue-items]))
         items (get-in db [:edit-application :items])
         ;; TODO change api to booleans
         licenses (into {}
                        (for [[id checked?] (get-in db [:edit-application :licenses])
                              :when checked?]
                          [id "approved"]))]
     ;; TODO disable form while saving?
     (rf/dispatch [::set-status :pending])
     (save-application command (get-in db [:identity :user]) app-id catalogue-ids items licenses))
   {}))

(defn- judge-application [command user application-id round comment]
  (PUT "/api/application/judge"
       {:format :json
        :params {:command command
                 :application-id application-id
                 :round round
                 :comment comment}
        :handler (fn [resp]
                   (rf/dispatch [::start-fetch-application application-id]))}))

(rf/reg-event-fx
 ::judge-application
 (fn [{:keys [db]} [_ command]]
   (let [application-id (get-in db [:application :application :id])
         round (get-in db [:application :application :curround])
         user (get-in db [:identity :user])
         comment (get db ::judge-comment "")]
     (judge-application command user application-id round comment)
     {})))

;;;; UI components ;;;;

(defn- format-validation-messages
  [msgs]
  (into [:ul]
        (for [m msgs]
          [:li (text-format (:key m) (:title (:field m)))])))

(defn flash-message
  "Displays a notification (aka flash) message.

   :status   - one of the alert types :success, :info, :warning or :failure
   :contents - content to show inside the notification"
  [{status :status contents :contents}]
  (when status
    [:div.alert
     ;; TODO should this case and perhaps unnecessary mapping from keywords to Bootstrap be removed?
     {:class (case status
               :success "alert-success"
               :warning "alert-warning"
               :failure "alert-danger"
               :info "alert-info")}
     contents]))

;; Fields

(defn- set-field-value
  [id]
  (fn [event]
    (rf/dispatch [::set-field id (.. event -target -value)])))

(defn- id-to-name [id]
  (str "field" id))

(defn- field-validation-message [validation]
  (when validation
    [:div {:class "text-danger"}
     (text-format (:key validation) (:title (:field validation)))]))

(defn- text-field
  [{:keys [title id prompt readonly optional value validation]}]
  [:div.form-group.field
   [:label {:for (id-to-name id)}
    title " "
    (when optional
      (text :t.form/optional))]
   [:input.form-control {:type "text"
                         :name (id-to-name id)
                         :placeholder prompt
                         :class (when validation "is-invalid")
                         :value value :readOnly readonly
                         :onChange (set-field-value id)}]
   [field-validation-message validation]])

(defn- texta-field
  [{:keys [title id prompt readonly optional value validation]}]
  [:div.form-group.field
   [:label {:for (id-to-name id)}
    title " "
    (when optional
      (text :t.form/optional))]
   [:textarea.form-control {:name (id-to-name id)
                            :placeholder prompt
                            :class (when validation "is-invalid")
                            :value value :readOnly readonly
                            :onChange (set-field-value id)}]
   [field-validation-message validation]])

(defn- label [{title :title}]
  [:div.form-group
   [:label title]])

(defn- set-license-approval
  [id]
  (fn [event]
    (rf/dispatch [::set-license id (.. event -target -checked)])))

(defn- license [id approved readonly validation content]
  [:div
   [:div.row
    [:div.col-1
     [:input {:type "checkbox"
              :name (str "license" id)
              :disabled readonly
              :class (when validation "is-invalid")
              :checked approved
              :onChange (set-license-approval id)}]]
    [:div.col content]]
   [:div.row
    [:div.col
     [field-validation-message validation]]]])

(defn- link-license
  [{:keys [title id textcontent readonly approved validation]}]
  [license id approved readonly validation
   [:a {:href textcontent :target "_blank"}
    title " "]])

(defn- text-license
  [{:keys [title id textcontent approved readonly validation]}]
  [license id approved readonly validation
   [:div.license-panel
    [:h6.license-title
     [:a.license-header.collapsed {:data-toggle "collapse"
                                   :href (str "#collapse" id)
                                   :aria-expanded "false"
                                   :aria-controls (str "collapse" id)}
      title " " [:i {:class "fa fa-ellipsis-h"}]]]
    [:div.collapse {:id (str "collapse" id) }
     [:div.license-block textcontent]]]])

(defn- unsupported-field
  [f]
  [:p.alert.alert-warning "Unsupported field " (pr-str f)])

(defn- field [f]
  (case (:type f)
    "text" [text-field f]
    "texta" [texta-field f]
    "label" [label f]
    "license" (case (:licensetype f)
                "link" [link-license f]
                "text" [text-license f]
                [unsupported-field f])
    [unsupported-field f]))

(defn- status-widget []
  (let [status (:status @(rf/subscribe [:edit-application]))]
    [:span (case status
             nil ""
             :pending [:i {:class "fa fa-spinner"}]
             :saved [:i {:class "fa fa-check-circle"}]
             :failed [:i {:class "fa fa-times-circle text-danger"}])]))

(defn- save-button []
  [:button#save.btn.btn-secondary
   {:name "save" :onClick #(rf/dispatch [::save-application "save"])}
   (text :t.form/save)])

(defn- submit-button []
  [:button#submit.btn.btn-primary
   {:name "submit" :onClick #(rf/dispatch [::save-application "submit"])}
   (text :t.form/submit)])

(defn- fields [form edit-application]
  (let [application (:application form)
        {:keys [items licenses validation]} edit-application
        validation-by-field-id (index-by [(comp :type :field) (comp :id :field)] validation)
        state (:state application)
        editable? (#{"draft" "returned" "withdrawn"} state)
        readonly? (not editable?)]
    [collapsible/component
     {:id "form"
      :class "slow"
      :open? true
      :title (text :t.form/application)
      :collapse
      [:div
       (into [:div]
             (for [i (:items form)]
               [field (assoc i
                             :validation (get-in validation-by-field-id [:item (:id i)])
                             :readonly readonly?
                             :value (get items (:id i)))]))
       (when-let [form-licenses (not-empty (:licenses form))]
         [:div.form-group.field
          [:h4 (text :t.form/licenses)]
          (into [:div]
                (for [l form-licenses]
                  [field (assoc l
                                :validation (get-in validation-by-field-id [:license (:id l)])
                                :readonly readonly?
                                :approved (get licenses (:id l)))]))])
       (when-not readonly?
         [:div.col.commands
          [status-widget]
          [save-button]
          [submit-button]])]}]))

;; Header

(defn- info-field
  "A component that shows a readonly field with title and value.

  Used for e.g. displaying applicant attributes."
  [title value]
  [:div.form-group
   [:label title]
   [:input.form-control {:type "text" :defaultValue value :readOnly true}]])

(defn- application-header [state events]
  [collapsible/component
   {:id "header"
    :title [:span
            (text :t.applications/state)
            (when state (list ": " (localize-state state)))]
    :always [:div
             [:div.mb-3 {:class (str "state-" state)} (phases (get-application-phases state))]
             (when-let [c (:comment (last events))]
               (info-field (text :t.form/comment) c))]
    :collapse (when (seq events)
                [:div
                 [:h4 (text :t.form/events)]
                 (into [:table#event-table.table.table-hover.mb-0
                        [:thead
                         [:tr
                          [:th (text :t.form/user)]
                          [:th (text :t.form/event)]
                          [:th (text :t.form/comment)]
                          [:th (text :t.form/date)]]]
                        (into [:tbody]
                              (for [e events]
                                [:tr
                                 [:td (:userid e)]
                                 [:td (localize-event (:event e))]
                                 [:td.event-comment (:comment e)]
                                 [:td (localize-time (:time e))]]))])])}])

;; Applicant info

(defn applicant-info [id user-attributes]
  [collapsible/component
   {:id id
    :title (str (text :t.applicant-info/applicant))
    :always [:div.row
             [:div.col-md-6
              [info-field (text :t.applicant-info/username) (:eppn user-attributes)]]
             [:div.col-md-6
              [info-field (text :t.applicant-info/email) (:mail user-attributes)]]]
    :collapse (into [:form]
                    (for [[k v] (dissoc user-attributes :commonName :mail)]
                      [info-field k v]))}])

;; Approval

(defn- approve-button []
  [:button#submit.btn.btn-primary
   {:name "approve" :onClick #(rf/dispatch [::judge-application "approve"])}
   (text :t.actions/approve)])

(defn- reject-button []
  [:button#submit.btn.btn-secondary
   {:name "reject" :onClick #(rf/dispatch [::judge-application "reject"])}
   (text :t.actions/reject)])

(defn- return-button []
  [:button#submit.btn.btn-secondary
   {:name "return" :onClick #(rf/dispatch [::judge-application "return"])}
   (text :t.actions/return)])

(defn- actions-form [app]
  (let [buttons (concat (when (:can-approve? app)
                          [[reject-button]
                           [return-button]
                           [approve-button]]))]
    (if (empty? buttons)
      [:div]
      [collapsible/component
       {:id "actions"
        :title (text :t.form/actions)
        :always [:div
                 [:div.form-group
                  [:textarea.form-control
                   {:name "judge-comment" :placeholder "Comment"
                    :value @(rf/subscribe [::judge-comment])
                    :onChange #(rf/dispatch [::set-judge-comment (.. % -target -value)])}]]
                 (into [:div.col.commands]
                       buttons)]}])))

;; Whole application

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

(defn- render-application [application edit-application]
  ;; TODO should rename :application
  (let [app (:application application)
        state (:state app)
        events (:events app)
        user-attributes (:applicant-attributes application)]
    [:div
     [:h2 (text :t.applications/application)]
     [disabled-items-warning (:catalogue-items application)]
     (when (:validation edit-application)
       [flash-message
        {:status :failure
         :contents [:div (text :t.form/validation.errors)
                    [format-validation-messages (:validation edit-application)]]}])
     [application-header state events]
     (when user-attributes
       [:div.mt-3 [applicant-info "applicant-info" user-attributes]])
     [:div.mt-3 [applied-resources (:catalogue-items application)]]
     [:div.my-3 [fields application edit-application]]
     [:div.mb-3 [actions-form app]]]))

;;;; Entrypoint ;;;;

(defn- show-application []
  (if-let [application @(rf/subscribe [:application])]
    (let [edit-application @(rf/subscribe [:edit-application])]
      [render-application application edit-application])
    [:p "No application loaded"]))

(defn application-page []
  [show-application])

;;;; Guide ;;;;

(def ^:private lipsum
  (str "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod "
       "tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim "
       "veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex "
       "ea commodo consequat. Duis aute irure dolor in reprehenderit in "
       "voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur "
       "sint occaecat cupidatat non proident, sunt in culpa qui officia "
       "deserunt mollit anim id est laborum."))

(defn guide []
  [:div
   (component-info info-field)
   (example "info-field with data"
            [info-field "Name" "Bob Tester"])
   (component-info applicant-info)
   ;; TODO: fix applicant-info example when we have roles
   (example "applicant-info for applicant shows no details"
            [applicant-info "info1" {:eppn "developer@uu.id"
                                     :mail "developer@uu.id"
                                     :commonName "Deve Loper"
                                     :organization "Testers"
                                     :address "Testikatu 1, 00100 Helsinki"}])
   (example "applicant-info for approver shows attributes"
            [applicant-info "info2" {:eppn "developer@uu.id"
                                     :mail "developer@uu.id"
                                     :commonName "Deve Loper"
                                     :organization "Testers"
                                     :address "Testikatu 1, 00100 Helsinki"}])

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

   (component-info flash-message)
   (example "flash-message with info"
            [flash-message {:status :info
                            :contents "Hello world"}])

   (example "flash-message with error"
            [flash-message {:status :failure
                            :contents "You fail"}])

   (component-info field)
   (example "field of type \"text\""
            [:form
             [field {:type "text" :title "Title" :inputprompt "prompt"}]])
   (example "field of type \"text\" with validation error"
            [:form
             [field {:type "text" :title "Title" :inputprompt "prompt"
                     :validation {:field {:title "Title"} :key :t.form.validation.required}}]])
   (example "field of type \"texta\""
            [:form
             [field {:type "texta" :title "Title" :inputprompt "prompt"}]])
   (example "field of type \"texta\" with validation error"
            [:form
             [field {:type "texta" :title "Title" :inputprompt "prompt"
                     :validation {:field {:title "Title"} :key :t.form.validation.required}}]])
   (example "optional field"
            [:form
             [field {:type "texta" :optional "true" :title "Title" :inputprompt "prompt"}]])
   (example "field of type \"label\""
            [:form
             [field {:type "label" :title "Lorem ipsum dolor sit amet"}]])
   (example "link license"
            [:form
             [field {:type "license" :title "Link to license" :licensetype "link" :textcontent "/guide"}]])
   (example "link license with validation error"
            [:form
             [field {:type "license" :title "Link to license" :licensetype "link" :textcontent "/guide"
                     :validation {:field {:title "Link to license"} :key :t.form.validation.required}}]])
   (example "text license"
            [:form
             [field {:type "license" :id 1 :title "A Text License" :licensetype "text"
                     :textcontent lipsum}]])
   (example "text license with validation error"
            [:form
             [field {:type "license" :id 1 :title "A Text License" :licensetype "text" :textcontent lipsum
                     :validation {:field {:title "A Text License"} :key :t.form.validation.required}}]])

   (component-info render-application)
   (example "application, partially filled"
            [render-application
             {:title "Form title"
              :application {:id 17 :state "draft"
                            :can-approve? false
                            :can-close? false
                            :review-type nil}
              :catalogue-items [{:title "An applied item"}]
              :items [{:id 1 :type "text" :title "Field 1" :inputprompt "prompt 1"}
                      {:id 2 :type "label" :title "Please input your wishes below."}
                      {:id 3 :type "texta" :title "Field 2" :optional true :inputprompt "prompt 2"}
                      {:id 4 :type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
              :licenses [{:id 4 :type "license" :title "A Text License" :licensetype "text"
                          :textcontent lipsum}
                         {:id 5 :type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"}]}
             {:items {1 "abc"}
              :licenses {4 false 5 true}}])
   (example "application, applied"
            [render-application
             {:title "Form title"
              :application {:id 17 :state "applied"
                            :can-approve? true
                            :can-close? false
                            :review-type nil}
              :catalogue-items [{:title "An applied item"}]
              :items [{:id 1 :type "text" :title "Field 1" :inputprompt "prompt 1"}]
              :licenses [{:id 2 :type "license" :title "A Text License" :licensetype "text"
                          :textcontent lipsum}]}
             {:items {1 "abc"}
              :licenses {2 true}}])
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
