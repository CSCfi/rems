(ns rems.form
  (:require [hiccup.form :as f]
            [rems.context :as context]
            [rems.db.core :as db]
            [rems.anti-forgery :refer [anti-forgery-field]]
            [compojure.core :refer [defroutes POST]]
            [ring.util.response :refer [redirect]]))

(defn- process-item
  "Returns an item structure like this:

    {:id 123
     :type \"texta\"
     :title \"Item title\"
     :placeholder \"hello\"
     :optional true
     :value \"filled value or nil\"}"
  [application-id form-id item]
  {:id (:id item)
   :title (:title item)
   :inputprompt (:inputprompt item)
   :type (:type item)
   :value (when application-id
            (:value
             (db/get-field-value {:item (:id item)
                                  :form form-id
                                  :application application-id})))})

(defn get-form-for
  "Returns a form structure like this:

    {:id id
     :title \"Title\"
     :application 4
     :items [{:id 123
              :type \"texta\"
              :title \"Item title\"
              :inputprompt \"hello\"
              :optional true
              :value \"filled value or nil\"}
             ...]}"
  [catalogue-item language & [application-id]]
  (let [form (db/get-form-for-catalogue-item
              {:id catalogue-item :lang language})
        form-id (:formid form)
        items (mapv #(process-item application-id form-id %)
                    (db/get-form-items {:id form-id}))]
    {:id form-id
     :application application-id
     :title (or (:formtitle form) (:metatitle form))
     :items items}))

(defn- id-to-name [id]
  (str "field" id))

(defn text-field [{title :title id :id
                   prompt :inputprompt value :value}]
  [:div.form-group
   [:label {:for (id-to-name id)} title]
   [:input.form-control {:type "text" :name (id-to-name id) :placeholder prompt
                         :value value}]])

(defn texta-field [{title :title id :id
                    prompt :inputprompt value :value}]
  [:div.form-group
   [:label {:for (id-to-name id)} title]
   [:textarea.form-control {:name (id-to-name id) :placeholder prompt}
    value]])

(defn field [f]
  (case (:type f)
    "text" (text-field f)
    "texta" (texta-field f)
    [:p.alert.alert-warning "Unsupported field " (pr-str f)]))

(defn form [form]
  [:form {:method "post"
          :action (if-let [app (:application form)]
                    (str "/form/" (:id form) "/" app "/save")
                    (str "/form/" (:id form) "/save"))}
   [:h3 (:title form)]
   (for [i (:items form)]
     (field i))
   (anti-forgery-field)
   [:button.btn {:type "submit"} "Save"]])

(defn link-to-form [item]
  [:a.btn.btn-primary {:href (str "/form/" (:id item))} "Apply"])

(defn- save-fields
  [resource-id application-id input]
  (let [form (get-form-for resource-id (name context/*lang*))]
    (doseq [{item-id :id :as item} (:items form)]
      (when-let [value (get input (id-to-name item-id))]
        (db/save-field-value! {:application application-id
                               :form (:id form)
                               :item item-id
                               :user 0
                               :value value})))))

(defn- save
  ([resource-id input]
   (save resource-id
         (:id (db/create-application!
               {:item resource-id :user 0}))
         input))
  ([resource-id application-id input]
   (save-fields resource-id application-id input)
   (redirect (str "/form/" resource-id "/" application-id) :see-other)))

(defroutes form-routes
  (POST "/form/:id/save" [id :as {input :form-params}]
        (save (Long/parseLong id) input))
  (POST "/form/:id/:application/save" [id application :as {input :form-params}]
        (save (Long/parseLong id) (Long/parseLong application) input)))
