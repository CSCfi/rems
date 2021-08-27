(ns rems.api.services.categories (:require [rems.api.services.dependencies :as dependencies]
                                           [rems.db.core :as db]
                                           [rems.json :as json]
                                           [jsonista.core :as j]
                                           [muuntaja.core :as m]
                                           [rems.db.organizations :as organizations]
                                           [rems.db.categories :as categories]))

(def m (m/create))
(m/encodes m)


(defn- join-dependencies [category]
  (when category
    (->> category
         organizations/join-organization
        ;;  licenses/join-resource-licenses
        ;;  (transform [:licenses ALL] organizations/join-organization)
         )))


(defn create-category! [{:keys [id data organization]}]
  (let [id (:id (db/create-category! {:id id
                                      :data (json/generate-string data)
                                      :organization (:organization/id organization)}))]
    ;; reset-cache! not strictly necessary since resources don't depend on anything, but here for consistency
    (dependencies/reset-cache!)
    {:success true
     :id id}))

;; (defn get-category [id]
;;   (db/get-category-by-id! id))

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

(defn get-category [id]
  (when-let [category (db/get-category-by-id! {:id id})]
    (format-category-id (join-dependencies category))))

(defn get-categories []
  (map
   #(format-category %)
   (db/get-categories)))

