(ns rems.db.form
  (:require [rems.db.core :as db]
            [rems.util :refer [get-user-id]]))

(defn get-forms [filters]
  (->> (db/get-forms)
       (map db/assoc-active)
       (db/apply-filters filters)))

(defn- create-form-item! [form item-index {:keys [title optional type input-prompt]}]
  (let [item (db/create-form-item! {:type type
                                    :optional optional
                                    :user (get-user-id)
                                    :value 0})]
    (db/link-form-item! {:form (:id form)
                         :itemorder item-index
                         :optional optional
                         :item (:id item)
                         :user (get-user-id)})
    (doseq [lang (keys title)]
      (db/localize-form-item! {:item (:id item)
                               :langcode (name lang)
                               :title (get title lang)
                               :inputprompt (get input-prompt lang)}))))

(defn create-form! [{:keys [organization title items]}]
  (let [form (db/create-form! {:organization organization
                               :title title
                               :user (get-user-id)})]
    (doseq [[index item] (map-indexed vector items)]
      (create-form-item! form index item))
    {:id (:id form)}))
