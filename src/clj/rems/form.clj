(ns rems.form
  (:require [clj-time.core :as time]
            [clj-time.format :as format]
            [clojure.string :as s]
            [clojure.set :refer [difference]]
            [compojure.core :refer [GET POST defroutes]]
            [hiccup.util :refer [url]]
            [rems.actions :as actions]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.applicant-info :as applicant-info]
            [rems.collapsible :as collapsible]
            [rems.context :as context]
            [rems.db.applications :refer [assoc-review-type-to-app
                                          can-approve?
                                          can-review?
                                          can-third-party-review?
                                          create-new-draft
                                          draft?
                                          get-application-phases
                                          get-application-state
                                          get-draft-form-for
                                          get-form-for
                                          make-draft-application
                                          is-applicant?
                                          submit-application]]
            [rems.db.core :as db]
            [rems.events :as events]
            [rems.guide :refer :all]
            [rems.info-field :as info-field]
            [rems.layout :as layout]
            [rems.phase :refer [phases]]
            [rems.roles :refer [when-role has-roles?]]
            [rems.text :refer :all]
            [rems.util :refer [get-user-id getx getx-in]]
            [ring.util.response :refer [redirect]]))

;; TODO remove id-to-name when no more forms submitted by SPA
(defn- id-to-name [id]
  (str "field" id))

(defn- text-field
  [{title :title id :id
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

(defn- texta-field
  [{title :title id :id
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

(defn- link-license
  [{title :title id :id textcontent :textcontent approved :approved readonly :readonly}]
  (license id readonly approved
           [:a {:href textcontent :target "_blank"} (str " " title)]))

(defn- text-license
  [{title :title id :id textcontent :textcontent approved :approved readonly :readonly}]
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

(defn- may-see-event?
  "May the current user see this event?

  Applicants can't see review events, reviewers and approvers can see everything."
  [event]
  ;; could implement more granular checking based on authors etc.
  ;; now strictly role-based
  (let [applicant-types #{"apply" "autoapprove" "approve" "reject" "return" "withdraw" "close"}]
    (or (has-roles? :reviewer :approver) ;; reviewer and approver can see everything
        (applicant-types (:event event)))))

(defn- application-state [state]
  (when state
    (collapsible/component
     {:id "state"
      :class (str "state-" state)
      :open? true
      :title (text :t.applications/state)
      :always [:div.state-display {:class (str "state-" state)} (text (localize-state state))]})))

(defn- application-events [events]
  (when events
    [:div {:class "events"}
     (collapsible/component
      {:id "events"
       :title (text :t.form/events)
       :always (when-let [c (:comment (last events))]
                 [:div [:h4 (text :t.form/comment)]
                  [:p [:span.inline-comment-content] c]])
       :collapse (when (seq events)
                   (list
                    [:h4 (text :t.form/events)]
                    (into [:table.table.table-hover.mb-0#event-table
                           [:tr
                            [:th (text :t.form/user)]
                            [:th (text :t.form/event)]
                            [:th (text :t.form/comment)]
                            [:th (text :t.form/date)]]]
                          (for [e events]
                            [:tr
                             [:td (:userid e)]
                             [:td (text (localize-event (:event e)))]
                             [:td.event-comment (:comment e)]
                             [:td (localize-time (:time e))]]))))})]))


(defn- form-fields [form]
  (let [application (:application form)
        state (:state application)
        new-application? (draft? (:id application))
        editable? (or new-application? (#{"draft" "returned" "withdrawn"} state))
        readonly? (not editable?)
        withdrawable? (= "applied" state)
        closeable? (and
                    (not new-application?)
                    (not= "closed" state))]
    (collapsible/component
     {:id "form"
      :class "slow"
      :open? true
      :title (text :t.form/application)
      :collapse
      (list
       (events/approval-confirm-modal "close" (text :t.actions/close) application)
       (events/approval-confirm-modal "withdraw" (text :t.actions/withdraw) application)
       [:form {:method "post"
               :action (let [app (:id application)]
                         (str "/form/" app "/save"))}
        (for [i (:items form)]
          (field (assoc i :readonly readonly?)))
        (when-let [licenses (not-empty (:licenses form))]
          [:div.form-group
           [:h4 (text :t.form/licenses)]
           (for [l licenses]
             (field (assoc l :readonly readonly?)))])
        (anti-forgery-field)
        (when (or new-application?
                  (is-applicant? (:application form)))
          [:div.row
           [:div.col
            [:a#back.btn.btn-secondary {:href "/catalogue"} (text :t.form/back)]]
           (into [:div.col.commands]
                 [(when closeable? [:button#close.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#close-modal"}
                                    (text :t.actions/close)])
                  (when editable? [:button#save.btn.btn-secondary {:type "submit" :name "save"} (text :t.form/save)])
                  (when editable? [:button#submit.btn.btn-primary.submit-button {:type "submit" :name "submit"} (text :t.form/submit)])
                  (when withdrawable? [:button#withdraw.btn.btn-secondary {:type "button" :data-toggle "modal" :data-target "#withdraw-modal"} (text :t.actions/withdraw)])
                  ])])
        ])})))

(defn- applied-resources [catalogue-items]
  (collapsible/component
   {:id "resources"
    :open? true
    :title (text :t.form/resources)
    :always [:div.form-items.form-group
             [:ul
              (for [item catalogue-items]
                [:li (:title item)])]]}))


(defn- form [form]
  (let [state (:state (:application form))
        actionable? (= state "applied")
        events (get-in form [:application :events])
        user-attributes (or (:applicant-attributes form) context/*user*)]
    (list
     [:h2 (text :t.applications/application)]

     (application-state state)

     [:div.mt-3 (application-events (filter may-see-event? events))]

     [:div.my-3 (phases (get-application-phases state))]

     (applicant-info/details "applicant-info" user-attributes)

     [:div.mt-3 (applied-resources (:catalogue-items form))]

     [:div.my-3 (form-fields form)]

     ;; TODO resource owner should be able to close

     (when-role :approver
       (if (and actionable? (can-approve? (:id (:application form))))
         (events/approve-form (:application form))
         [:div.row
          [:div.col.commands
           (events/back-to-actions-button)]]))
     (when-role :reviewer
       (if (and actionable? (or (can-review? (:id (:application form)))
                                (can-third-party-review? (:id (:application form)))))
         (events/review-form (assoc-review-type-to-app (:application form)))
         [:div.row
          [:div.col.commands
           (events/back-to-actions-button)]])
       ))))

(defn link-to-application [items]
  (url "/form" {:catalogue-items (s/join "," (mapv :id items))}))

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
  "Validates a filled in form from (get-form-for application).

   Returns either :valid or a sequence of validation errors."
  [form]
  (let [messages (vec (concat (filterv identity (mapv validate-item (sort-by :id (:items form))))
                              (filterv identity (mapv validate-license (sort-by :id (:licenses form))))))]
    (if (empty? messages)
      :valid
      messages)))

(defn- format-validation-messages
  [msgs]
  [:ul
   (for [m msgs]
     [:li m])])

(defn save-application-items [application-id catalogue-item-ids]
  (doseq [catalogue-item-id catalogue-item-ids]
    (db/add-application-item! {:application application-id :item catalogue-item-id})))

(defn- save-fields
  [application-id input]
  (let [form (get-form-for application-id)]
    (doseq [{item-id :id :as item} (:items form)]
      (when-let [value (get input item-id (get input (id-to-name item-id)))]
        (db/save-field-value! {:application application-id
                               :form (:id form)
                               :item item-id
                               :user (get-user-id)
                               :value value})))))

(defn save-licenses
  [application-id input]
  (let [form (get-form-for application-id)]
    (doseq [{licid :id :as license} (sort-by :id (:licenses form))]
      (if-let [state (get input licid (get input (str "license" licid)))]
        (db/save-license-approval! {:catappid application-id
                                    :round 0
                                    :licid licid
                                    :actoruserid (get-user-id)
                                    :state state})
        (db/delete-license-approval! {:catappid application-id
                                      :licid licid
                                      :actoruserid (get-user-id)})))))

(defn- redirect-to-application [application-id]
  (redirect (str "/form/" application-id) :see-other))

(defn- save-internal [application items licenses]
  (let [application-id (:id application)
        item-ids (mapv :id (:catalogue-items application))]
    (save-application-items application-id item-ids)
    (save-fields application-id items)
    (save-licenses application-id licenses)
    (let [submit? (get items "submit")
          form (get-form-for application-id)
          validation (validate form)
          valid? (= :valid validation)
          perform-submit? (and submit? valid?)
          success? (or (not submit?) perform-submit?)
          flash (cond
                  perform-submit? ;; valid submit
                  [{:status :success :contents (text :t.form/submitted)}]
                  submit? ;; invalid submit
                  [{:status :warning :contents (text :t.form/saved)}
                   {:status :warning :contents (format-validation-messages validation)}]
                  valid? ;; valid draft
                  [{:status :success :contents (text :t.form/saved)}]
                  :else ;; invalid draft
                  [{:status :success :contents (text :t.form/saved)}
                   {:status :info :contents (format-validation-messages validation)}])]
      (when perform-submit?
        (submit-application application-id))
      {:submit? submit?
       :form form
       :validation validation
       :valid? valid?
       :perform-submit? perform-submit?
       :success? success?
       :flash flash}
      )))

(defn api-save [request]
  (let [{:keys [application-id items licenses operation catalogue-items]} request
        application (make-draft-application -1 catalogue-items)
        items (if (= operation "send") (assoc items "submit" true) items)
        wfid (getx application :wfid)
        application-id (if (draft? application-id) (create-new-draft wfid) application-id)
        application (assoc application :id application-id)
        {:keys [success? valid? validation]} (save-internal application items licenses)]
    (cond-> {:success success?
             :valid valid?}
      (not valid?) (assoc :validation validation)
      success? (assoc :id application-id
                      :state (:state (get-application-state application-id))))
    ))

(defn- form-save [{params :params input :form-params session :session}]
  (let [application-id (Long/parseLong (getx params :application))
        application (if (draft? application-id)
                      (getx-in session [:applications application-id])
                      (get-application-state application-id))
        form (if (draft? application-id)
               (get-draft-form-for application)
               (get-form-for application-id))
        db-application-id (if (draft? application-id)
                            (create-new-draft (getx application :wfid))
                            application-id)
        application (assoc application :id db-application-id)]
    (let [{:keys [flash]} (save-internal application input input)
          new-session (-> session
                          (update :applications dissoc application-id) ; remove temporary application
                          (update :cart difference (set (mapv :id (:catalogue-items application)))) ; remove applied items from cart
                          )]
      (->
       (redirect-to-application db-application-id)
       (assoc :flash flash)
       (assoc :session new-session)
       ))))

(defn- form-page [application]
  (layout/render
   "form"
   (form application)))

(defroutes form-routes
  (GET "/form/:application" [application :as req]
       (let [application-id (Long/parseLong application)]
         (if (draft? application-id)
           (form-page (get-draft-form-for (get-in req [:session :applications application-id])))
           (form-page (get-form-for application-id))
           )))
  (GET "/form" req
       (let [{session :session {:keys [catalogue-items]} :params} req
             catalogue-item-ids (map #(Long/parseLong %) (s/split catalogue-items #"[,]"))
             new-app-id (dec (apply min (keys (merge (:applications session) {0 nil}))))
             draft (make-draft-application new-app-id catalogue-item-ids)]
         (-> (redirect-to-application new-app-id)
             (assoc :session (assoc-in session [:applications new-app-id] draft)))))
  (POST "/form/:application/save" req (form-save req)))

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
                   :catalogue-items [{:title "An applied item"}]
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
                   :catalogue-items [{:title "An applied item"}]
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
                   :catalogue-items [{:title "An applied item"}]
                   :application {:id 17 :state "approved" :events [{:event "approve" :comment "Looking good, approved!"}]}
                   :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                           {:type "label" :title "Please input your wishes below."}
                           {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2" :value "def"}
                           {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
                   :licenses [{:type "license" :title "A Text License" :licensetype "text" :id 3
                               :textcontent lipsum}
                              {:type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                               :approved true}]
                   :comments [{:comment "a comment"}]}))))
