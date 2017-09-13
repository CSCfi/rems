(ns rems.form
  (:require [clj-time.core :as time]
            [clj-time.format :as format]
            [compojure.core :refer [GET POST defroutes]]
            [rems.actions :as actions]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.applicant-info :as applicant-info]
            [rems.collapsible :as collapsible]
            [rems.context :as context]
            [rems.db.applications :refer [can-approve?
                                          can-review?
                                          create-new-draft
                                          get-application-phases
                                          get-application-state
                                          get-draft-id-for get-form-for
                                          can-3rd-party-review?
                                          submit-application]]
            [rems.db.core :as db]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.phase :refer [phases]]
            [rems.role-switcher :refer [when-role]]
            [rems.text :refer :all]
            [rems.util :refer [get-user-id]]
            [ring.util.response :refer [redirect]]))

(def ^:private time-format (format/formatter "yyyy-MM-dd HH:mm"
                                             (time/default-time-zone)))

;; TODO remove id-to-name when no more forms submitted by SPA
(defn- id-to-name [id]
  (str "field" id))

(defn- text-field [{title :title id :id
                    prompt :inputprompt value :value
                    optional :optional
                    readonly :readonly}]
  [:div.form-group
   [:label {:for (id-to-name id)}
    title " "
    (when optional
      (text :t.form/optional))]
   [:input.form-control {:type "text" :name (id-to-name id) :placeholder prompt
                         :value value :readonly readonly}]])

(defn- texta-field [{title :title id :id
                     prompt :inputprompt value :value
                     optional :optional
                     readonly :readonly}]
  [:div.form-group
   [:label {:for (id-to-name id)}
    title " "
    (when optional
      (text :t.form/optional))]
   [:textarea.form-control {:name (id-to-name id) :placeholder prompt
                            :readonly readonly}
    value]])

(defn- label [{title :title}]
  [:div.form-group
   [:label title]])

(defn- license [id readonly approved content]
  [:div.row
   [:div.col-1
    [:input (merge {:type "checkbox" :name (str "license" id) :value "approved"
                    :disabled readonly}
                   (when approved {:checked ""}))]]
   [:div.col
    content]])

(defn- link-license [{title :title id :id textcontent :textcontent approved :approved
                      readonly :readonly}]
  (license id readonly approved
           [:a {:href textcontent :target "_blank"} (str " " title)]))

(defn- text-license [{title :title id :id textcontent :textcontent approved :approved
                      readonly :readonly}]
  (license id readonly approved
           [:div.license-panel
            [:h6.license-title
             [:a.license-header.collapsed {:data-toggle "collapse" :href (str "#collapse" id) :aria-expanded "false" :aria-controls (str "collapse" id)} title]]
            [:div.collapse {:id (str "collapse" id) }
             [:div.license-block textcontent]]]))

(defn- unsupported-field
  [f]
  [:p.alert.alert-warning "Unsupported field " (pr-str f)])

(defn- field [f]
  (case (:type f)
    "text" (text-field f)
    "texta" (texta-field f)
    "label" (label f)
    "license" (case (:licensetype f)
                "link" (link-license f)
                "text" (text-license f)
                (unsupported-field f))
    (unsupported-field f)))



(defn- application-state [state events]
  (when state
    [:div {:class (str "state-" state)}
     (collapsible/component
      "events"
      false
      [:span (text :t.applications/state) ": " (text (localize-state state))
       (when-let [c (:comment (last events))] [:p.inline-comment [:br] (text :t.form/comment) ": " [:span.inline-comment-content] c])]
      (when (seq events)
        (list
         [:h4 (text :t.form/events)]
         (into [:table.table.table-hover.mb-0
                [:tr
                 [:th (text :t.form/user)]
                 [:th (text :t.form/event)]
                 [:th (text :t.form/comment)]
                 [:th (text :t.form/date)]]]
               (for [e events]
                 [:tr
                  [:td (:userid e)]
                  [:td (:event e)]
                  [:td (:comment e)]
                  [:td (format/unparse time-format (:time e))]])))))]))


(defn- form-fields [form]
  (let [state (:state (:application form))
        editable? (or (nil? state) (#{"draft" "returned" "withdrawn"} state))
        readonly? (not editable?)
        withdrawable? (= "applied" state)
        closeable? (and
                    (not (nil? state))
                    (not= "closed" state))]
    (collapsible/component "form"
                           true
                           (:title form)
                           (list
                            (actions/approval-confirm-modal "close" (text :t.actions/close) (:application form))
                            (actions/approval-confirm-modal "withdraw" (text :t.actions/withdraw) (:application form))
                            [:form {:method "post"
                                    :action (if-let [app (:id (:application form))]
                                              (str "/form/" (:catalogue-item form) "/" app "/save")
                                              (str "/form/" (:catalogue-item form) "/save"))}
                             (for [i (:items form)]
                               (field (assoc i :readonly readonly?)))
                             (when-let [licenses (not-empty (:licenses form))]
                               [:div.form-group
                                [:h4 (text :t.form/licenses)]
                                (for [l licenses]
                                  (field (assoc l :readonly readonly?)))])
                             (anti-forgery-field)
                             (when-role :applicant
                               [:div.row
                                [:div.col
                                 [:a.btn.btn-secondary {:href "/catalogue"} (text :t.form/back)]]
                                (into [:div.col.commands]
                                      [(when closeable? [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#close-modal"}
                                                         (text :t.actions/close)])
                                       (when editable? [:button.btn.btn-secondary {:type "submit" :name "save"} (text :t.form/save)])
                                       (when editable? [:button.btn.btn-primary.submit-button {:type "submit" :name "submit"} (text :t.form/submit)])
                                       (when withdrawable? [:button.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#withdraw-modal"} (text :t.actions/withdraw)])
                                       ])])
                             ]))))


(defn- form [form]
  (let [state (:state (:application form))
        actionable? (= state "applied")
        events (get-in form [:application :events])
        user-attributes (or (:applicant-attributes form) context/*user*)]
    (list
     [:h2 (text :t.applications/application)]

     (application-state state events)

     [:div.my-3 (phases (get-application-phases state))]

     (applicant-info/details "applicant-info" user-attributes)

     [:div.my-3 (form-fields form)]

     ;; TODO resource owner should be able to close

     (when-role :approver
       (if (and actionable? (can-approve? (:id (:application form))))
         (actions/approve-form (:application form))
         [:div.row
          [:div.col.commands
           (actions/back-to-actions-button)]])
       )
     (when-role :reviewer
       (if (and actionable? (or (can-review? (:id (:application form)))
                                (can-3rd-party-review? (:id (:application form)))))
         (actions/review-form (:application form))
         [:div.row
          [:div.col.commands
           (actions/back-to-actions-button)]])
       ))))

(defn link-to-item [item]
  (str "/form/" (:id item)))

(defn- validate-item
  [item]
  (when-not (:optional item)
    (when (empty? (:value item))
      (text-format :t.form.validation/required (:title item)))))

(defn- validate-license
  [license]
  (when-not (:approved license)
    (text-format :t.form.validation/required (:title license))))

(defn- validate
  "Validates a filled in form from (get-form-for resource application).

   Returns either :valid or a sequence of validation errors."
  [form]
  (let [messages (vec (concat (filterv identity (map validate-item (:items form)))
                              (filterv identity (map validate-license (:licenses form)))))]
    (if (empty? messages)
      :valid
      messages)))

(defn- format-validation-messages
  [msgs]
  [:ul
   (for [m msgs]
     [:li m])])

(defn- save-fields
  [resource-id application-id input]
  (let [form (get-form-for resource-id)]
    (doseq [{item-id :id :as item} (:items form)]
      (when-let [value (get input item-id (get input (id-to-name item-id)))]
        (db/save-field-value! {:application application-id
                               :form (:id form)
                               :item item-id
                               :user (get-user-id)
                               :value value})))))

(defn save-licenses
  [resource-id application-id input]
  (let [form (get-form-for resource-id)]
    (doseq [{licid :id :as license} (:licenses form)]
      (if-let [state (get input licid (get input (str "license" licid)))]
        (db/save-license-approval! {:catappid application-id
                                    :round 0
                                    :licid licid
                                    :actoruserid (get-user-id)
                                    :state state})
        (db/delete-license-approval! {:catappid application-id
                                      :licid licid
                                      :actoruserid (get-user-id)})))))

(defn- redirect-to-application [resource-id application-id]
  (redirect (str "/form/" resource-id "/" application-id) :see-other))

(defn form-save [resource-id form]
  (let [{:keys [application-id items licenses operation]} form
        application-id (or application-id (create-new-draft resource-id))]
    (save-fields resource-id application-id items)
    (save-licenses resource-id application-id licenses)
    (let [submit? (= operation "send")
          validation (validate (get-form-for resource-id application-id))
          valid? (= :valid validation)
          perform-submit? (and submit? valid?)
          success? (or (not submit?) perform-submit?)]
      (when perform-submit?
        (submit-application application-id))
      (cond-> {:success success?
               :valid valid?}
        (not valid?) (assoc :validation validation)
        success? (assoc :id application-id
                        :state (:state (get-application-state application-id))))
      )))

(defn- save [{params :params input :form-params session :session}]
  (let [resource-id (Long/parseLong (get params :id))
        application-id (if-let [s (get params :application)]
                         (Long/parseLong s)
                         (create-new-draft resource-id))]
    (save-fields resource-id application-id input)
    (save-licenses resource-id application-id input)
    (let [submit (get input "submit")
          validation (validate (get-form-for resource-id application-id))
          valid (= :valid validation)
          perform-submit (and submit valid)
          flash (cond
                  perform-submit ;; valid submit
                  [{:status :success :contents (text :t.form/submitted)}]
                  submit ;; invalid submit
                  [{:status :warning :contents (text :t.form/saved)}
                   {:status :warning :contents (format-validation-messages validation)}]
                  valid ;; valid draft
                  [{:status :success :contents (text :t.form/saved)}]
                  :else ;; invalid draft
                  [{:status :success :contents (text :t.form/saved)}
                   {:status :info :contents (format-validation-messages validation)}])]
      (when perform-submit
        (submit-application application-id))
      (->
       (redirect-to-application resource-id application-id)
       (assoc :flash flash)
       (assoc :session (update session :cart disj resource-id))))))

(defn- form-page [id application]
  (layout/render
   "form"
   (form (get-form-for id application))))

(defroutes form-routes
  (GET "/form/:id/:application" [id application]
       (form-page (Long/parseLong id) (Long/parseLong application)))
  (GET "/form/:id" [id]
       (let [resource-id (Long/parseLong id)]
         (if-let [app (get-draft-id-for resource-id)]
           (redirect-to-application id app)
           (form-page resource-id nil))))
  (POST "/form/:id/save" req (save req))
  (POST "/form/:id/:application/save" req (save req)))

(def ^:private lipsum
  (str "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod "
       "tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim "
       "veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex "
       "ea commodo consequat. Duis aute irure dolor in reprehenderit in "
       "voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur "
       "sint occaecat cupidatat non proident, sunt in culpa qui officia "
       "deserunt mollit anim id est laborum."))

(defn guide
  []
  (list
   (example "field of type \"text\""
            [:form
             (field {:type "text" :title "Title" :inputprompt "prompt"})])
   (example "field of type \"texta\""
            [:form
             (field {:type "texta" :title "Title" :inputprompt "prompt"})])
   (example "optional field"
            [:form
             (field {:type "texta" :optional "true" :title "Title" :inputprompt "prompt"})])
   (example "field of type \"label\""
            [:form
             (field {:type "label" :title "Lorem ipsum dolor sit amet"})])
   (example "link license"
            [:form
             (field {:type "license" :title "Link to license" :licensetype "link" :textcontent "/guide"})])
   (example "text license"
            [:form
             (field {:type "license" :id 1 :title "A Text License" :licensetype "text"
                     :textcontent lipsum})])
   (example "field of unsupported type"
            [:form
             (field {:type "unsupported" :title "Title" :inputprompt "prompt"})])
   (example "form, partially filled"
            (form {:title "Form title"
                   :application {:id 17 :state "draft"}
                   :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                           {:type "label" :title "Please input your wishes below."}
                           {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2"}
                           {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
                   :licenses [{:type "license" :title "A Text License" :licensetype "text" :id 2
                               :textcontent lipsum}
                              {:type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                               :approved true}]}))
   (example "form, applied"
            (form {:title "Form title"
                   :application {:id 17 :state "applied"}
                   :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                           {:type "label" :title "Please input your wishes below."}
                           {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2" :value "def"}
                           {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
                   :licenses [{:type "license" :title "A Text License" :licensetype "text" :id 3
                               :textcontent lipsum}
                              {:type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                               :approved true}]
                   :comments [{:comment "a comment"}]}))

   (example "form, approved"
            (form {:title "Form title"
                   :application {:id 17 :state "approved" :events [{:comment "Looking good, approved!"}]}
                   :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                           {:type "label" :title "Please input your wishes below."}
                           {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2" :value "def"}
                           {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
                   :licenses [{:type "license" :title "A Text License" :licensetype "text" :id 3
                               :textcontent lipsum}
                              {:type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                               :approved true}]
                   :comments [{:comment "a comment"}]}))))
