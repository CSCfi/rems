(ns rems.form
  (:require [hiccup.form :as f]
            [rems.context :as context]
            [rems.layout :as layout]
            [rems.guide :refer :all]
            [rems.text :refer :all]
            [rems.db.core :as db]
            [rems.db.applications :refer [get-form-for get-draft-id-for create-new-draft]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :refer [redirect]]))

(defn- id-to-name [id]
  (str "field" id))

(defn- text-field [{title :title id :id
                   prompt :inputprompt value :value
                   readonly :readonly}]
  [:div.form-group
   [:label {:for (id-to-name id)} title]
   [:input.form-control {:type "text" :name (id-to-name id) :placeholder prompt
                         :value value :readonly readonly}]])

(defn- texta-field [{title :title id :id
                    prompt :inputprompt value :value
                    readonly :readonly}]
  [:div.form-group
   [:label {:for (id-to-name id)} title]
   [:textarea.form-control {:name (id-to-name id) :placeholder prompt
                            :readonly readonly}
    value]])

(defn- label [{title :title}]
  [:div.form-group
   [:label title]])

(defn- field [f]
  (case (:type f)
    "text" (text-field f)
    "texta" (texta-field f)
    "label" (label f)
    [:p.alert.alert-warning "Unsupported field " (pr-str f)]))

(defn- form [form]
  (let [applied (= (:state form) "applied")]
    [:form {:method "post"
            :action (if-let [app (:application form)]
                      (str "/form/" (:catalogue-item form) "/" app "/save")
                      (str "/form/" (:catalogue-item form) "/save"))}
     [:h3 (:title form)]
     (when applied
       [:h2 (text :t.applications.states/applied)])
     (for [i (:items form)]
       (field (assoc i :readonly applied)))
     (anti-forgery-field)
     (when-not applied
       (list
        [:button.btn {:type "submit" :name "save"} (text :t.form/save)]
        [:button.btn.btn-primary {:type "submit" :name "submit"}
         (text :t.form/submit)]))]))

(defn link-to-item [item]
  (str "/form/" (:id item)))

(defn- save-fields
  [resource-id application-id input]
  (let [form (get-form-for resource-id)]
    (doseq [{item-id :id :as item} (:items form)]
      (when-let [value (get input (id-to-name item-id))]
        (db/save-field-value! {:application application-id
                               :form (:id form)
                               :item item-id
                               :user 0
                               :value value})))))

(defn- redirect-to-application [resource-id application-id]
  (redirect (str "/form/" resource-id "/" application-id) :see-other))

(defn- save
  ([resource-id input]
   (save resource-id (create-new-draft resource-id) input))
  ([resource-id application-id input]
   (save-fields resource-id application-id input)
   (when (get input "submit")
     (db/update-application-state! {:id application-id :user 0 :state "applied"}))
   (redirect-to-application resource-id application-id)))

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
  (POST "/form/:id/save" [id :as {input :form-params}]
        (save (Long/parseLong id) input))
  (POST "/form/:id/:application/save" [id application :as {input :form-params}]
        (save (Long/parseLong id) (Long/parseLong application) input)))

(defn guide
  []
  (list
   (example "field of type \"text\""
            [:form
             (field {:type "text" :title "Title" :inputprompt "prompt"})])
   (example "field of type \"texta\""
            [:form
             (field {:type "texta" :title "Title" :inputprompt "prompt"})])
   (example "field of type \"label\""
            [:form
             (field {:type "label" :title "Lorem ipsum dolor sit amet"})])
   (example "field of unsupported type"
            [:form
             (field {:type "unsupported" :title "Title" :inputprompt "prompt"})])
   (example "form"
            (form {:title "Form title"
                        :items [{:type "text" :title "Field 1" :inputprompt "prompt 1"}
                                {:type "label" :title "Please input your wishes below."}
                                {:type "texta" :title "Field 2" :inputprompt "prompt 2"}
                                {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]}))
   (example "applied form"
            (form {:title "Form title"
                        :state "applied"
                        :items [{:type "text" :title "Field 1" :inputprompt "prompt 1"}
                                {:type "label" :title "Please input your wishes below."}
                                {:type "texta" :title "Field 2" :inputprompt "prompt 2"}
                                {:type "unsupported" :title "Field 3" :inputprompt "prompt 3"}]}))))
