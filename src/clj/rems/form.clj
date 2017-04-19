(ns rems.form
  (:require [compojure.core :refer [GET POST defroutes]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.applications :as applications]
            [rems.approvals :as approvals]
            [rems.db.applications :refer [create-new-draft
                                          get-draft-id-for
                                          get-form-for]]
            [rems.db.core :as db]
            [rems.guide :refer :all]
            [rems.layout :as layout]
            [rems.role-switcher :refer [when-role]]
            [rems.text :refer :all]
            [rems.util :refer [get-user-id]]
            [ring.util.response :refer [redirect]]))

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
           (list [:h6 title]
                 [:p textcontent])))

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

(defn- form [form]
  (let [state (:state (:application form))
        editable (or (nil? state) (= state "draft"))
        readonly (not editable)
        approvable (= state "applied")
        comments (keep :comment (:comments form))]
    (list
     [:form {:method "post"
             :action (if-let [app (:id (:application form))]
                       (str "/form/" (:catalogue-item form) "/" app "/save")
                       (str "/form/" (:catalogue-item form) "/save"))}
      [:h3 (:title form)]
      (when state
        [:h4 (text (applications/localize-state state))])
      (for [i (:items form)]
        (field (assoc i :readonly readonly)))
      (when-let [licenses (not-empty (:licenses form))]
        [:div.form-group
         [:h4 (text :t.form/licenses)]
         (for [l licenses]
           (field (assoc l :readonly readonly)))])
      (when-not (empty? comments)
        (list
         [:h4 (text :t.form/comments)]
         [:ul
          (for [c comments]
            [:li c])]))
      (anti-forgery-field)
      (when-role :applicant
        [:div.row
         [:div.col
          [:a.btn.btn-secondary {:href "/catalogue"} (text :t.form/back)]]
         (when editable
           [:div.col.actions
            [:button.btn.btn-secondary {:type "submit" :name "save"} (text :t.form/save)]
            [:button.btn.btn-primary.submit-button {:type "submit" :name "submit"} (text :t.form/submit)]])])]
     ;; The approve buttons need to be outside the form since they're
     ;; implemented as forms
     (when-role :approver
       (list
        (when approvable
          (approvals/approve-form (:application form)))
        [:a.btn.btn-secondary {:href "/approvals"} (text :t.form/back-approvals)])))))

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
      (when-let [value (get input (id-to-name item-id))]
        (db/save-field-value! {:application application-id
                               :form (:id form)
                               :item item-id
                               :user (get-user-id)
                               :value value})))))

(defn save-licenses
  [resource-id application-id input]
  (let [form (get-form-for resource-id)]
    (doseq [{licid :id :as license} (:licenses form)]
      (if-let [state (get input (str "license" licid))]
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
        (db/update-application-state! {:id application-id :user (get-user-id) :state "applied" :curround 0}))
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
             (field {:type "license" :title "A Text License" :licensetype "text"
                     :textcontent lipsum})])
   (example "field of unsupported type"
            [:form
             (field {:type "unsupported" :title "Title" :inputprompt "prompt"})])
   (example "partially filled form"
            (form {:title "Form title"
                   :application {:id 17 :state "draft"}
                   :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                           {:type "label" :title "Please input your wishes below."}
                           {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2"}
                           {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
                   :licenses [{:type "license" :title "A Text License" :licensetype "text"
                               :textcontent lipsum}
                              {:type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                               :approved true}]}))
   (example "applied form"
            (form {:title "Form title"
                   :application {:id 17 :state "applied"}
                   :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                           {:type "label" :title "Please input your wishes below."}
                           {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2" :value "def"}
                           {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]
                   :licenses [{:type "license" :title "A Text License" :licensetype "text"
                               :textcontent lipsum}
                              {:type "license" :licensetype "link" :title "Link to license" :textcontent "/guide"
                               :approved true}]
                   :comments [{:comment "a comment"}]}))))
