(ns rems.application
  (:require [ajax.core :refer [GET PUT]]
            [re-frame.core :as rf]
            [rems.collapsible :as collapsible]
            [rems.phase :refer [phases get-application-phases]]
            [rems.text :refer [text localize-state localize-event localize-time]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

;;;; Events and actions ;;;;

(rf/reg-sub
 :application
 (fn [db _]
   (:application db)))

(rf/reg-event-fx
 ::start-fetch-application
 (fn [coeff [_ id]]
   {::fetch-application [(get-in coeff [:db :user]) id]}))

(defn- fetch-application [user id]
  ;; TODO: handle errors (e.g. unauthorized)
  (rf/dispatch [::fetch-application-result nil])
  (GET (str "/api/application/" id) {:handler #(rf/dispatch [::fetch-application-result %])
                                     :response-format :json
                                     :headers {"x-rems-user-id" (:eppn user)}
                                     :keywords? true}))

(rf/reg-fx
 ::fetch-application
 (fn [[user id]]
   (fetch-application user id)))

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

(rf/reg-event-db
 ::set-field
 (fn [db [_ id value]]
   (assoc-in db [:edit-application :items id] value)))

;; status can be :pending :saved :failed or nil
(rf/reg-event-db
 ::set-status
 (fn [db [_ value]]
   (assoc-in db [:edit-application :status] value)))

(defn- save-application [user application-id catalogue-items items licenses]
  (PUT "/api/application" {:headers {"x-rems-api-key" 42
                                     "x-rems-user-id" (:eppn user)}
                           :handler (fn [resp]
                                      (prn :SUCCESS resp)
                                      (if (:success resp)
                                        (rf/dispatch [::set-status :saved])
                                        (rf/dispatch [::set-status :failed])))
                           :error-handler (fn [err]
                                            (prn :FAIL err)
                                            (rf/dispatch [::set-status :failed]))
                           :format :json
                           :params {:operation "save"
                                    ;; TODO why do I need to send these for an existing application?
                                    :catalogue-items catalogue-items
                                    :application-id application-id
                                    :items items
                                    :licenses licenses}}))

(rf/reg-event-fx
 ::save-application
 (fn [{:keys [db]} [_]]
   (let [app-id (get-in db [:application :id])
         catalogue-ids (mapv :id (get-in db [:application :catalogue-items]))
         items (get-in db [:edit-application :items])
         ;; TODO change api to booleans
         licenses (into {}
                        (for [[id checked?] (get-in db [:edit-application :licenses])
                              :when checked?]
                          [id "approved"]))]
     (rf/dispatch [::set-status :pending])
     (save-application (:user db) app-id catalogue-ids items licenses))
   {}))

;;;; UI components ;;;;

;; Fields

(defn- set-field-value
  [id]
  (fn [event]
    (rf/dispatch [::set-field id (.. event -target -value)])))

(defn- get-field-value
  [id]
  (get-in @(rf/subscribe [:edit-application]) [:items id]))

(defn- id-to-name [id]
  (str "field" id))

(defn- text-field
  [{:keys [title id prompt readonly optional]}]
  [:div.form-group.field
   [:label {:for (id-to-name id)}
    title " "
    (when optional
      (text :t.form/optional))]
   [:input.form-control {:type "text" :name (id-to-name id) :placeholder prompt
                         :value (get-field-value id) :readOnly readonly
                         :onChange (set-field-value id)}]])

(defn- texta-field
  [{:keys [title id prompt readonly optional]}]
  [:div.form-group.field
   [:label {:for (id-to-name id)}
    title " "
    (when optional
      (text :t.form/optional))]
   [:textarea.form-control {:name (id-to-name id) :placeholder prompt
                            :value (get-field-value id) :readOnly readonly
                            :onChange (set-field-value id)}]])

(defn- label [{title :title}]
  [:div.form-group
   [:label title]])

(defn- set-license-approval
  [id]
  (fn [event]
    (rf/dispatch [::set-license id (.. event -target -checked)])))

(defn- get-license-approval
  [id]
  (get-in @(rf/subscribe [:edit-application]) [:licenses id]))

(defn- license [id readonly content]
  [:div.row
   [:div.col-1
    [:input {:type "checkbox" :name (str "license" id) :disabled readonly
             :checked (get-license-approval id)
             :onChange (set-license-approval id)}]]
   [:div.col content]])

(defn- link-license
  [{:keys [title id textcontent readonly]}]
  [license id readonly
   [:a {:href textcontent :target "_blank"}
    title " "]])

(defn- text-license
  [{:keys [title id textcontent approved readonly]}]
  [license id readonly
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

(defn- save-button []
  (let [status (:status @(rf/subscribe [:edit-application]))]
    [:div
     [:button#save.btn.btn-secondary
      {:name "save" :onClick #(rf/dispatch [::save-application])}
      (text :t.form/save)]
     ;; TODO nicer styling
     ;; TODO make the spinner spin
     [:span (case status
              nil ""
              :pending [:i {:class "fa fa-spinner"}]
              :saved [:i {:class "fa fa-check-circle"}]
              :failed [:i {:class "fa fa-times-circle"}])]]))

(defn- fields [form]
  (let [application (:application form)
        state (:state application)
        editable? (= "draft" state)
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
               [field (assoc i :readonly readonly?)]))
       (when-let [licenses (not-empty (:licenses form))]
         [:div.form-group.field
          [:h4 (text :t.form/licenses)]
          (into [:div]
                (for [l licenses]
                  [field (assoc l :readonly readonly?)]))])
       (when-not readonly?
         [save-button])]}]))

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
    ;; TODO hide from reviewer
    :collapse (into [:form]
                    (for [[k v] (dissoc user-attributes :commonName :mail)]
                      [info-field k v]))}])

;; Whole application

(defn- applied-resources [catalogue-items]
  [collapsible/component
   {:id "resources"
    :open? true
    :title (text :t.form/resources)
    :always [:div.form-items.form-group
             (into [:ul]
                   (for [item catalogue-items]
                     ^{:key (:id item)}
                     [:li (:title item)]))]}])

(defn- render-application [application]
  ;; TODO should rename :application
  (let [app (:application application)
        state (:state app)
        events (:events app)
        user-attributes (:applicant-attributes application)]
    [:div
     [:h2 (text :t.applications/application)]
     ;; TODO may-see-event? needs to be implemented in backend
     [application-header state events]
     ;; TODO hide from applicant:
     (when user-attributes
       [:div.mt-3 [applicant-info "applicant-info" user-attributes]])
     [:div.mt-3 [applied-resources (:catalogue-items application)]]
     [:div.my-3 [fields application]]]))

;;;; Entrypoint ;;;;

(defn- show-application []
  (if-let [application @(rf/subscribe [:application])]
    [render-application application]
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
   (component-info field)
   (example "field of type \"text\""
            [:form
             [field {:type "text" :title "Title" :inputprompt "prompt"}]])
   (example "field of type \"texta\""
            [:form
             [field {:type "texta" :title "Title" :inputprompt "prompt"}]])
   (example "optional field"
            [:form
             [field {:type "texta" :optional "true" :title "Title" :inputprompt "prompt"}]])
   (example "field of type \"label\""
            [:form
             [field {:type "label" :title "Lorem ipsum dolor sit amet"}]])
   (example "link license"
            [:form
             [field {:type "license" :title "Link to license" :licensetype "link" :textcontent "/guide"}]])
   (example "text license"
            [:form
             [field {:type "license" :id 1 :title "A Text License" :licensetype "text"
                     :textcontent lipsum}]])

   (component-info render-application)
   (example "application, partially filled"
            [render-application
             {:title "Form title"
              :application {:id 17 :state "draft"
                            :can-approve? false
                            :can-close? false
                            :review-type nil}
              :catalogue-items [{:title "An applied item"}]
              :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                      {:type "label" :title "Please input your wishes below."}
                      {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2"}
                      {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
              :licenses [{:type "license" :title "A Text License" :licensetype "text" :id 2
                          :textcontent lipsum}
                         {:type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                          :approved true}]}])
   (example "form, approved"
            [render-application
             {:title "Form title"
              :catalogue-items [{:title "An applied item"}]
              :applicant-attributes {:eppn "eppn" :mail "email@example.com" :additional "additional field"}
              :application {:id 17 :state "approved"
                            :can-approve? false
                            :can-close? true
                            :review-type nil
                            :events [{:event "approve" :comment "Looking good, approved!"}]}
              :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                      {:type "label" :title "Please input your wishes below."}
                      {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2" :value "def"}
                      {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
              :licenses [{:type "license" :title "A Text License" :licensetype "text" :id 3
                          :textcontent lipsum}
                         {:type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                          :approved true}]
              :comments [{:comment "a comment"}]}])])
