(ns rems.api.services.catalogue
  (:require [clojure.core.memoize :as memo]
            [rems.common-util :refer [index-by]]
            [rems.db.core :as db]
            [rems.db.catalogue :as catalogue]))

(defn create-catalogue-item! [command]
  (let [id (:id (db/create-catalogue-item! (select-keys command [:title :form :resid :wfid :enabled :archived])))]
    {:success (not (nil? id))
     :id id}))

(defn create-catalogue-item-localization! [command]
  (let [return {:success (not (nil? (:id (db/create-catalogue-item-localization! (select-keys command [:id :langcode :title])))))}]
    ;; Reset cache so that next call to get localizations will get this one.
    (catalogue/reset-cache!)
    return))

(defn update-catalogue-item! [command]
  ;; TODO disallow unarchiving catalogue item if its resource, form or licenses are archived
  (db/set-catalogue-item-state! (select-keys command [:id :enabled :archived]))
  {:success true})

(def get-localized-catalogue-items catalogue/get-localized-catalogue-items)
(def get-localized-catalogue-item catalogue/get-localized-catalogue-item)
