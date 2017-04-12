(ns rems.form
  (:require [rems.context :as context]
            [rems.layout :as layout]
            [rems.guide :refer :all]
            [rems.text :refer :all]
            [rems.util :refer [get-user-id]]
            [rems.db.core :as db]
            [rems.db.applications :refer [get-form-for get-draft-id-for create-new-draft]]
            [rems.approvals :as approvals]
            [rems.applications :as applications]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [rems.role-switcher :refer [when-role]]
            [compojure.core :refer [defroutes GET POST]]
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

(defn- checkbox-attrs [id approved]
  (if approved
    {:type "checkbox" :name (str "license" id) :checked "" :value "approved"}
    {:type "checkbox" :name (str "license" id) :value "approved"}))

(defn- license [{title :title id :id textcontent :textcontent approved :approved}]
  [:div.checkbox
   [:label
    [:input (checkbox-attrs id approved)]
    [:a {:href textcontent :target "_blank"} (str " " title)]]])

(defn- unsupported-field
  [f]
  [:p.alert.alert-warning "Unsupported field " (pr-str f)])

(defn- field [f]
  (case (:type f)
    "text" (text-field f)
    "texta" (texta-field f)
    "label" (label f)
    "license" (if (= "link" (:licensetype f))
                (license f)
                (unsupported-field f))
    (unsupported-field f)))

(defn- form [form]
  (let [state (:state (:application form))
        editable (= state "draft")
        readonly (not editable)
        approvable (= state "applied")]
    (list
     [:form {:method "post"
             :action (if-let [app (:id (:application form))]
                       (str "/form/" (:catalogue-item form) "/" app "/save")
                       (str "/form/" (:catalogue-item form) "/save"))}
      [:h3 (:title form)]
      [:h4 (text (applications/localize-state state))]
      (for [i (:items form)]
        (field (assoc i :readonly readonly)))
      (when-let [licenses (not-empty (:licenses form))]
        [:div
         [:label (text :t.form/licenses)]
         (for [l licenses]
           (field (assoc l :readonly readonly)))])
      (anti-forgery-field)
      (when-role :applicant
        [:div.row
         [:div.col
          [:a.btn.btn-secondary {:href "/catalogue"} (text :t.form/back)]]
         (when editable
           [:div.col.actions
            [:button.btn.btn-secondary {:type "submit" :name "save"} (text :t.form/save)]
            [:button.btn.btn-primary {:type "submit" :name "submit"} (text :t.form/submit)]])])]
     ;; The approve buttons need to be outside the form since they're
     ;; implemented as forms
     (when-role :approver
       [:div.row
        [:div.col
         [:a.btn.btn-secondary {:href "/approvals"} (text :t.form/back-approvals)]]
        (when approvable
          [:div.col.actions
           (approvals/approve-button (:application form))
           (approvals/reject-button (:application form))])]))))

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
  (let [messages (vec (concat (vec (filter identity (map validate-item (:items form))))
                              (vec (filter identity (map validate-license (:licenses form))))))]
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
          message (if perform-submit
                   (text :t.form/submitted)
                   (text :t.form/saved))
          flash (if valid
                  {:status :success
                   :contents message}
                  {:status :warning
                   :contents (list message
                                   (format-validation-messages validation))})]
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
   (example "field of type \"license\""
            [:form
             (field {:type "license" :title "Link to license" :licensetype "link" :textcontent "/guide"})])
   (example "field of unsupported type"
            [:form
             (field {:type "unsupported" :title "Title" :inputprompt "prompt"})])
   (example "partially filled form"
            (form {:title "Form title"
                   :application {:id 17 :state "draft"}
                   :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                           {:type "label" :title "Please input your wishes below."}
                           {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2"}
                           {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]}))
   (example "applied form"
            (form {:title "Form title"
                   :application {:id 17 :state "applied"}
                   :items [{:type "text" :title "Field 1" :inputprompt "prompt 1" :value "abc"}
                           {:type "label" :title "Please input your wishes below."}
                           {:type "texta" :title "Field 2" :optional true :inputprompt "prompt 2" :value "def"}
                           {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]}))))
