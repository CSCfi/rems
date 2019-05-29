(ns rems.db.form
  (:require [clojure.test :refer :all]
            [rems.InvalidRequestException]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.json :as json]))

;;; form api related code – form "templates"

(defn get-form-templates [filters]
  (->> (db/get-form-templates)
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
                               :inputprompt (get input-prompt lang)}))
    item-id))

(defn create-form! [user-id {:keys [organization title fields] :as form}]
  ;; FIXME Remove saving old style forms only when we have a db migration.
  ;;       Otherwise it will get reeealy tricky to return both versions in get-api.
  (let [form-id (:id (db/create-form! {:organization organization
                                       :title title
                                       :user user-id}))
        ;; Mirror field ids to form template so that form templates
        ;; can be cross-referenced with form answers. Once old-style
        ;; forms are gone, will need to allocate ids here (just use
        ;; order, or generate UUIDs)
        fields-with-ids (map-indexed (fn [order field]
                                       (let [id (create-form-item! user-id form-id order field)]
                                         (assoc field :id id)))
                                     fields)]
    (db/save-form-template! (assoc form
                                   :id form-id
                                   :user user-id
                                   :fields (json/generate-string fields-with-ids)))
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
