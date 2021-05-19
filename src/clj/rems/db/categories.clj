(ns rems.db.categories
  (:require [rems.db.core :as db]))

(defn- format-category
  [{:keys [id]}]
  {:id id})

(defn get-category [id]
  (when-let [category (db/get-category-by-id! {:id id})]
    (format-category category)))

(defn get-categories []
  (->> (db/get-categories)
       (map format-category)))







