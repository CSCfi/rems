(ns rems.db.form
  (:require [rems.InvalidRequestException]
            [rems.db.core :as db]
            [rems.json :as json]))

;;; form api related code – form "templates"

(defn get-forms [filters]
  (->> (db/get-forms)
       (map db/assoc-active)
       (db/apply-filters filters)))

(defn get-form [id]
  (-> (db/get-form {:id id})
      (db/assoc-active)
      (update :fields json/parse-string)))

(defn get-form-template [id]
  (-> (db/get-form-template {:id id})
      (db/assoc-active)
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

(defn create-form! [user-id {:keys [organization title items] :as form}]
  ;; FIXME Remove saving old style forms only when we have a db migration.
  ;;       Otherwise it will get reeealy tricky to return both versions in get-api.
  (let [form-id (:id (db/create-form! {:organization organization
                                       :title title
                                       :user user-id}))]
    (db/save-form-template! (assoc form
                                   :id form-id
                                   :user user-id
                                   :fields (json/generate-string (:items form))))
    (doseq [[index item] (map-indexed vector items)]
      (create-form-item! user-id form-id index item))
    {:success (not (nil? form-id))
     :id form-id}))
