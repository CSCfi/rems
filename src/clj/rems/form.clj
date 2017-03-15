(ns rems.form
  (:require [hiccup.form :as f]
            [rems.db.core :as db]))

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
     :title (or (:formtitle form) (:metatitle form))
     :items items}))

(defn text-field [{title :title id :id
                   prompt :inputprompt value :value}]
  (let [nam (str "text" id)]
    [:div.form-group
     [:label {:for nam} title]
     [:input.form-control {:type "text" :id nam :placeholder prompt
                           :value value}]]))

(defn texta-field [{title :title id :id
                    prompt :inputprompt value :value}]
  (let [nam (str "text" id)]
    [:div.form-group
     [:label {:for nam} title]
     [:textarea.form-control {:id nam :placeholder prompt
                              :value value}]]))

(defn field [f]
  (case (:type f)
    "text" (text-field f)
    "texta" (texta-field f)
    [:p "Unsupported field " (pr-str f)]))

(defn form [form]
  [:form
   [:h3 (:title form)]
   (for [i (:items form)]
     (field i))])

(defn link-to-form [item]
  [:a.btn.btn-primary {:href (str "/form/" (:id item))} "Apply"])
