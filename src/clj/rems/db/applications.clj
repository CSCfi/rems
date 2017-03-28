(ns rems.db.applications
  "Query functions for forms and applications."
  (:require [rems.context :as context]
            [rems.db.core :as db]
            [rems.db.catalogue :refer [get-localized-catalogue-item]]
            [rems.util :refer [index-by]]
            [rems.auth.util :refer [throw-unauthorized]]))

(defn get-applications []
  (doall
   (for [a (db/get-applications {:applicant context/*user*})]
     (assoc a :catalogue-item
            (get-in (get-localized-catalogue-item {:id (:catid a)})
                    [:localizations context/*lang*])))))

(defn get-draft-id-for
  "Finds applications in the draft state for the given catalogue item.
   Returns an id of an arbitrary one of them, or nil if there are none."
  [catalogue-item]
  (when-let [app (first (db/get-applications {:resource catalogue-item :state "draft" :applicant context/*user*}))]
    (:id app)))

(defn- process-item
  "Returns an item structure like this:

    {:id 123
     :type \"texta\"
     :title \"Item title\"
     :inputprompt \"hello\"
     :optional true
     :value \"filled value or nil\"}"
  [application-id form-id item]
  {:id (:id item)
   :title (:title item)
   :inputprompt (:inputprompt item)
   :optional (:formitemoptional item)
   :type (:type item)
   :value (when application-id
            (:value
             (db/get-field-value {:item (:id item)
                                  :form form-id
                                  :application application-id})))})

(defn- process-license
  "Returns a license structure like this:

    {:type \"license\"
     :licensetype \"link\"
     :title \"LGPL\"
     :textcontent \"www.license.link\"}"
  [localizations license]
  (let [localized-title (get-in localizations [context/*lang* :title])
        localized-content (get-in localizations [context/*lang* :textcontent])]
    {:type "license"
     :licensetype (:type license)
     :title (or localized-title (:title license))
     :textcontent (or localized-content (:textcontent license))}))

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
             ...]
     :licences [{:type \"license\"
                 :licensetype \"link\"
                 :title \"LGPL\"
                 :textcontent \"http://foo\"}]}"
  ([catalogue-item]
   (get-form-for catalogue-item nil))
  ([catalogue-item application-id]
   (let [form (db/get-form-for-catalogue-item
               {:id catalogue-item :lang (name context/*lang*)})
         application (when application-id
                       (first (db/get-applications {:id application-id})))
         form-id (:formid form)
         items (mapv #(process-item application-id form-id %)
                     (db/get-form-items {:id form-id}))
         license-localizations (->> (db/get-license-localizations)
                                    (map #(update-in % [:langcode] keyword))
                                    (index-by [:licid :langcode]))
         licenses (mapv #(process-license (license-localizations (:id %)) %)
                        (db/get-workflow-licenses {:catId catalogue-item}))]
     (when (and application-id
                (not= (:applicantuserid application) context/*user*))
       (throw-unauthorized))
     {:id form-id
      :catalogue-item catalogue-item
      :application application-id
      :state (:state application)
      :title (or (:formtitle form) (:metatitle form))
      :items items
      :licenses licenses})))

(defn create-new-draft [resource-id]
  (let [id (:id (db/create-application!
                 {:item resource-id :user context/*user*}))]
    (db/update-application-state! {:id id :user context/*user* :state "draft"})
    id))
