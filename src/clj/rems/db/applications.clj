(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [rems.context :as context]
            [rems.db.core :as db]
            [rems.db.catalogue :refer [get-localized-catalogue-item]]))

(defn get-applications []
  (doall
   (for [a (db/get-applications)]
     (assoc a :catalogue-item
            (get-in (get-localized-catalogue-item {:id (:catid a)})
                    [:localizations context/*lang*])))))

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

    {:id 7
     :title \"Title\"
     :application 3
     :state \"draft\"
     :catalogue-item 3
     :items [{:id 123
              :type \"texta\"
              :title \"Item title\"
              :inputprompt \"hello\"
              :optional true
              :value \"filled value or nil\"}
             ...]}"
  ([catalogue-item]
   (get-form-for catalogue-item nil))
  ([catalogue-item application-id]
   (let [form (db/get-form-for-catalogue-item
               {:id catalogue-item :lang (name context/*lang*)})
         application (when application-id
                       (db/get-application {:id application-id}))
         form-id (:formid form)
         items (mapv #(process-item application-id form-id %)
                     (db/get-form-items {:id form-id}))]
     {:id form-id
      :catalogue-item catalogue-item
      :application application-id
      :state (:state application)
      :title (or (:formtitle form) (:metatitle form))
      :items items})))

(defn create-new-draft [resource-id]
  (let [id (:id (db/create-application!
                 {:item resource-id :user 0}))]
    (db/update-application-state! {:id id :user 0 :state "draft"})
    id))
