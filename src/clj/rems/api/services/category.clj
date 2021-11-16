(ns rems.api.services.category
  (:require [rems.db.category :as category]
            [rems.api.services.dependencies :as dependencies]))

(defn join-categories [m ks]
  (update-in m ks category/enrich-categories))

(defn get-category [id]
  (when-let [category (category/get-category id)]
    (-> category
        (join-categories [:category/children]))))

(defn get-categories []
  (category/get-categories))

(defn- check-category-children [children]
  (when-let [not-found (seq (remove (comp category/get-category :category/id) children))]
    {:success false
     :errors [{:type :t.administration.errors/dependencies-not-found
               :categories not-found}]}))

(defn create-category! [command]
  (or (check-category-children (:category/children command))
      (let [id (category/create-category! command)]
        (dependencies/reset-cache!)
        {:success true
         :id id})))

(defn update-category! [command]
  (or (check-category-children (:category/children command))
      (let [id (:category/id command)
            data (dissoc command :category/id)]
        (category/update-category! id data)
        (dependencies/reset-cache!)
        {:success true})))

(defn delete-category! [command]
  (or (dependencies/in-use-error (select-keys command [:category/id]))
      (do
        (category/delete-category! (:category/id command))
        (dependencies/reset-cache!)
        {:success true})))