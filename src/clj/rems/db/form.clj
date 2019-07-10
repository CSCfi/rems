(ns rems.db.form
  (:require [clojure.test :refer :all]
            [rems.api.schema :refer [FormFieldWithId]]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [schema.core :as s]))

;;; form api related code – form "templates"

(s/defschema Fields
  [FormFieldWithId])

(def ^:private fields-coercer
  (coerce/coercer Fields coerce/string-coercion-matcher))

(defn- coerce-fields [fields]
  (let [result (fields-coercer fields)]
    (if (schema.utils/error? result)
      (throw (ex-info "Failed to coerce fields" {:fields fields :error result}))
      result)))

(defn- parse-db-row [row]
  (-> row
      (update :fields #(coerce-fields (json/parse-string %)))
      db/assoc-expired))

(defn get-form-templates [filters]
  (->> (db/get-form-templates)
       (map parse-db-row)
       (db/apply-filters filters)))

(defn get-form-template [id]
  (-> (db/get-form-template {:id id})
      parse-db-row))

(defn- create-form-item! [user-id form-id item-index {:keys [input-prompt options]
                                                      :field/keys [title optional type max-length]}]
  (let [item-id (:id (db/create-form-item! {:type (name type)
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
                         :maxlength max-length
                         :item item-id
                         :user user-id})
    (doseq [lang (keys title)]
      (db/localize-form-item! {:item item-id
                               :langcode (name lang)
                               :title (get title lang)
                               :inputprompt (get input-prompt lang)}))
    item-id))

(defn- catalogue-items-for-form [id]
  (->> (catalogue/get-localized-catalogue-items {:form id :archived false})
       (map #(select-keys % [:id :title :localizations]))))

(defn- form-in-use-error [form-id]
  (let [catalogue-items (catalogue-items-for-form form-id)]
    (when (seq catalogue-items)
      {:success false
       :errors [{:type :t.administration.errors/form-in-use :catalogue-items catalogue-items}]})))

(defn form-editable [form-id]
  (or (form-in-use-error form-id)
      {:success true}))

(defn- generate-fields-with-ids! [user-id form-id fields]
  ;; Mirror field ids to form template so that form templates
  ;; can be cross-referenced with form answers. Once old-style
  ;; forms are gone, will need to allocate ids here (just use
  ;; order, or generate UUIDs)
  (map-indexed (fn [order field]
                 (let [id (create-form-item! user-id form-id order field)]
                   (assoc field :field/id id)))
               fields))

(defn create-form! [user-id {:keys [organization title fields] :as form}]
  ;; FIXME Remove saving old style forms only when we have a db migration.
  ;;       Otherwise it will get reeealy tricky to return both versions in get-api.
  (let [;; NB: Legacy forms (created by db/create-form!) are not updated
        ;;   when the form is edited. This is on purpose: this whole legacy
        ;;   codepath will be removed soon.
        form-id (:id (db/create-form! {:organization organization
                                       :title title
                                       :user user-id}))]
    (db/save-form-template!
     (assoc form
            :id form-id
            :user user-id
            :fields (->> (generate-fields-with-ids! user-id form-id fields)
                         (s/validate Fields)
                         (json/generate-string))))
    {:success (not (nil? form-id))
     :id form-id}))

(defn edit-form! [user-id form-id {:keys [fields] :as form}]
  (or (form-in-use-error form-id)
      (do (db/edit-form-template!
           (assoc form
                  :id form-id
                  :user user-id
                  :fields (->> (generate-fields-with-ids! user-id form-id fields)
                               (s/validate Fields)
                               (json/generate-string))))
          {:success true})))

(defn update-form! [command]
  (let [catalogue-items (catalogue-items-for-form (:id command))]
    (if (and (:archived command) (seq catalogue-items))
      {:success false
       :errors [{:type :t.administration.errors/form-in-use :catalogue-items catalogue-items}]}
      (do
        (db/set-form-state! command)
        (db/set-form-template-state! command)
        {:success true}))))
