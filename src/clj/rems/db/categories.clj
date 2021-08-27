(ns rems.db.categories
  (:require [rems.db.core :as db]))

(defn- format-category
  [{:keys [id data organization]}]
  {:id id
   :data data
   :organization organization})

(defn get-category [id]
  (when-let [category (db/get-category-by-id! {:id id})]
    (format-category category)))

(defn get-categories []
  (map
   #(format-category %)
   (db/get-categories)))







