(ns rems.db.categories
  (:require [rems.db.core :as db]
            [rems.db.organizations :as organizations]
            [rems.json :as json]))


(defn- format-category
  [{:keys [id data organization]}]
  {:id id
   :data (str data) ;;; will through json error if not converted
   :organization {:organization/id (str organization)}})

(defn- format-category-id
  [{:keys [id data organization]}]
  {:id id
   :data (str data) ;;; will through json error if not converted
   :organization organization})

(defn- join-dependencies [category]
  (when category
    (->> category
         organizations/join-organization)))


(defn get-category [id]
  (when-let [category (db/get-category-by-id! {:id id})]
    (format-category-id (join-dependencies category))))

(defn get-categories []
  (map
   #(format-category %)
   (db/get-categories)))

(defn edit-category! [{:keys [id data organization]}]
  (let [category (get-category id)]
    (db/edit-category-by-id! {:id (or id (:id category))
                              :data (or (json/generate-string data) (:data category))
                              :organization (or (:organization/id organization) (get-in category [:organization :organization/id]))}))
  {:success true})








