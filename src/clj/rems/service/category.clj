(ns rems.service.category
  (:require [medley.core :refer [update-existing-in]]
            [rems.db.category]
            [rems.service.dependencies :as dependencies]))

(defn join-categories [m ks]
  (update-existing-in m ks rems.db.category/enrich-categories))

(defn get-category [id]
  (when-let [category (rems.db.category/get-category id)]
    (-> category
        (join-categories [:category/children]))))

(defn get-categories []
  (rems.db.category/get-categories))

(defn- category-entities-not-found-error [children]
  (when-let [not-found (seq (remove #(rems.db.category/get-category (:category/id %)) children))]
    {:success false
     :errors [{:type :t.administration.errors/dependencies-not-found
               :categories not-found}]}))

(defn create-category! [command]
  (or (category-entities-not-found-error (:category/children command))
      (let [id (rems.db.category/create-category! command)]
        (dependencies/reset-cache!)
        {:success true
         :category/id id})))

(defn- self-as-subcategory-error [id children]
  (when (seq (filter #(= (:category/id %) id) children))
    {:success false
     :errors [{:type :t.administration.errors/self-as-subcategory-disallowed
               :category/id id}]}))

(defn- ancestor-as-subcategory-error [id children]
  (let [ancestors (rems.db.category/get-ancestors-of id)]
    (when-let [looping-ancestors (seq (filter #(ancestors (:category/id %)) children))]
      {:success false
       :errors [{:type :t.administration.errors/ancestor-as-subcategory-disallowed
                 :categories (mapv #(select-keys (rems.db.category/get-category (:category/id %))
                                                 [:category/id :category/title])
                                   looping-ancestors)}]})))

(defn update-category! [command]
  (or (category-entities-not-found-error (:category/children command))
      (self-as-subcategory-error (:category/id command) (:category/children command))
      (ancestor-as-subcategory-error (:category/id command) (:category/children command))
      (let [id (:category/id command)
            data (dissoc command :category/id)]
        (rems.db.category/update-category! id data)
        (dependencies/reset-cache!)
        {:success true})))

(defn delete-category! [command]
  (or (dependencies/in-use-error (select-keys command [:category/id]))
      (do
        (rems.db.category/delete-category! (:category/id command))
        (dependencies/reset-cache!)
        {:success true})))
