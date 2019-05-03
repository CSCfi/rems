(ns rems.db.form
  (:require [clojure.test :refer :all]
            [cprop.tools :refer [merge-maps]]
            [rems.InvalidRequestException]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.json :as json]))

;;; form api related code – form "templates"

(defn get-forms [filters]
  (->> (db/get-forms)
       (map db/assoc-expired)
       (db/apply-filters filters)))

(defn get-form-template [id]
  (-> (db/get-form-template {:id id})
      (db/assoc-expired)
      (update :fields json/parse-string)))

(defn- create-form-item! [user-id form-id item-index {:keys [title optional type input-prompt maxlength options]}]
  (let [item-id (:id (db/create-form-item! {:type type
                                            :user user-id
                                            :value 0}))]
    (doseq [[index option] (map-indexed vector options)]
      (doseq [[lang label] (:label option)]
        (db/create-form-item-option! {:itemId item-id
                                      :langCode (name lang)
                                      :key (:key option)
                                      :label label
                                      :displayOrder index})))
    (db/link-form-item! {:form form-id
                         :itemorder item-index
                         :optional optional
                         :maxlength maxlength
                         :item item-id
                         :user user-id})
    (doseq [lang (keys title)]
      (db/localize-form-item! {:item item-id
                               :langcode (name lang)
                               :title (get title lang)
                               :inputprompt (get input-prompt lang)}))))

(defn create-form! [user-id {:keys [organization title fields] :as form}]
  ;; FIXME Remove saving old style forms only when we have a db migration.
  ;;       Otherwise it will get reeealy tricky to return both versions in get-api.
  (let [form-id (:id (db/create-form! {:organization organization
                                       :title title
                                       :user user-id}))]
    (db/save-form-template! (assoc form
                                   :id form-id
                                   :user user-id
                                   :fields (json/generate-string fields)))
    (doseq [[index item] (map-indexed vector fields)]
      (create-form-item! user-id form-id index item))
    {:success (not (nil? form-id))
     :id form-id}))

(defn update-form! [command]
  (let [catalogue-items (->> (catalogue/get-localized-catalogue-items {:form (:id command) :archived false})
                             (map #(select-keys % [:id :title :localizations])))]
    (if (and (:archived command) (seq catalogue-items))
      {:success false
       :errors [{:type :t.administration.errors/form-in-use :catalogue-items catalogue-items}]}
      (do
        (db/set-form-state! command)
        (db/set-form-template-state! command)
        {:success true}))))

;;; older, non-form-template code path, for fetching forms for applications

(defn- get-field-value [field form-id application-id]
  (let [query-params {:item (:id field)
                      :form form-id
                      :application application-id}]
    (if (= "attachment" (:type field))
      (:filename (db/get-attachment query-params))
      (:value (db/get-field-value query-params)))))

(defn- process-field-options [options]
  (->> options
       (map (fn [{:keys [key langcode label displayorder]}]
              {:key key
               :label {(keyword langcode) label}
               :displayorder displayorder}))
       (group-by :key)
       (map (fn [[_key options]] (apply merge-maps options))) ; merge label translations
       (sort-by :displayorder)
       (mapv #(select-keys % [:key :label]))))

(deftest process-field-options-test
  (is (= [{:key "yes" :label {:en "Yes" :fi "Kyllä"}}
          {:key "no" :label {:en "No" :fi "Ei"}}]
         (process-field-options
          [{:itemid 9, :key "no", :langcode "en", :label "No", :displayorder 1}
           {:itemid 9, :key "no", :langcode "fi", :label "Ei", :displayorder 1}
           {:itemid 9, :key "yes", :langcode "en", :label "Yes", :displayorder 0}
           {:itemid 9, :key "yes", :langcode "fi", :label "Kyllä", :displayorder 0}]))))

;; TODO figure out a better name
(defn process-field
  "Returns a field structure like this:

    {:id 123
     :type \"texta\"
     :title \"Item title\"
     :inputprompt \"hello\"
     :optional true
     :value \"filled value or nil\"}"
  [application-id form-id field]
  {:id (:id field)
   :optional (:formitemoptional field)
   :type (:type field)
   ;; TODO here we do a db call per item, for licenses we do one huge
   ;; db call. Not sure which is better?
   :localizations (into {} (for [{:keys [langcode title inputprompt]}
                                 (db/get-form-item-localizations {:item (:id field)})]
                             [(keyword langcode) {:title title :inputprompt inputprompt}]))
   :options (process-field-options (db/get-form-item-options {:item (:id field)}))
   ;; TODO this is kinda hacky, get rid of the call to get-field-value
   :value (or
           (when application-id
             (get-field-value field form-id application-id))
           "")
   :maxlength (:maxlength field)})

(defn get-form [form-id]
  (-> (db/get-form {:id form-id})
      (db/assoc-expired)
      (select-keys [:id :organization :title :start :end])
      (assoc :items (->> (db/get-form-items {:id form-id})
                         (mapv #(process-field nil form-id %))))))

(comment
  (get-form 1))
