(ns rems.api.services.category
  (:require [rems.db.category :as category]
            [rems.db.organizations :as organizations]
            [rems.api.services.util :as util]
            [rems.api.services.dependencies :as dependencies]))

(defn- join-dependencies [category]
  (when category
    (->> category
         organizations/join-organization)))

(defn join-categories [m ks]
  (update-in m ks category/enrich-categories))

(defn get-category [id]
  (when-let [category (category/get-category id)]
    (-> category
        join-dependencies
        (join-categories [:category/children]))))

(defn get-categories []
  (map join-dependencies (category/get-categories)))

(defn create-category! [command]
  (util/check-allowed-organization! (:organization command))
  (let [id (category/create-category! command)]
    (dependencies/reset-cache!)
    {:success true
     :id id}))

(defn update-category! [command]
  (let [id (:category/id command)
        data (dissoc command :category/id)]
    (util/check-allowed-organization! (:organization data))
    (category/update-category! id data)
    (dependencies/reset-cache!)
    {:success true}))

(defn delete-category! [command]
  (or (dependencies/in-use-error {:category/id (:category/id command)})
      (do
        (category/delete-category! (:category/id command))
        (dependencies/reset-cache!)
        {:success true})))