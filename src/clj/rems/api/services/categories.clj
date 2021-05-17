(ns rems.api.services.categories (:require [rems.api.services.dependencies :as dependencies]
                                           [rems.db.core :as db]
                                           [rems.db.categories :as categories]))


(defn create-category! [{:keys [id data]}]
  (let [id (:id (db/create-category! {:id id
                                      :data data}))]
    ;; reset-cache! not strictly necessary since resources don't depend on anything, but here for consistency
    (dependencies/reset-cache!)
    {:success true
     :id id}))

(defn get-category [id]
  (categories/get-category id))

(defn get-categories []
  (db/get-categories))

