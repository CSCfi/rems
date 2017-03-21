(ns rems.form
  (:require [hiccup.form :as f]
            [rems.context :as context]
            [rems.text :refer :all]
            [rems.db.core :as db]
            [rems.db.applications :refer [get-form-for create-new-draft]]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [compojure.core :refer [defroutes POST]]
            [ring.util.response :refer [redirect]]))

(defn- id-to-name [id]
  (str "field" id))

(defn text-field [{title :title id :id
                   prompt :inputprompt value :value
                   readonly :readonly}]
  [:div.form-group
   [:label {:for (id-to-name id)} title]
   [:input.form-control {:type "text" :name (id-to-name id) :placeholder prompt
                         :value value :readonly readonly}]])

(defn texta-field [{title :title id :id
                    prompt :inputprompt value :value
                    readonly :readonly}]
  [:div.form-group
   [:label {:for (id-to-name id)} title]
   [:textarea.form-control {:name (id-to-name id) :placeholder prompt
                            :readonly readonly}
    value]])

(defn label [{title :title}]
  [:div.form-group
   [:label title]])

(defn field [f]
  (case (:type f)
    "text" (text-field f)
    "texta" (texta-field f)
    "label" (label f)
    [:p.alert.alert-warning "Unsupported field " (pr-str f)]))

(defn form [form]
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

(defn- save
  ([resource-id input]
   (save resource-id (create-new-draft resource-id) input))
  ([resource-id application-id input]
   (save-fields resource-id application-id input)
   (when (get input "submit")
     (db/update-application-state! {:id application-id :user 0 :state "applied"}))
   (redirect (str "/form/" resource-id "/" application-id) :see-other)))

(defroutes form-routes
  (POST "/form/:id/save" [id :as {input :form-params}]
        (save (Long/parseLong id) input))
  (POST "/form/:id/:application/save" [id application :as {input :form-params}]
        (save (Long/parseLong id) (Long/parseLong application) input)))
