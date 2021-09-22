(ns rems.api.services.categories (:require [rems.api.services.dependencies :as dependencies]
                                           [rems.db.core :as db]
                                           [rems.json :as json]
                                           [rems.api.services.util :as util]
                                           [jsonista.core :as j]
                                           [rems.db.categories :as categories]))




(defn create-category! [{:keys [id data organization]}]
  ;; (util/check-allowed-organization! organization)
  (let [id (:id (db/create-category! {:id id
                                      :data (json/generate-string data)
                                      :organization (:organization/id organization)}))]
    ;; reset-cache! not strictly necessary since resources don't depend on anything, but here for consistency
    (dependencies/reset-cache!)
    {:success true
     :id id}))

(defn get-category [id]
  (categories/get-category id))

(defn get-categories []
  (categories/get-categories))

(defn edit-category [{:keys [id data organization]}]
  (categories/edit-category {:id id
                             :data (json/generate-string data)
                             :organization (:organization/id organization)}))




